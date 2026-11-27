"""GenTV TV API: read-only wallpaper/releases and anonymous aggregate session counters."""
from contextlib import closing
import mobile
import analytics
import location
from rate_limit import limited
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
MAX_VIDEO_BYTES = 100 * 1024 * 1024
WALLPAPER_TITLES = {
    'alpine-lake': ('高山湖泊', 'Alpine lake'),
    'autumn': ('秋日', 'Autumn'),
    'coast': ('海岸', 'Coast'),
    'desert': ('沙漠', 'Desert'),
    'forest': ('森林', 'Forest'),
    'milky-way': ('银河', 'Milky Way'),
    'winter': ('冬日', 'Winter'),
}

def wallpaper_titles(path):
    return WALLPAPER_TITLES.get(path.stem, (path.stem, path.stem))

PREVIEW_SUFFIX = '-preview'

def images():
    """Every servable image, gallery previews included."""
    if not MEDIA.is_dir():
        return []
    return [p for p in sorted(MEDIA.iterdir()) if not p.name.startswith('.') and p.suffix.lower() in ALLOWED
            and p.is_file() and not p.is_symlink() and 0 < p.stat().st_size <= MAX_BYTES]

def pictures():
    """Wallpapers proper: previews (``<name>-preview.jpg``) are served but never listed as wallpapers."""
    return [p for p in images() if not p.stem.endswith(PREVIEW_SUFFIX)][:100]

def preview_name(picture, available):
    """The pre-rendered 640x360 preview made by tools/make_wallpaper_previews.py, or the picture itself."""
    candidate = MEDIA / (picture.stem + PREVIEW_SUFFIX + '.jpg')
    return candidate.name if candidate in available else picture.name

def content_version(path):
    # Timestamp metadata can collide on Linux: always derive the public version from bytes.
    with path.open('rb') as source:
        data = source.read(MAX_BYTES + 1)
    if not 0 < len(data) <= MAX_BYTES:
        raise ValueError('Invalid wallpaper size')
    return hashlib.sha256(data).hexdigest()

CAT_VIDEO = 'catWallpaperLoop1080p.mp4'
CAT_PREVIEW = 'catWallpaperLoop1080p-preview.jpg'
AROWANA_VIDEO = 'arowanaWallpaperLoop1080p.mp4'
AROWANA_PREVIEW = 'arowanaWallpaperLoop1080p-preview.jpg'
DEFAULT_IMAGE = 'gentv-default.jpg'
# Optional media is published separately from source deployments. Keep a card hidden
# until both the video and its still preview are present in the public media directory.
EXTRA_VIDEOS = (
    ('pets', 'pet-sunlit-cat', '午后橘猫', 'Sunlit cat', 'pet-sunlit-cat.mp4'),
    ('pets', 'pet-halloween-cat', '万圣夜小猫', 'Halloween kitten', 'pet-halloween-cat.mp4'),
    ('pets', 'pet-tea-bear', '茶会小熊', 'Tea-time teddy', 'pet-tea-bear.mp4'),
    ('pets', 'pet-rainbow-ducks', '彩虹小鸭', 'Rainbow ducklings', 'pet-rainbow-ducks.mp4'),
    ('nature', 'nature-sunlit-leaves', '绿叶光影', 'Sunlit leaves', 'nature-sunlit-leaves.mp4'),
    ('nature', 'nature-alpine-lake', '山间湖泊', 'Mountain lake', 'nature-alpine-lake.mp4'),
    ('dreamy', 'dreamy-sakura-girl', '樱花幻梦', 'Sakura dream', 'dreamy-sakura-girl.mp4'),
    ('dreamy', 'dreamy-afternoon-girl', '午后时光', 'Afternoon reverie', 'dreamy-afternoon-girl.mp4'),
    ('dreamy', 'dreamy-purple-girl', '紫夜少女', 'Violet night', 'dreamy-purple-girl.mp4'),
    ('dreamy', 'dreamy-underwater-girl', '水下幻梦', 'Underwater dream', 'dreamy-underwater-girl.mp4'),
)

def video_preview(name):
    return name[:-4] + '-preview.jpg'

VIDEOS = {CAT_VIDEO, AROWANA_VIDEO} | {entry[4] for entry in EXTRA_VIDEOS}
RESERVED_IMAGES = {DEFAULT_IMAGE, CAT_PREVIEW, AROWANA_PREVIEW} | {
    video_preview(entry[4]) for entry in EXTRA_VIDEOS}

