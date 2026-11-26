"""Authenticated, read-only analytics report. Only reads aggregate analytics and the bounded anonymous path window."""
from contextlib import closing
import base64
import analytics_report
from analytics import APPS, TRAFFIC_LABELS, QUESTION_RESULTS, QUESTION_TOPICS
import csv
import hashlib
import hmac
import html
import io
import json
import os
import sqlite3
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import parse_qs

STATE = Path(os.environ.get('GENTV_STATE_DIR', '/var/lib/gentv-tv'))
ROOT = Path(__file__).parent
EVENTS = {'question': '用户问题', 'action': '用户操作', 'app_open': '应用启动', 'connection_result': '连接电视', 'tidy_complete': '清理完成',
          'screen_view': '页面访问', 'file_send_result': '文件发送', 'update_check': '检查更新', 'feedback_submit': '反馈提交'}
VALUES = {'connected': '成功', 'denied': '拒绝', 'failed': '失败', 'unreachable': '无法连接',
          'sent': '成功', 'current': '已是最新', 'available': '有更新', 'success': '成功',
          'cancelled': '已取消', 'no_space': '空间不足', 'unconfigured': '未配置'}

def authorized(environ):
    try:
        scheme, value = environ.get('HTTP_AUTHORIZATION', '').split(' ', 1)
        if scheme.lower() != 'basic':
            return False
        username, password = base64.b64decode(value, validate=True).decode().split(':', 1)
        # Additional users use salted, deliberately expensive hashes. Legacy
        # credentials remain valid until their owner explicitly rotates them.
        users = json.loads(os.environ.get('GENTV_REPORT_USERS_JSON', '{}'))
        encoded = users.get(username) if isinstance(users, dict) else None
        if encoded is not None:
            algorithm, rounds, salt, expected = encoded.split('$')
            if algorithm != 'pbkdf2_sha256' or not 100000 <= int(rounds) <= 2000000:
                return False
            actual = hashlib.pbkdf2_hmac('sha256', password.encode(), bytes.fromhex(salt), int(rounds)).hex()
            return hmac.compare_digest(actual, expected)
        expected = os.environ.get('GENTV_REPORT_PASSWORD_SHA256', '')
        return (len(expected) == 64
                and hmac.compare_digest(username.encode(), os.environ.get('GENTV_REPORT_USER', 'admin').encode())
                and hmac.compare_digest(hashlib.sha256(password.encode()).hexdigest(), expected))
    except (ValueError, UnicodeError, TypeError, AttributeError):
        return False

def read_rows(filename, query, args):
    path = STATE / filename
    if not path.is_file():
        return [], '尚无数据库'
    with closing(sqlite3.connect(path.as_uri() + '?mode=ro', uri=True, timeout=3)) as db, db:
        db.execute('PRAGMA query_only=ON')
        return db.execute(query, args).fetchall(), '正常'

def collect(start, end):
    tv, tv_status = read_rows('stats.sqlite3', 'SELECT day,version,sessions,seconds FROM daily WHERE day BETWEEN ? AND ? ORDER BY day,version', (start.isoformat(), end.isoformat()))
    mobile, mobile_status = read_rows('mobile.sqlite3', 'SELECT day,version,name,value,count FROM totals WHERE day BETWEEN ? AND ? ORDER BY day,version,name,value', (start.isoformat(), end.isoformat()))
    daily = {}
    for i in range((end - start).days + 1):
        day = (start + timedelta(days=i)).isoformat()
        daily[day] = dict(day=day, sessions=0, seconds=0, opens=0, connected=0, tidy=0)
    versions = {}
    for day, version, sessions, seconds in tv:
        daily[day]['sessions'] += sessions
        daily[day]['seconds'] += seconds
        v = versions.setdefault(version, dict(sessions=0, seconds=0))
        v['sessions'] += sessions
        v['seconds'] += seconds
    for day, version, name, value, count in mobile:
        field = {'app_open': 'opens', 'tidy_complete': 'tidy'}.get(name)
        if name == 'connection_result' and value == 'connected':
            field = 'connected'
        if field:
            daily[day][field] += count
    totals = {key: sum(row[key] for row in daily.values()) for key in ('sessions', 'seconds', 'opens', 'connected', 'tidy')}
    return dict(daily=list(daily.values()), versions=versions, mobile=mobile, totals=totals,
                tv_status=tv_status, mobile_status=mobile_status)

