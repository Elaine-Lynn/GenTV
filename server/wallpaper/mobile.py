from rate_limit import limited
"""Bounded mobile ingestion. No public read routes, no IP or device IDs in analytics."""
import base64
import json
import re
import sqlite3
import time
import uuid

VALUES = {
    'app_open': {''}, 'tidy_complete': {''},
    'screen_view': set('welcome unsupported get_ready connect hub review tidying home_screen done remote send_files check rescue space ads apps settings desktop'.split()),
    'connection_result': {'connected', 'denied', 'unreachable', 'failed'},
    'file_send_result': {'sent', 'failed', 'no_space', 'cancelled'},
    'update_check': {'current', 'available', 'failed', 'unconfigured'},
    'feedback_submit': {'success', 'failed', 'unconfigured'},
}

def read(environ, limit):
    size = int(environ.get('CONTENT_LENGTH', '0'))
    if not 0 < size <= limit: raise ValueError('body limit')
    value = json.loads(environ['wsgi.input'].read(size))
    if not isinstance(value, dict): raise ValueError('object required')
    return value

def ident(value):
    if not isinstance(value, str) or len(value) != 36: raise ValueError('UUID required')
    return str(uuid.UUID(value))

def events(environ, state):
    batch = read(environ, 65536)
    if set(batch) != {'schema_version','app','app_version','android_sdk','session_id','events'}: raise ValueError('fields')
    if type(batch['schema_version']) is not int or batch['schema_version'] != 1 or batch['app'] != 'gentv_mobile': raise ValueError('schema')
    version=batch['app_version']; sdk=batch['android_sdk']; ident(batch['session_id'])
    if not isinstance(version,str) or not re.fullmatch(r'[0-9]+(?:\.[0-9]+){0,2}(?:-[a-zA-Z0-9.]+)?',version) or len(version)>40: raise ValueError('version')
    if type(sdk) is not int or not 26 <= sdk <= 100: raise ValueError('SDK')
    rows=batch['events']; now=int(time.time()*1000)
    if not isinstance(rows,list) or not 1 <= len(rows) <= 50: raise ValueError('batch')
    validated=[]
    for row in rows:
        if not isinstance(row,dict) or set(row) != {'event_id','occurred_at','name','value'}: raise ValueError('event fields')
        event_id=ident(row['event_id']); timestamp=row['occurred_at']; name=row['name']; value=row['value']
        if type(timestamp) is not int or not now-2*86400000 <= timestamp <= now+300000: raise ValueError('time')
        if not isinstance(name,str) or not isinstance(value,str) or name not in VALUES or value not in VALUES[name]: raise ValueError('event value')
        validated.append((event_id,timestamp,now,version,name,value))
    state.mkdir(parents=True,exist_ok=True)
    with sqlite3.connect(state/'mobile.sqlite3',timeout=5) as db:
        db.execute('CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY, occurred INTEGER, received INTEGER, version TEXT, name TEXT, value TEXT)')
        db.execute('CREATE TABLE IF NOT EXISTS totals(day TEXT, version TEXT, name TEXT, value TEXT, count INTEGER, PRIMARY KEY(day,version,name,value))')
        for row in validated:
            cur=db.execute('INSERT OR IGNORE INTO events VALUES(?,?,?,?,?,?)',row)
            if cur.rowcount:
                day=time.strftime('%Y-%m-%d',time.gmtime(row[1]/1000))
                db.execute('INSERT INTO totals VALUES(?,?,?,?,1) ON CONFLICT(day,version,name,value) DO UPDATE SET count=count+1',(day,row[3],row[4],row[5]))
        db.execute('DELETE FROM events WHERE received < ?', (now-30*86400000,))
    return '204 No Content', b''

def feedback(environ,state):
    data=read(environ,2*1024*1024)
    required={'app','type','feedback','page','platform','version','device','os','email','user_id','source'}
    if not required <= set(data) or set(data)-required-{'screenshot_base64'}: raise ValueError('fields')
    if any(not isinstance(data[k],str) for k in data): raise ValueError('strings required')
    if data['app']!='GenTV' or data['platform']!='android' or data['source']!='in-app': raise ValueError('app')
    if data['type'] not in {'bug','feature','praise','question'}: raise ValueError('type')
    if not data['feedback'].strip() or len(data['feedback'])>24000: raise ValueError('feedback')
    for key in required-{'feedback'}:
        if len(data[key])>256: raise ValueError('field too long')
    if 'screenshot_base64' in data:
        image=base64.b64decode(data['screenshot_base64'],validate=True)
        if len(image)>1024*1024 or not (image.startswith(b'\xff\xd8\xff') or image.startswith(b'\x89PNG\r\n\x1a\n')): raise ValueError('image')
    state.mkdir(parents=True,exist_ok=True)
    now=int(time.time())
    with sqlite3.connect(state/'feedback.sqlite3',timeout=5) as db:
        db.execute('CREATE TABLE IF NOT EXISTS feedback(id TEXT PRIMARY KEY, received INTEGER, payload TEXT)')
        db.execute('DELETE FROM feedback WHERE received < ?', (now-30*86400,))
        if db.execute('SELECT count(*) FROM feedback').fetchone()[0]>=10000: return '503 Service Unavailable',b'{"success":false}'
        db.execute('INSERT INTO feedback VALUES(?,?,?)',(str(uuid.uuid4()),now,json.dumps(data,separators=(',',':'))))
    return '201 Created',b'{"success":true}'

def handle(environ,start_response,state):
    kind=environ['PATH_INFO'].rsplit('/',1)[-1]
    if environ.get('REQUEST_METHOD')!='POST':
        start_response('405 Method Not Allowed',[('Allow','POST'),('Content-Length','0')]); return []
    if limited(kind,environ):
        start_response('429 Too Many Requests',[('Retry-After','60'),('Content-Length','0')]); return []
    try: status,body=(events if kind=='events' else feedback)(environ,state)
    except (ValueError,KeyError,TypeError,UnicodeError): status,body='400 Bad Request',b'{"success":false}'
    except (OSError,sqlite3.Error): status,body='503 Service Unavailable',b'{"success":false}'
    start_response(status,[('Content-Type','application/json'),('Cache-Control','no-store'),('Content-Length',str(len(body)))]); return [body]