def catalog():
    def item(id, title, en, kind, **extra):
        return dict(id=id, title=title, titleEn=en, kind=kind, **extra)

    def group(id, title, en, items):
        return dict(id=id, title=title, titleEn=en, items=items)

    def asset(name):
        return f'wallpapers/{quote(name)}'

    def extra_video_items(category):
        result = []
        for group_id, video_id, title, title_en, name in EXTRA_VIDEOS:
            if group_id != category:
                continue
            video, preview = MEDIA / name, MEDIA / video_preview(name)
            try:
                if (not video.is_file() or video.is_symlink() or not preview.is_file()
                        or preview.is_symlink() or not 0 < video.stat().st_size <= MAX_VIDEO_BYTES
                        or not 0 < preview.stat().st_size <= MAX_BYTES):
                    continue
            except OSError:
                continue
            result.append(item(video_id, title, title_en, 'video',
                               path=asset(name), preview=asset(preview.name)))
        return result

    available = set(images())
    stills = [item('default', '默认壁纸', 'Default wallpaper', 'image',
                   path=asset(DEFAULT_IMAGE), preview=asset(preview_name(MEDIA / DEFAULT_IMAGE, available)))]
    for picture in pictures():
        if picture.name in RESERVED_IMAGES:
            continue
        title, title_en = wallpaper_titles(picture)
        stills.append(item('image-' + picture.name, title, title_en, 'image',
                           path=asset(picture.name), preview=asset(preview_name(picture, available))))
        if len(stills) >= 24:
            break
    return {'categories': [
        group('pets', '萌宠', 'Pets', [
            item('cat-loop', '小猫', 'Kitten', 'video', path=asset(CAT_VIDEO),
                 preview=asset(CAT_PREVIEW)),
            item('arowana-loop', '金龙鱼', 'Arowana', 'video', path=asset(AROWANA_VIDEO),
                 preview=asset(AROWANA_PREVIEW)),
            *extra_video_items('pets')]),
        # Categories list only wallpapers that are deployed; never "coming soon" stand-ins.
        group('nature', '自然', 'Nature', extra_video_items('nature')),
        group('dreamy', '唯美', 'Dreamy', extra_video_items('dreamy')),
        group('images', '图片', 'Pictures', stills),
        group('shuffle', '随机', 'Shuffle', [
            item('shuffle-all', '全部随机', 'Shuffle all', 'shuffle', scope='all'),
            item('shuffle-pets', '随机播放萌宠', 'Shuffle pets', 'shuffle', scope='pets'),
            item('shuffle-nature', '随机播放自然', 'Shuffle nature', 'shuffle', scope='nature'),
            item('shuffle-dreamy', '随机播放唯美', 'Shuffle dreamy', 'shuffle', scope='dreamy'),
            item('shuffle-images', '随机播放图片', 'Shuffle pictures', 'shuffle', scope='images')]),
    ]}

def app(environ, start_response):
    method = environ.get('REQUEST_METHOD', 'GET')
    path = environ.get('PATH_INFO', '/')
    try:
        path = path.encode('latin-1').decode('utf-8')
    except (UnicodeEncodeError, UnicodeDecodeError):
        pass
    if path == '/tv/analytics':
        return mobile.handle_analytics(environ,start_response,STATE)
    if path in ('/tv/events','/tv/feedback'):
        return mobile.handle(environ,start_response,STATE)
    if path == '/tv/location':
        if method != 'GET':
            start_response('405 Method Not Allowed', [('Allow', 'GET'), ('Content-Length', '0')]); return []
        if limited('location', environ):
            start_response('429 Too Many Requests', [('Retry-After', '60'), ('Content-Length', '0')]); return []
        return location.handle(environ, start_response)
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
            with closing(sqlite3.connect(STATE / 'stats.sqlite3', timeout=5)) as db, db:
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
        if parse_qs(environ.get('QUERY_STRING', '')).get('view') == ['catalog']:
            payload = json.dumps(catalog(), ensure_ascii=False).encode()
            start_response('200 OK', [('Content-Type', 'application/json; charset=utf-8'),
                                      ('Cache-Control', 'no-cache'),
                                      ('Content-Length', str(len(payload)))])
            return [] if method == 'HEAD' else [payload]
        items = []
        for p in pictures():
            try:
                if p.name in RESERVED_IMAGES:
                    continue
                version = content_version(p)
            except (OSError, ValueError):
                continue
            items.append({'id': version, 'title': wallpaper_titles(p)[0],
                          'url': f'{BASE}/wallpapers/{quote(p.name)}?v={version}'})
        body = json.dumps({'wallpapers': items, 'intervalSeconds': 120}, ensure_ascii=False, separators=(',', ':')).encode()
        kind = 'application/json; charset=utf-8'; cache = 'public, max-age=60'
    elif path.startswith('/tv/wallpapers/'):
        name = path[len('/tv/wallpapers/'):]
        # WSGI already decodes PATH_INFO. Accept one filename only; never follow links.
        file = MEDIA / name
        is_video = name in VIDEOS and file.is_file() and not file.is_symlink()
        if '/' in name or '\\' in name or (file not in images() and not is_video):
            start_response('404 Not Found', [('Content-Length', '0')]); return []
        limit = MAX_VIDEO_BYTES if is_video else MAX_BYTES
        try:
            if file.stat().st_size > limit:
                start_response('404 Not Found', [('Content-Length', '0')]); return []
            body = file.read_bytes()
        except OSError:
            start_response('404 Not Found', [('Content-Length', '0')]); return []
        if len(body) > limit:
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
