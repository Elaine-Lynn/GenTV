"""Usage analytics with server-verified production provenance."""
from contextlib import closing
import hashlib
import hmac
import secrets
import json
import os
import re
import sqlite3
import stat
import time
import uuid
from datetime import datetime, timezone, timedelta

SCREENS = set('welcome unsupported get_ready connect hub review tidying home_screen done remote send_files check rescue space ads apps settings desktop home inputs launcher_settings quick_settings update app_library screensaver privacy other'.split())
ACTIONS = set('launch_app open_inputs switch_input open_network open_settings reorder open_module edit_dock remote_key download_update install_update'.split())
RESULTS = {
    'app_open': {''}, 'heartbeat': {''}, 'session_duration': {''}, 'screen_view': SCREENS, 'action': ACTIONS,
    'tidy_complete': {''}, 'connection_result': {'connected','denied','unreachable','failed'},
    'file_send_result': {'sent','failed','no_space','cancelled'},
    'update_check': {'current','available','failed','unconfigured'},
    'feedback_submit': {'success','failed','unconfigured'},
}
APPS = {'gentv_tv': 'GenTV Launcher', 'gentv_mobile': 'GenTV 手机伴侣', 'gentv_agent': 'GenTV Agent'}
TRAFFIC_LABELS = {'production':'已验证正式流量', 'test':'测试 / 模拟器', 'unverified':'未验证来源 / 历史流量'}
AGENT_SCREENS = {'chat','devices','restore','settings'}
AGENT_ACTIONS = {'new_chat','connect_device','execute_plan','disconnect_device'} | {
    'feature_'+name for name in 'health install launcher desktop restore ads caches performance network picture resources'.split()
}
QUESTION_TOPICS = {
    'network':'网络', 'performance':'卡顿', 'picture':'画质', 'audio':'声音', 'resources':'影视资源',
    'launcher':'桌面', 'install':'安装应用', 'ads':'广告', 'caches':'缓存清理', 'privacy':'隐私权限',
    'restore':'恢复', 'health':'体检诊断', 'remote':'遥控与投屏', 'screenshot':'截图分析', 'other':'其他',
}
QUESTION_RESULTS = {
    'answered':'已回复', 'clarify':'待补充', 'needs_connection':'待连接', 'unsupported':'不支持',
    'awaiting_action':'待确认或补充附件', 'partial':'部分结果', 'failed':'失败', 'cancelled':'取消',
}
AGENT_RESULTS = {
    **{name:RESULTS[name] for name in ('app_open','heartbeat','session_duration','connection_result')},
    'screen_view':AGENT_SCREENS, 'action':AGENT_ACTIONS,
    'question':{topic+':'+outcome for topic in QUESTION_TOPICS for outcome in QUESTION_RESULTS},
}
SCHEMA = '''
CREATE TABLE IF NOT EXISTS device_secret(id INTEGER PRIMARY KEY CHECK(id=1), value BLOB NOT NULL);
CREATE TABLE IF NOT EXISTS device_aliases(app TEXT,traffic TEXT,install TEXT,device TEXT,
 PRIMARY KEY(app,traffic,install));
CREATE TABLE IF NOT EXISTS installs(app TEXT, traffic TEXT, install TEXT, first_day TEXT, last_day TEXT,
 PRIMARY KEY(app,traffic,install));
CREATE TABLE IF NOT EXISTS activity(day TEXT,app TEXT,traffic TEXT,install TEXT,version TEXT,seconds INTEGER DEFAULT 0,
 sessions INTEGER DEFAULT 0,events INTEGER DEFAULT 0, PRIMARY KEY(day,app,traffic,install,version));
CREATE TABLE IF NOT EXISTS sessions(app TEXT,traffic TEXT,install TEXT,session TEXT,started INTEGER,last_seen INTEGER,version TEXT,
 PRIMARY KEY(app,traffic,install,session));
CREATE TABLE IF NOT EXISTS events(app TEXT,id TEXT,traffic TEXT,install TEXT,session TEXT,occurred INTEGER,received INTEGER,
 sequence INTEGER,version TEXT,name TEXT,value TEXT,seconds INTEGER, PRIMARY KEY(app,traffic,id));
CREATE UNIQUE INDEX IF NOT EXISTS event_sequence ON events(app,traffic,install,session,sequence);
CREATE INDEX IF NOT EXISTS events_received ON events(received);
CREATE INDEX IF NOT EXISTS sessions_expiry ON sessions(last_seen);
CREATE INDEX IF NOT EXISTS activity_expiry ON activity(day);
CREATE INDEX IF NOT EXISTS installs_expiry ON installs(last_day);
CREATE INDEX IF NOT EXISTS events_path ON events(app,traffic,install,session,occurred,sequence);
CREATE INDEX IF NOT EXISTS activity_range ON activity(app,traffic,day);
CREATE INDEX IF NOT EXISTS installs_cohort ON installs(app,traffic,first_day);
'''

def has_verified_traffic(db):
    return any(name=='traffic' and key_position for _,name,_,_,_,key_position in db.execute('PRAGMA table_info(events)'))


