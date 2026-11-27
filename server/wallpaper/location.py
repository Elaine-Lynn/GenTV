"""City-level AMap IP lookup. Key stays server-side; no persistent IP storage."""
import hashlib
import ipaddress
import json
import os
import threading
import time
from urllib.parse import urlencode
from urllib.request import urlopen, Request

_CACHE = {}
_LOCK = threading.Lock()
_SALT = os.urandom(32)


def source_ip(environ):
    peer = environ.get('REMOTE_ADDR', '')
    # Only the local reverse proxy may supply this header; it must overwrite it.
    value = environ.get('HTTP_X_GENTV_PEER', peer) if peer in ('127.0.0.1', '::1') else peer
    address = ipaddress.ip_address(value)
    if address.version != 4 or not address.is_global:
        raise ValueError('Public IPv4 required')
    return str(address)


def lookup(address, key):
    cache_key = hashlib.blake2b(address.encode(), key=_SALT, digest_size=16).digest()
    now = time.monotonic()
    with _LOCK:
        cached = _CACHE.get(cache_key)
        if cached and cached[0] > now:
            return cached[1]
    # Explicit client IP is essential: otherwise AMap would locate our server.
    url = 'https://restapi.amap.com/v3/ip?' + urlencode({'key': key, 'ip': address, 'output': 'json'})
    request = Request(url, headers={'User-Agent': 'GenTV-Location/1'})
    with urlopen(request, timeout=5) as response:
        raw = response.read(16385)
    if len(raw) > 16384:
        raise ValueError('Location response too large')
    data = json.loads(raw)
    province, city, adcode = data.get('province'), data.get('city'), data.get('adcode')
    if data.get('status') != '1' or data.get('infocode') != '10000':
        raise ValueError('Location provider unavailable')
    if not isinstance(province, str) or not isinstance(city, str) or not province or not city:
        raise ValueError('City unavailable')
    if len(province) > 80 or len(city) > 80 or not isinstance(adcode, str) or not adcode.isdigit() or len(adcode) != 6:
        raise ValueError('Invalid city')
    result = {'province': province, 'city': city, 'adcode': adcode, 'source': 'amap-ip'}
    with _LOCK:
        for expired in [k for k, v in _CACHE.items() if v[0] <= now]:
            del _CACHE[expired]
        if len(_CACHE) >= 4096:
            del _CACHE[next(iter(_CACHE))]
        _CACHE[cache_key] = (now + 6 * 3600, result)
    return result


def handle(environ, start_response):
    def reply(status, data):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        start_response(status, [('Content-Type', 'application/json; charset=utf-8'),
            ('Cache-Control', 'no-store'), ('Content-Length', str(len(body)))])
        return [body]
    key = os.environ.get('AMAP_WEB_SERVICE_KEY', '').strip()
    if not key:
        return reply('503 Service Unavailable', {'error': 'location_not_configured'})
    try:
        address = source_ip(environ)
    except ValueError:
        return reply('422 Unprocessable Entity', {'error': 'public_ipv4_required'})
    try:
        return reply('200 OK', lookup(address, key))
    except Exception:
        # Never expose provider URLs, API keys or source addresses in errors/logs.
        return reply('503 Service Unavailable', {'error': 'location_unavailable'})
