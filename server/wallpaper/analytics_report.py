"""Read-only schema 2 report queries. All filters are bound SQL parameters."""
from contextlib import closing
import sqlite3
from analytics import has_verified_traffic
from datetime import date, timedelta, datetime, timezone


def collect(state,start,end,app='gentv_tv',traffic='production'):
    path=state/'analytics.sqlite3'
    if not path.exists():return None
    with closing(sqlite3.connect(path.as_uri()+'?mode=ro',uri=True,timeout=5)) as db, db:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        # Resolve identities at read time: later MAC observations also deduplicate
        # retained history for that installation, without rewriting raw events.
        has_aliases=bool(db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='device_aliases'").fetchone())
        identity_cte="""installs AS (
          SELECT i.app,i.traffic,coalesce(d.device,i.install) AS install,
                 min(i.first_day) AS first_day,max(i.last_day) AS last_day
          FROM main.installs i LEFT JOIN main.device_aliases d
          ON d.app=i.app AND d.traffic=i.traffic AND d.install=i.install
          GROUP BY i.app,i.traffic,coalesce(d.device,i.install)),
        activity AS (
          SELECT a.day,a.app,a.traffic,coalesce(d.device,a.install) AS install,
                 a.version,a.seconds,a.sessions,a.events
          FROM main.activity a LEFT JOIN main.device_aliases d
          ON d.app=a.app AND d.traffic=a.traffic AND d.install=a.install),
        events AS (
          SELECT e.app,e.id,e.traffic,coalesce(d.device,e.install) AS install,
                 e.session,e.occurred,e.received,e.sequence,e.version,e.name,e.value,e.seconds
          FROM main.events e LEFT JOIN main.device_aliases d
          ON d.app=e.app AND d.traffic=e.traffic AND d.install=e.install)"""
        def execute(sql, parameters=()):
            if has_aliases:
                sql='WITH '+identity_cte+(', '+sql[5:] if sql.startswith('WITH ') else ' '+sql)
            return db.execute(sql,parameters)

        if not has_verified_traffic(db):
            traffic={'production':'__no_verified_data__','unverified':'production'}.get(traffic,traffic)
        args=(app,traffic);lo=start.isoformat();hi=end.isoformat()
        active=dict(execute('SELECT day,count(DISTINCT install) FROM activity WHERE app=? AND traffic=? AND day BETWEEN ? AND ? GROUP BY day',args+(lo,hi)))
        new=dict(execute('SELECT first_day,count(*) FROM installs WHERE app=? AND traffic=? AND first_day BETWEEN ? AND ? GROUP BY first_day',args+(lo,hi)))
        seconds=dict(execute('SELECT day,sum(seconds) FROM activity WHERE app=? AND traffic=? AND day BETWEEN ? AND ? GROUP BY day',args+(lo,hi)))
        sessions=dict(execute('SELECT day,sum(sessions) FROM activity WHERE app=? AND traffic=? AND day BETWEEN ? AND ? GROUP BY day',args+(lo,hi)))
        daily=[]
        for i in range((end-start).days+1):
            day=(start+timedelta(days=i)).isoformat();dau=active.get(day,0);sec=seconds.get(day,0)
            daily.append(dict(day=day,new=new.get(day,0),active=dau,seconds=sec,sessions=sessions.get(day,0),average=sec/dau if dau else 0))
        def distinct(a,b):
            return execute('SELECT count(DISTINCT install) FROM activity WHERE app=? AND traffic=? AND day BETWEEN ? AND ?',args+(a,b)).fetchone()[0]
        total=dict(new=sum(r['new'] for r in daily),active=distinct(lo,hi),seconds=sum(r['seconds'] for r in daily),
                   dau=active.get(hi,0),wau=distinct((end-timedelta(days=6)).isoformat(),hi),mau=distinct((end-timedelta(days=29)).isoformat(),hi),sessions=sum(r['sessions'] for r in daily))
        total['average']=total['seconds']/max(1,sum(r['active'] for r in daily))
        total['mac_active']=db.execute(
            'SELECT count(DISTINCT d.device) FROM main.activity a JOIN main.device_aliases d ON d.app=a.app AND d.traffic=a.traffic AND d.install=a.install WHERE a.app=? AND a.traffic=? AND a.day BETWEEN ? AND ?',
            args+(lo,hi)).fetchone()[0] if has_aliases else 0
        total['fallback_active']=total['active']-total['mac_active']

        versions=execute('SELECT version,count(DISTINCT install),sum(seconds) FROM activity WHERE app=? AND traffic=? AND day BETWEEN ? AND ? GROUP BY version ORDER BY count(DISTINCT install) DESC',args+(lo,hi)).fetchall()
        today=datetime.now(timezone.utc).date();cohorts=[]
        for cohort,size in execute('SELECT first_day,count(*) FROM installs WHERE app=? AND traffic=? AND first_day BETWEEN ? AND ? GROUP BY first_day ORDER BY first_day DESC',args+(lo,hi)):
            result=[cohort,size]
            for offset in (1,7,30):
                target=date.fromisoformat(cohort)+timedelta(days=offset)
                if target>=today:result.append(None);continue
                retained=execute('SELECT count(*) FROM installs i WHERE i.app=? AND i.traffic=? AND i.first_day=? AND EXISTS(SELECT 1 FROM activity a WHERE a.app=i.app AND a.traffic=i.traffic AND a.install=i.install AND a.day=?)',args+(cohort,target.isoformat())).fetchone()[0]
                result.append((retained,size))
            cohorts.append(result)
        # Bounded rolling raw-event window. Sequence preserves order across upload batches.
        cutoff=max(lo,(today-timedelta(days=29)).isoformat())
        window=args+(int(datetime.fromisoformat(cutoff).replace(tzinfo=timezone.utc).timestamp()*1000),int(datetime.combine(end+timedelta(days=1),datetime.min.time(),tzinfo=timezone.utc).timestamp()*1000))
        paths=execute('''WITH ordered AS (
          SELECT name||':'||value AS node,
            lag(name||':'||value) OVER(PARTITION BY app,traffic,install,session ORDER BY sequence,occurred,id) AS previous
          FROM events WHERE app=? AND traffic=? AND occurred>=? AND occurred<? AND name IN ('screen_view','action','connection_result','tidy_complete','file_send_result','update_check','feedback_submit','question'))
          SELECT previous,node,count(*) FROM ordered WHERE previous IS NOT NULL AND previous!=node
          GROUP BY previous,node ORDER BY count(*) DESC,previous,node LIMIT 30''',window).fetchall() if cutoff<=hi else []
        observed=bool(execute('SELECT 1 FROM installs WHERE app=? AND traffic=? LIMIT 1',args).fetchone())
        features=execute("SELECT name,value,count(*) FROM events WHERE app=? AND traffic=? AND occurred>=? AND occurred<? AND name NOT IN ('heartbeat','session_duration') GROUP BY name,value ORDER BY count(*) DESC LIMIT 50",window).fetchall() if cutoff<=hi else []
        questions=[]
        question_totals=[]
        if app=='gentv_agent' and cutoff<=hi:
            questions=execute("SELECT occurred,value,version FROM events WHERE app=? AND traffic=? AND occurred>=? AND occurred<? AND name='question' ORDER BY occurred DESC,sequence DESC LIMIT 200",window).fetchall()
            question_totals=execute("SELECT value,count(*) FROM events WHERE app=? AND traffic=? AND occurred>=? AND occurred<? AND name='question' GROUP BY value ORDER BY count(*) DESC,value",window).fetchall()
        return dict(questions=questions,question_totals=question_totals,observed=observed,features=features,daily=daily,totals=total,versions=versions,cohorts=cohorts,paths=paths,path_start=cutoff)