def migrate_traffic(db):
    if has_verified_traffic(db):
        return
    # Legacy production was client-declared. Retain all its data as unverified.
    db.execute('ALTER TABLE events RENAME TO events_legacy')
    statements = [statement.strip() for statement in SCHEMA.split(';')]
    db.execute(next(statement for statement in statements if statement.startswith('CREATE TABLE IF NOT EXISTS events(')))
    db.execute("INSERT INTO events SELECT app,id,CASE WHEN traffic='production' THEN 'unverified' ELSE traffic END,install,session,occurred,received,sequence,version,name,value,seconds FROM events_legacy")
    db.execute('DROP TABLE events_legacy')
    for statement in statements:
        if statement.startswith(('CREATE INDEX', 'CREATE UNIQUE INDEX')) and ' ON events(' in statement:
            db.execute(statement)
    for table in ('device_aliases', 'installs', 'activity', 'sessions'):
        db.execute(f"UPDATE {table} SET traffic='unverified' WHERE traffic='production'")


def resolve_traffic(app, claimed, raw, signature):
    if signature:
        filename = os.environ.get('GENTV_ANALYTICS_' + app.upper() + '_KEY_FILE')
        key = None
        if filename:
            try:
                with os.fdopen(os.open(filename, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK), 'rb') as file:
                    info = os.fstat(file.fileno())
                    if stat.S_ISREG(info.st_mode) and info.st_uid==os.geteuid() and not info.st_mode & 0o077:
                        key = file.read(1024).strip()
            except OSError:
                pass
        if not key or len(key)<32 or not re.fullmatch(r'[a-f0-9]{64}', signature):
            raise PermissionError('untrusted analytics source')
        expected=hmac.new(key, raw, hashlib.sha256).hexdigest()
        if claimed!='production' or not hmac.compare_digest(expected, signature):
            raise PermissionError('untrusted analytics source')
        return 'production'
    return 'test' if claimed=='test' else 'unverified'


def ident(value):
    if not isinstance(value,str) or str(uuid.UUID(value)) != value:
        raise ValueError('canonical UUID required')
    return value

def normalize_mac(value):
    """Ignore unavailable, placeholder, multicast and locally administered MACs."""
    if not isinstance(value, str):
        raise ValueError('mac type')
    compact=value.strip().replace(':','').replace('-','').lower()
    if not re.fullmatch(r'[0-9a-f]{12}',compact):
        return ''
    octets=bytes.fromhex(compact)
    if not any(octets) or octets[0] & 3:
        return ''
    return ':'.join(f'{b:02x}' for b in octets)


def day(ms):
    return datetime.fromtimestamp(ms/1000,timezone.utc).date().isoformat()

