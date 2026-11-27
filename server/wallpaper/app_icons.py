"""Mirror the Dangbei launcher's app tile artwork onto the GenTV server.

The upstream list (``{"list":[{"packname":..., "longicon":...}]}``) maps package names to tile images.
``sync()`` downloads every image, stores it under its SHA-256 and writes ``index.json`` last, so clients
only ever see a complete set and never talk to the upstream service themselves. No device data is sent.

Run ``python3 app_icons.py`` (daily via gentv-app-icons.timer).
"""
import hashlib
import json
import os
import re
import struct
import sys
import tempfile
import time
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

STATE = Path(os.environ.get('GENTV_STATE_DIR', '/var/lib/gentv-tv'))
SOURCE_URL = os.environ.get('APP_ICON_SOURCE_URL', 'http://zmapi.dangbei.net/v2/appinfo?2')
IMAGE_HOSTS = frozenset(h.strip().lower() for h in
                        os.environ.get('APP_ICON_IMAGE_HOSTS', 'imgzm.qun7.com').split(',') if h.strip())
USER_AGENT = 'GenTV-AppIcons/1.0'
MAX_LIST_BYTES = 1024 * 1024
MAX_IMAGE_BYTES = 2 * 1024 * 1024
MAX_ICONS = 1000
MAX_SIDE = 4096
TIMEOUT = 20
PACKAGE = re.compile(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+')
FILE_NAME = re.compile(r'[0-9a-f]{64}\.(?:png|jpg|webp)')
EXTENSIONS = {'png': 'image/png', 'jpg': 'image/jpeg', 'webp': 'image/webp'}


def root(state=None):
    return Path(state or STATE) / 'app-icons'


def files_dir(state=None):
    return root(state) / 'files'


def index_path(state=None):
    return root(state) / 'index.json'


def load_index(state=None):
    """The last complete index, or an empty one; never raises for a missing or damaged file."""
    try:
        data = json.loads(index_path(state).read_text(encoding='utf-8'))
        icons = data.get('icons')
        return data if isinstance(icons, list) else {'icons': []}
    except (OSError, ValueError, AttributeError):
        return {'icons': []}


def image_info(data):
    """(extension, width, height) for a PNG, JPEG or WebP image, or None for anything else."""
    if data[:8] == b'\x89PNG\r\n\x1a\n' and len(data) >= 24 and data[12:16] == b'IHDR':
        width, height = struct.unpack('>II', data[16:24])
        return 'png', width, height
    if data[:2] == b'\xff\xd8':
        i = 2
        while i + 9 < len(data):
            if data[i] != 0xFF:
                return None
            marker = data[i + 1]
            if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
                i += 2
                continue
            length = struct.unpack('>H', data[i + 2:i + 4])[0]
            # SOF0..SOF15 carry the frame size, except DHT (C4), JPG (C8) and DAC (CC).
            if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
                height, width = struct.unpack('>HH', data[i + 5:i + 9])
                return 'jpg', width, height
            if length < 2:
                return None
            i += 2 + length
        return None
    if data[:4] == b'RIFF' and data[8:12] == b'WEBP' and len(data) >= 30:
        chunk = data[12:16]
        if chunk == b'VP8 ' and data[23:26] == b'\x9d\x01\x2a':
            width, height = struct.unpack('<HH', data[26:30])
            return 'webp', width & 0x3FFF, height & 0x3FFF
        if chunk == b'VP8L' and data[20] == 0x2F:
            bits = int.from_bytes(data[21:25], 'little')
            return 'webp', (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
        if chunk == b'VP8X':
            return 'webp', int.from_bytes(data[24:27], 'little') + 1, int.from_bytes(data[27:30], 'little') + 1
    return None


def image_url(raw):
    """The upstream image address upgraded to HTTPS, or None when it is not on an allowed image host."""
    if not isinstance(raw, str):
        return None
    parts = urlsplit(raw.strip())
    if parts.scheme not in ('http', 'https') or (parts.hostname or '').lower() not in IMAGE_HOSTS \
            or parts.username or parts.password or parts.port not in (None, 80, 443):
        return None
    return urlunsplit(('https', parts.hostname.lower(), parts.path, parts.query, ''))


def fetch(url, limit):
    request = urllib.request.Request(url, headers={'User-Agent': USER_AGENT, 'Accept': '*/*'})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        if response.status != 200:
            raise OSError(f'HTTP {response.status}')
        data = response.read(limit + 1)
    if len(data) > limit:
        raise OSError('Response too large')
    return data


def upstream_entries(payload):
    """Valid, de-duplicated (package, https image URL) pairs in upstream order."""
    try:
        items = json.loads(payload.decode('utf-8')).get('list')
    except (ValueError, UnicodeDecodeError, AttributeError) as error:
        raise ValueError('Upstream list is not JSON') from error
    if not isinstance(items, list):
        raise ValueError('Upstream list is missing')
    entries, seen = [], set()
    for item in items:
        if not isinstance(item, dict):
            continue
        package, url = item.get('packname'), image_url(item.get('longicon'))
        if not isinstance(package, str) or not PACKAGE.fullmatch(package) or len(package) > 255 \
                or url is None or package in seen:
            continue
        seen.add(package)
        entries.append((package, url))
        if len(entries) >= MAX_ICONS:
            break
    return entries


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(dir=path.parent, prefix='.tmp-')
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
        os.chmod(temp, 0o644)
        os.replace(temp, path)
    except BaseException:
        try:
            os.unlink(temp)
        except OSError:
            pass
        raise


def sync(state=None, source_url=None, fetch=fetch, log=print):
    """Mirror the upstream artwork. Returns the new index; on failure the previous one stays in place."""
    files = files_dir(state)
    files.mkdir(parents=True, exist_ok=True)
    previous = {icon.get('package'): icon for icon in load_index(state)['icons'] if isinstance(icon, dict)}
    entries = upstream_entries(fetch(source_url or SOURCE_URL, MAX_LIST_BYTES))
    if not entries:
        raise ValueError('Upstream list has no usable icons')
    icons, failed = [], 0
    for package, url in entries:
        try:
            data = fetch(url, MAX_IMAGE_BYTES)
            info = image_info(data)
            if info is None or not (0 < info[1] <= MAX_SIDE and 0 < info[2] <= MAX_SIDE):
                raise ValueError('Not a usable PNG, JPEG or WebP image')
            extension, width, height = info
            digest = hashlib.sha256(data).hexdigest()
            name = f'{digest}.{extension}'
            if not (files / name).is_file():
                atomic_write(files / name, data)
            icons.append({'package': package, 'file': name, 'sha256': digest,
                          'width': width, 'height': height, 'source': url})
        except (OSError, ValueError) as error:
            failed += 1
            # Keep the last good copy for this package rather than dropping its tile artwork.
            old = previous.get(package)
            if old and FILE_NAME.fullmatch(str(old.get('file', ''))) and (files / old['file']).is_file():
                icons.append(old)
            log(f'app-icons: {package}: {error}', file=sys.stderr)
    if not icons:
        raise ValueError('No icon could be downloaded')
    index = {'source': 'dangbei', 'syncedAt': int(time.time()), 'icons': icons}
    atomic_write(index_path(state), json.dumps(index, ensure_ascii=False, indent=1).encode('utf-8'))
    referenced = {icon['file'] for icon in icons}
    for path in files.iterdir():
        if path.name not in referenced and (FILE_NAME.fullmatch(path.name) or path.name.startswith('.tmp-')):
            try:
                path.unlink()
            except OSError:
                pass
    log(f'app-icons: {len(icons)} icons mirrored, {failed} failed')
    return index


def manifest(base, state=None):
    """The public list: package name to a GenTV-hosted, content-addressed image URL."""
    files = files_dir(state)
    icons = []
    for icon in load_index(state)['icons']:
        if not isinstance(icon, dict):
            continue
        name, package = str(icon.get('file', '')), icon.get('package')
        if not FILE_NAME.fullmatch(name) or not isinstance(package, str) or not PACKAGE.fullmatch(package):
            continue
        path = files / name
        if not path.is_file() or path.is_symlink():
            continue
        icons.append({'package': package, 'url': f'{base}/app-icons/{name}',
                      'width': icon.get('width'), 'height': icon.get('height')})
    return {'icons': icons}


def image_file(name, state=None):
    """The stored image for a public file name, or None."""
    if not FILE_NAME.fullmatch(name):
        return None
    path = files_dir(state) / name
    try:
        if path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= MAX_IMAGE_BYTES:
            return path
    except OSError:
        pass
    return None


def content_type(name):
    return EXTENSIONS[name.rsplit('.', 1)[1]]


if __name__ == '__main__':
    try:
        sync()
    except (OSError, ValueError) as error:
        print(f'app-icons: sync failed, previous icons kept: {error}', file=sys.stderr)
        sys.exit(1)
