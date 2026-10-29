"""GenTV TV API: read-only wallpaper/releases and anonymous aggregate session counters."""
import mobile
from rate_limit import limited
from functools import lru_cache
import hashlib
import json
import mimetypes
import os
import re
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote, parse_qs

MEDIA = Path(os.environ.get('WALLPAPER_DIR', Path(__file__).parent / 'media')).resolve()
BASE = os.environ.get('PUBLIC_BASE_URL', 'https://api.gentv.app/tv').rstrip('/')
STATE = Path(os.environ.get('GENTV_STATE_DIR', '/var/lib/gentv-tv'))
ALLOWED = {'.jpg', '.jpeg', '.png', '.webp'}
MAX_BYTES = 8 * 1024 * 1024

def pictures():
    if not MEDIA.is_dir():
        return []
    return [p for p in sorted(MEDIA.iterdir()) if not p.name.startswith('.') and p.suffix.lower() in ALLOWED
            and p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= MAX_BYTES][:100]

@lru_cache(maxsize=128)
def content_version(path, signature):
    # Stat values only invalidate the server's hash memo; public versions depend on bytes alone.
    with path.open('rb') as source:
        data = source.read(MAX_BYTES + 1)
    if not 0 < len(data) <= MAX_BYTES:
        raise ValueError('Invalid wallpaper size')
    return hashlib.sha256(data).hexdigest()

def app(environ, start_response):
    method = environ.get('REQUEST_METHOD', 'GET')
    path = environ.get('PATH_INFO', '/')
    try:
        path = path.encode('latin-1').decode('utf-8')
    except (UnicodeEncodeError, UnicodeDecodeError):
        pass
    if path in ('/tv/events','/tv/feedback'):
        return mobile.handle(environ,start_response,STATE)
    if path == '/tv/stats':
        if method != 'POST':
            start_response('405 Method Not Allowed', [('Allow', 'POST'), ('Content-Length', '0')]); return []
        if limited('stats',environ):
            start_response('429 Too Many Requests', [('Retry-After','60'),('Content-Length','0')]); return []
        try:
            length = int(environ.get('CONTENT_LENGTH', '0'))
            if not 0 < length <= 1024: raise ValueError()
            event = json.loads(environ['wsgi.input'].read(length))
            if set(event) != {'event', 'versionCode', 'foregroundSeconds'} or event['event'] != 'session': raise ValueError()
            version, seconds = event['versionCode'], event['foregroundSeconds']
            if type(version) is not int or not 1 <= version <= 1000000 or type(seconds) is not int or not 0 <= seconds <= 86400: raise ValueError()
        except (ValueError, KeyError, TypeError):
            start_response('400 Bad Request', [('Content-Length', '0')]); return []
        try:
            STATE.mkdir(parents=True, exist_ok=True)
            with sqlite3.connect(STATE / 'stats.sqlite3', timeout=5) as db:
                db.execute('CREATE TABLE IF NOT EXISTS daily(day TEXT, version INTEGER, sessions INTEGER, seconds INTEGER, PRIMARY KEY(day,version))')
                db.execute('INSERT INTO daily VALUES(?,?,1,?) ON CONFLICT(day,version) DO UPDATE SET sessions=sessions+1,seconds=seconds+excluded.seconds',
                           (datetime.now(timezone.utc).strftime('%Y-%m-%d'), version, seconds))
            start_response('204 No Content', [('Cache-Control', 'no-store'), ('Content-Length', '0')]); return []
        except (OSError, sqlite3.Error):
            start_response('503 Service Unavailable', [('Content-Length', '0')]); return []
    if method not in ('GET', 'HEAD'):

        start_response('405 Method Not Allowed', [('Allow', 'GET, HEAD'), ('Content-Length', '0')]); return []
    if path == '/tv/update':
        platform = parse_qs(environ.get('QUERY_STRING', '')).get('platform', ['mobile'])
        manifest = STATE / ('update.json' if platform == ['tv'] else 'update-mobile.json')
        # Previously distributed TV builds used a debug certificate. Keep their compatible
        # channel immutable; an Android certificate change requires a deliberate reinstall.
        if platform == ['tv'] and re.fullmatch(r'GenTV/3\.(?:[0-6])(?:\.[0-9]+)?',environ.get('HTTP_USER_AGENT','')):
            legacy=STATE/'update-legacy.json'
            if legacy.is_file(): manifest=legacy
        try:
            release = json.loads(manifest.read_text())
        except (OSError, ValueError):
            start_response('503 Service Unavailable', [('Content-Length', '0'), ('Cache-Control', 'no-store')]); return []
        body = json.dumps(release, separators=(',', ':')).encode()
        kind = 'application/json; charset=utf-8'; cache = 'no-cache'
    elif path == '/tv/wallpaper':
        items = []
        for p in pictures():
            try:
                stat = p.stat()
                version = content_version(p, (stat.st_ino, stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns))
            except (OSError, ValueError):
                continue
            items.append({'id': version, 'url': f'{BASE}/wallpapers/{quote(p.name)}?v={version}'})
        body = json.dumps({'wallpapers': items, 'intervalSeconds': 120}, separators=(',', ':')).encode()
        kind = 'application/json; charset=utf-8'; cache = 'public, max-age=60'
    elif path.startswith('/tv/wallpapers/'):
        name = path[len('/tv/wallpapers/'):]
        # WSGI already decodes PATH_INFO. Accept one filename only; never follow links.
        file = MEDIA / name
        if '/' in name or '\\' in name or file not in pictures():
            start_response('404 Not Found', [('Content-Length', '0')]); return []
        try:
            body = file.read_bytes()
        except OSError:
            start_response('404 Not Found', [('Content-Length', '0')]); return []
        if len(body) > MAX_BYTES:
            start_response('404 Not Found', [('Content-Length', '0')]); return []
        kind = mimetypes.guess_type(name)[0] or 'application/octet-stream'; cache = 'public, max-age=86400'
    else:
        start_response('404 Not Found', [('Content-Length', '0')]); return []
    etag = '"' + hashlib.sha256(body).hexdigest() + '"'
    headers = [('Content-Type', kind), ('Cache-Control', cache), ('ETag', etag), ('X-Content-Type-Options', 'nosniff')]
    if environ.get('HTTP_IF_NONE_MATCH') == etag:
        start_response('304 Not Modified', headers); return []
    start_response('200 OK', headers + [('Content-Length', str(len(body)))])
    return [] if method == 'HEAD' else [body]

if __name__ == '__main__':
    from socketserver import ThreadingMixIn
    from wsgiref.simple_server import WSGIServer, make_server
    class Server(ThreadingMixIn, WSGIServer):
        daemon_threads = True
    with make_server('127.0.0.1', int(os.environ.get('PORT', '18765')), app, server_class=Server) as httpd:
        httpd.serve_forever()