def ingest(environ, state):
    length=int(environ.get('CONTENT_LENGTH','0'))
    if not 0 < length <= 65536:raise ValueError('body size')
    raw=environ['wsgi.input'].read(length)
    batch=json.loads(raw)
    if not isinstance(batch,dict):raise ValueError('schema')
    fields={'schema_version','app','app_version','install_id','session_id','traffic','events'}
    schema=batch.get('schema_version')
    app=batch.get('app');traffic=batch.get('traffic');version=batch.get('app_version')
    if app not in APPS or traffic not in ('production','test'):raise ValueError('app/traffic')
    traffic=resolve_traffic(app,traffic,raw,environ.get('HTTP_X_GENTV_ANALYTICS_SIGNATURE',''))
    if app=='gentv_agent':
        if type(schema) is not int or schema!=4 or set(batch)!=fields:raise ValueError('schema')
        vocabulary=AGENT_RESULTS
    else:
        fields.add('android_sdk')
        if type(schema) is not int or schema not in (2,3) or set(batch)!=(fields if schema==2 else fields|{'mac_address'}):raise ValueError('schema')
        sdk=batch['android_sdk']
        if type(sdk) is not int or not 26<=sdk<=100:raise ValueError('sdk')
        vocabulary=RESULTS
    mac=normalize_mac(batch['mac_address']) if schema==3 else ''
    if not isinstance(version,str) or not re.fullmatch(r'[0-9]+(?:\.[0-9]+){0,2}(?:-[a-zA-Z0-9.]+)?',version) or len(version)>40:raise ValueError('version')
    install=hashlib.sha256((app+':'+ident(batch['install_id'])).encode()).hexdigest()
    session=ident(batch['session_id']);rows=batch['events'];now=int(time.time()*1000)
    if not isinstance(rows,list) or not 1<=len(rows)<=50:raise ValueError('batch')
    validated=[]
    for r in rows:
        if not isinstance(r,dict) or set(r)!={'event_id','occurred_at','sequence','name','value','foreground_seconds'}:raise ValueError('event fields')
        eid=ident(r['event_id']);at=r['occurred_at'];seq=r['sequence'];name=r['name'];value=r['value'];seconds=r['foreground_seconds']
        if type(at) is not int or not now-2*86400000<=at<=now+300000:raise ValueError('timestamp')
        if type(seq) is not int or not 0<=seq<=2**31-1:raise ValueError('sequence')
        if not isinstance(name,str) or not isinstance(value,str) or name not in vocabulary or value not in vocabulary[name]:raise ValueError('vocabulary')
        if type(seconds) is not int or not 0<=seconds<=120 or (seconds and name not in ('heartbeat','session_duration')):raise ValueError('duration')
        validated.append((app,eid,traffic,install,session,at,now,seq,version,name,value,seconds))
    state.mkdir(parents=True,exist_ok=True)
    path=state/'analytics.sqlite3'
    fd=os.open(path,os.O_RDWR|os.O_CREAT,0o600)
    try:os.fchmod(fd,0o600)
    finally:os.close(fd)
    with closing(sqlite3.connect(path,timeout=5)) as db, db:
        db.executescript(SCHEMA)
        db.execute('BEGIN IMMEDIATE')
        migrate_traffic(db)
        # The random server secret is persisted once; ingestion and alias updates
        # share a transaction. Raw MACs and installation UUIDs never enter SQLite.
        if mac:
            db.execute('INSERT OR IGNORE INTO device_secret VALUES(1,?)',(secrets.token_bytes(32),))
            secret=db.execute('SELECT value FROM device_secret WHERE id=1').fetchone()[0]
            device=hmac.new(secret,(app+':'+mac).encode(),hashlib.sha256).hexdigest()
            # Keep the first valid binding if network interfaces later change.
            db.execute('INSERT OR IGNORE INTO device_aliases VALUES(?,?,?,?)',(app,traffic,install,device))
        for row in validated:
            app,eid,traffic,install,session,at,received,seq,version,name,value,seconds=row
            if not db.execute('INSERT OR IGNORE INTO events VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',row).rowcount:continue
            d=day(at)
            db.execute('INSERT INTO installs VALUES(?,?,?,?,?) ON CONFLICT(app,traffic,install) DO UPDATE SET first_day=MIN(first_day,excluded.first_day),last_day=MAX(last_day,excluded.last_day)',(app,traffic,install,d,d))
            old=db.execute('SELECT started,version FROM sessions WHERE app=? AND traffic=? AND install=? AND session=?',(app,traffic,install,session)).fetchone()
            fresh=db.execute('INSERT OR IGNORE INTO sessions VALUES(?,?,?,?,?,?,?)',(app,traffic,install,session,at,at,version)).rowcount
            if old and at<old[0] and (day(old[0])!=d or old[1]!=version):
                db.execute('UPDATE activity SET sessions=MAX(0,sessions-1) WHERE day=? AND app=? AND traffic=? AND install=? AND version=?',(day(old[0]),app,traffic,install,old[1]))
                fresh=1
            db.execute('UPDATE sessions SET version=CASE WHEN started>? THEN ? ELSE version END,started=MIN(started,?),last_seen=MAX(last_seen,?) WHERE app=? AND traffic=? AND install=? AND session=?',(at,version,at,at,app,traffic,install,session))
            db.execute('INSERT INTO activity VALUES(?,?,?,?,?,0,?,1) ON CONFLICT(day,app,traffic,install,version) DO UPDATE SET sessions=sessions+excluded.sessions,events=events+1',(d,app,traffic,install,version,1 if fresh else 0))
            # Split foreground intervals at UTC midnight. Delta events are <=120 s.
            cursor=at-seconds*1000
            while cursor<at:
                next_midnight=int((datetime.fromtimestamp(cursor/1000,timezone.utc).replace(hour=0,minute=0,second=0,microsecond=0)+timedelta(days=1)).timestamp()*1000)
                stop=min(at,next_midnight);amount=(stop-cursor)/1000
                db.execute('INSERT INTO activity VALUES(?,?,?,?,?,?,0,0) ON CONFLICT(day,app,traffic,install,version) DO UPDATE SET seconds=seconds+excluded.seconds',(day(cursor),app,traffic,install,version,amount))
                # First observed duration may start on the prior day.
                db.execute('UPDATE installs SET first_day=MIN(first_day,?) WHERE app=? AND traffic=? AND install=?',(day(cursor),app,traffic,install))
                cursor=stop
        # Retry IDs and paths remain available for 30 days; daily identities for 400 days.
        prune(db,now)
    return '204 No Content',b''

def prune(db, now):
    db.execute('DELETE FROM events WHERE received < ?',(now-30*86400000,))
    db.execute('DELETE FROM sessions WHERE last_seen < ?',(now-30*86400000,))
    cutoff=day(now-400*86400000)
    db.execute('DELETE FROM activity WHERE day < ?',(cutoff,))
    # Retain cohort metadata only while there is retained activity for that installation.
    db.execute('DELETE FROM installs WHERE last_day < ?',(cutoff,))
    if db.execute("SELECT 1 FROM sqlite_master WHERE name='device_aliases' AND type='table'").fetchone():db.execute('DELETE FROM device_aliases WHERE NOT EXISTS (SELECT 1 FROM installs i WHERE i.app=device_aliases.app AND i.traffic=device_aliases.traffic AND i.install=device_aliases.install)')