def duration(seconds):
    hours, rest = divmod(int(round(max(0,seconds))), 3600)
    minutes, seconds = divmod(rest, 60)
    return (f'{hours} 小时 ' if hours else '') + f'{minutes} 分 {seconds} 秒'

def esc(value):
    return html.escape(str(value), quote=True)

def table(headers, rows):
    body = ''.join('<tr>' + ''.join(f'<td>{esc(cell)}</td>' for cell in row) + '</tr>' for row in rows)
    if not body:
        body = f'<tr><td colspan="{len(headers)}" class="empty">所选日期暂无上报记录</td></tr>'
    return '<div class="table-scroll"><table><thead><tr>' + ''.join(f'<th scope="col">{esc(h)}</th>' for h in headers) + '</tr></thead><tbody>' + body + '</tbody></table></div>'

def chart(rows, labels=('电视会话', '手机启动')):
    peak = max(4, max(r['sessions'] for r in rows), max(r['opens'] for r in rows))
    peak = ((peak + 3) // 4) * 4
    width, height = 1000, 220
    def points(key):
        return ' '.join(f'{45+i*910/max(1,len(rows)-1):.1f},{180-r[key]*145/peak:.1f}' for i,r in enumerate(rows))
    indices = sorted(set([0, len(rows)//2, len(rows)-1]))
    ticks = ''.join(f'<text x="{45+i*910/max(1,len(rows)-1):.1f}" y="212" text-anchor="middle">{rows[i]["day"][5:]}</text>' for i in indices)
    lines = ''.join(f'<line x1="45" y1="{180-i*145/4}" x2="955" y2="{180-i*145/4}" class="grid"/><text x="30" y="{184-i*145/4}" text-anchor="end">{peak*i/4:g}</text>' for i in range(5))
    dots = ''.join(f'<circle cx="{45+i*910/max(1,len(rows)-1):.1f}" cy="{180-r[key]*145/peak:.1f}" r="3" fill="{color}"><title>{r["day"]} {label} {r[key]}</title></circle>' for key,color,label in [('sessions','#147d68',labels[0]),('opens','#4165ca',labels[1])] for i,r in enumerate(rows))
    return f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="每日{esc(labels[0])}和{esc(labels[1])}趋势，详细数值见下方每日明细">{lines}<polyline points="{points("sessions")}" stroke="#147d68"/><polyline points="{points("opens")}" stroke="#4165ca"/>{dots}{ticks}</svg>'

def question_label(value):
    topic, _, outcome=value.partition(':')
    return QUESTION_TOPICS.get(topic,'其他'),QUESTION_RESULTS.get(outcome,'未知')

def question_rows(rows):
    return [(datetime.fromtimestamp(at/1000,timezone.utc).strftime('%Y-%m-%d %H:%M:%S'),*question_label(value),version) for at,value,version in rows]

def render(data, start, end, growth=None, app_name="gentv_tv", traffic="production"):
    data = dict(data)
    totals = data['totals']
    cards = ''.join(f'<article class="metric"><span>{label}</span><strong>{value}</strong><small>{note}</small></article>' for label,value,note in [
        ('电视桌面会话', f'{totals["sessions"]:,}', '使用会话次数 · 不去重'),
        ('电视前台时长', duration(totals['seconds']), '已上报会话累计'),
        ('手机启动', f'{totals["opens"]:,}', '启动事件次数 · 不去重'),
        ('连接电视成功', f'{totals["connected"]:,}', '手机端成功连接事件')])
    daytable = table(['日期 · UTC', '电视会话', '前台时长', '手机启动', '连接成功', '清理完成'],
        [(r['day'], r['sessions'], duration(r['seconds']), r['opens'], r['connected'], r['tidy']) for r in reversed(data['daily'])])
    versiontable = table(['电视 versionCode', '会话次数', '前台时长', '会话占比'],
        [(v, r['sessions'], duration(r['seconds']), f'{r["sessions"]/max(1,totals["sessions"]):.1%}') for v,r in sorted(data['versions'].items(), reverse=True)])
    grouped = {}
    for day,version,name,value,count in data['mobile']:
        key = (version, name, value)
        grouped[key] = grouped.get(key, 0) + count
    eventtable = table(['手机版本', '事件', '结果 / 页面', '次数'],
        [(v, EVENTS.get(n,n), VALUES.get(value,value) or '—', count) for (v,n,value),count in sorted(grouped.items())])
    def node(value):
        kind, _, label = value.partition(':')
        names = {'home':'桌面首页','inputs':'输入源','quick_settings':'控制中心','launcher_settings':'桌面设置','update':'软件更新','privacy':'隐私统计','welcome':'欢迎','get_ready':'准备连接','connect':'连接','hub':'管理首页','review':'清理预览','tidying':'清理中','home_screen':'安装桌面','done':'完成','remote':'遥控器','send_files':'传文件','check':'状态检查','rescue':'恢复','space':'空间','ads':'广告管理','apps':'应用管理','settings':'设置','desktop':'桌面管理','launch_app':'启动应用','open_inputs':'打开输入源','switch_input':'切换输入源','open_settings':'打开设置','unsupported':'不支持','other':'其他页面'}
        if kind=='question':
            return '问题 · '+' / '.join(question_label(label))
        names.update(chat='电视管家',devices='我的设备',restore='恢复中心',new_chat='新建对话',connect_device='搜索并连接电视',disconnect_device='断开电视',execute_plan='确认执行')
        if label.startswith('feature_'):
            feature=label.removeprefix('feature_')
            return '功能 · '+QUESTION_TOPICS.get(feature,'桌面管理' if feature=='desktop' else feature)
        if kind not in ('screen_view','action'):
            return EVENTS.get(kind,kind)+' · '+(VALUES.get(label,label) or '完成')
        return ('操作 · ' if kind=='action' else '')+names.get(label,label)
    g = growth if growth and growth['observed'] else None
    gt = g['totals'] if g else {}
    growthcards = ''.join(f'<article class="metric"><span>{label}</span><strong>{value}</strong><small>{note}</small></article>' for label,value,note in [
        ('新增去重实例', f'{gt["new"]:,}' if g else '—', '匿名安装 ID · 首次观测' if app_name=='gentv_agent' else 'MAC 优先，安装 ID 回退 · 首次观测'),
        ('区间活跃实例', f'{gt["active"]:,}' if g else '—', ('按匿名安装 ID 去重' if app_name=='gentv_agent' else f'MAC 去重 {gt.get("mac_active",0)} · ID 回退 {gt.get("fallback_active",0)}') if g else '等待有效上报'),
        ('结束日 DAU', f'{gt["dau"]:,}' if g else '—', '结束日去重 · 当日可能未结束'),
        ('结束日 WAU / MAU', f'{gt["wau"]:,} / {gt["mau"]:,}' if g else '—', '截至结束日的 7 / 30 天滚动去重'),
        ('累计前台时长', duration(gt['seconds']) if g else '—', '仅前台增量 · 不含后台或屏保'),
        ('平均每日活跃时长', duration(gt['average']) if g else '—', '总前台秒数 ÷ 每日活跃之和'),
        ('区间会话数', f'{gt["sessions"]:,}' if g else '—', '连续使用 · 离开 30 分钟后新会话'),
        ('路径观察窗口', '最近 30 天', '固定页面和操作 · 无输入内容')])
    growthdaily=table(['日期 · UTC','新增','DAU','会话','前台时长','每活跃安装时长'],[(r['day'],r['new'],r['active'],r['sessions'],duration(r['seconds']),duration(r['average'])) for r in reversed(g['daily'])] if g else [])
    cohorts=table(['新增日期','样本数','D1 留存','D7 留存','D30 留存'],[(r[0],r[1])+tuple('未到观察日' if item is None else f'{item[0]/max(1,item[1]):.1%} ({item[0]}/{item[1]})' for item in r[2:]) for r in g['cohorts']] if g else [])
    paths=table(['来源页面 / 操作','下一步','转移次数'],[(node(a),node(b),count) for a,b,count in g['paths']] if g else [])
    growthversions=table(['应用版本','活跃安装','前台时长'],[(v,count,duration(sec)) for v,count,sec in g['versions']] if g else [])
    growthchart=chart([dict(day=r['day'],sessions=r['new'],opens=r['active']) for r in g['daily']], ('新增去重实例', '日活跃实例')) if g else '<p class="empty">等待新版客户端开启统计并上报；历史数据不能回填新增与留存。</p>'
    template = (ROOT/'report.html').read_text()
    questions=''
    if app_name=='gentv_agent':
        totals_table=table(['问题类别','处理结果','次数'],[(*question_label(value),count) for value,count in g.get('question_totals',[])] if g else [])
        records_table=table(['记录时间 · UTC','问题类别','处理结果','应用版本'],question_rows(g.get('questions',[])) if g else [])
        questions=f'<section class="panel"><div class="panel-head"><h2>用户问题记录</h2><span class="subtitle">按类别与处理结果统计 · 最近 30 天 · 明细最多 200 条</span></div>{totals_table}{records_table}<a class="download" href="/tv/report/export.csv?kind=questions&amp;start={start}&amp;end={end}&amp;app={app_name}&amp;traffic={traffic}">导出问题记录 ↓ CSV</a></section>'
        before,legacy=template.split('<!-- legacy-start -->',1)
        _,after=legacy.split('<!-- legacy-end -->',1)
        template=before+after
    replacements = dict(QUESTIONS=questions,AGENTSELECTED='selected' if app_name=='gentv_agent' else '',GROWTHCARDS=growthcards, GROWTHDAILY=growthdaily, COHORTS=cohorts, PATHS=paths, GROWTHVERSIONS=growthversions, GROWTHCHART=growthchart,
        TVSELECTED='selected' if app_name=='gentv_tv' else '', MOBILESELECTED='selected' if app_name=='gentv_mobile' else '',
        PRODSELECTED='selected' if traffic=='production' else '', TESTSELECTED='selected' if traffic=='test' else '',
        UNVERIFIEDSELECTED='selected' if traffic=='unverified' else '',
        FEATURES=table(['事件','结果 / 页面','次数'],[(EVENTS.get(n,n),node(n+':'+v) if n in ('screen_view','action','question') else VALUES.get(v,v) or '—',c) for n,v,c in g['features']] if g else []),
        APP=app_name, TRAFFIC=traffic,
        APP_LABEL=APPS[app_name],
        TRAFFIC_LABEL=TRAFFIC_LABELS[traffic],
        QUICKRANGES=''.join(f'<a href="?start={(end-timedelta(days=days-1)).isoformat()}&amp;end={end.isoformat()}&amp;app={app_name}&amp;traffic={traffic}">近 {days} 天</a>' for days in (7,30,90)),
         GROWTHSTATUS='新版统计已接入' if g else '等待新版客户端数据',
        START=start.isoformat(), END=end.isoformat(), TODAY=datetime.now(timezone.utc).date().isoformat(),
        UPDATED=datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC'), CARDS=cards, CHART=chart(data['daily']),
        DAILY=daytable, VERSIONS=versiontable, EVENTS=eventtable,
        STATUS=esc(f'电视汇总：{data["tv_status"]} · 手机汇总：{data["mobile_status"]}'))
    for key,value in replacements.items():
        template = template.replace('{{'+key+'}}',value)
    return template.encode()

def app(environ, start_response):
    headers = [('Cache-Control','no-store'), ('X-Content-Type-Options','nosniff'),
               ('X-Frame-Options','DENY'), ('Referrer-Policy','no-referrer'),
               ('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")]
    def respond(status, body, kind='text/html; charset=utf-8', extra=()):
        start_response(status, headers + [('Content-Type',kind),('Content-Length',str(len(body)))] + list(extra))
        return [] if environ.get('REQUEST_METHOD') == 'HEAD' else [body]
    if not authorized(environ):
        return respond('401 Unauthorized', '请使用报表账户登录。'.encode(), extra=[('WWW-Authenticate','Basic realm="GenTV Reports", charset="UTF-8"')])
    if environ.get('REQUEST_METHOD') not in ('GET','HEAD'):
        return respond('405 Method Not Allowed', b'', extra=[('Allow','GET, HEAD')])
    path = environ.get('PATH_INFO','')
    if path not in ('/tv/report/', '/tv/report/export.csv'):
        return respond('404 Not Found', b'Not found')
    try:
        query = parse_qs(environ.get('QUERY_STRING',''), keep_blank_values=True)
        today = datetime.now(timezone.utc).date()
        start = date.fromisoformat(query.get('start',['2026-10-28'])[0])
        end = date.fromisoformat(query.get('end',[today.isoformat()])[0])
        if start > end or end > today or (end-start).days > 365:
            raise ValueError()
    except ValueError:
        return respond('400 Bad Request', '日期无效：请选择不超过 366 天的日期范围，结束日期不能晚于 UTC 今天。'.encode())
    app_name=query.get('app',['gentv_tv'])[0];traffic=query.get('traffic',['production'])[0]
    if app_name not in APPS or traffic not in TRAFFIC_LABELS:
        return respond('400 Bad Request', '统计筛选无效。'.encode())
    try:
        data = collect(start, end)
        growth=analytics_report.collect(STATE,start,end,app_name,traffic)
    except (sqlite3.Error, OSError):
        return respond('503 Service Unavailable', '暂时无法读取统计汇总，请稍后刷新。'.encode())
    if path.endswith('.csv'):
        output = io.StringIO(); writer = csv.writer(output)
        kind=query.get('kind',[''])[0]
        if kind=='questions':
            if app_name!='gentv_agent':
                return respond('400 Bad Request', '该应用没有问题记录。'.encode())
            writer.writerow(['时间 UTC','问题类别','处理结果','应用版本'])
            writer.writerows(question_rows(growth['questions'] if growth else []))
            return respond('200 OK', ('\ufeff'+output.getvalue()).encode(), 'text/csv; charset=utf-8', [('Content-Disposition',f'attachment; filename="gentv-agent-questions-{start}-{end}.csv"')])
        if kind!='legacy':
            writer.writerow(['日期 UTC','新增去重实例','活跃安装实例 DAU','会话数','前台秒数','平均每日活跃秒数'])
            for row in growth['daily'] if growth and growth['observed'] else []:
                writer.writerow([row[k] for k in ('day','new','active','sessions','seconds','average')])
            return respond('200 OK', ('\ufeff'+output.getvalue()).encode(), 'text/csv; charset=utf-8', [('Content-Disposition',f'attachment; filename="{app_name}-growth-{start}-{end}.csv"')])
        writer.writerow(['日期 UTC','电视会话次数','电视前台秒数','手机启动次数','连接成功次数','清理完成次数'])
        for row in data['daily']:
            writer.writerow([row[k] for k in ('day','sessions','seconds','opens','connected','tidy')])
        return respond('200 OK', ('\ufeff'+output.getvalue()).encode(), 'text/csv; charset=utf-8',
                       [('Content-Disposition', f'attachment; filename="gentv-{start}-{end}.csv"')])
    return respond('200 OK', render(data, start, end, growth, app_name, traffic))
