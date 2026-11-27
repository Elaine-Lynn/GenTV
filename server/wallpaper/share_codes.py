"""Share codes: short public codes that open a link on any GenTV.

A TV asks for a code for a link it received from a phone; any TV can then look the code up.
Codes are four characters: all digits while any four-digit code is free, then one letter, then two,
and so on. Only the TV that created a code can delete it; it proves this with a random owner secret
of which the server keeps a SHA-256 digest. No addresses or device identifiers are stored.
"""
from contextlib import closing
from itertools import combinations, product
import hashlib
import json
import random
import re
import sqlite3
import time
from urllib.parse import urlsplit

from rate_limit import limited

DIGITS = '0123456789'
# I and O are left out so a letter is never mistaken for 1 or 0.
LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
LENGTH = 4
CODE = re.compile(r'[0-9A-HJ-NP-Z]{4}')
OWNER = re.compile(r'[0-9a-f]{64}')
NAME_LIMIT = 15
URL_LIMIT = 2048
PER_OWNER = 4
TOTAL = 200000
_RANDOM = random.SystemRandom()


def letter_count(code):
    return sum(character in LETTERS for character in code)


def tier_codes(letters):
    """Every code with exactly ``letters`` letters."""
    for places in combinations(range(LENGTH), letters):
        pools = [LETTERS if index in places else DIGITS for index in range(LENGTH)]
        for characters in product(*pools):
            yield ''.join(characters)


def tier_size(letters):
    size = 1
    for index in range(LENGTH):
        size *= len(LETTERS) if index < letters else len(DIGITS)
    count = 1
    for index in range(letters):
        count = count * (LENGTH - index) // (index + 1)
    return size * count


def random_code(letters, rng):
    places = set(rng.sample(range(LENGTH), letters))
    return ''.join(rng.choice(LETTERS if index in places else DIGITS) for index in range(LENGTH))


def allocate(db, rng=_RANDOM):
    """The next free code, preferring the fewest letters. Runs inside the caller's write transaction."""
    for letters in range(LENGTH + 1):
        taken = db.execute('SELECT count(*) FROM codes WHERE letters=?', (letters,)).fetchone()[0]
        if taken >= tier_size(letters):
            continue
        exists = lambda code: db.execute('SELECT 1 FROM codes WHERE code=?', (code,)).fetchone()
        for _ in range(64):
            code = random_code(letters, rng)
            if not exists(code):
                return code
        # A crowded tier: pick uniformly from what is left.
        used = {row[0] for row in db.execute('SELECT code FROM codes WHERE letters=?', (letters,))}
        free = [code for code in tier_codes(letters) if code not in used]
        if free:
            return rng.choice(free)
    raise OverflowError('No share code left')


def web_url(value):
    if not isinstance(value, str) or not 0 < len(value) <= URL_LIMIT or any(c.isspace() for c in value):
        raise ValueError('url')
    parts = urlsplit(value)
    if parts.scheme.lower() not in ('http', 'https') or not parts.hostname or parts.username or parts.password:
        raise ValueError('url')
    try:
        if parts.port is not None and not 0 < parts.port <= 65535:
            raise ValueError('url')
    except ValueError:
        raise ValueError('url')
    return parts.scheme.lower() + value[len(parts.scheme):]


def valid_name(value):
    if not isinstance(value, str):
        raise ValueError('name')
    value = value.strip()
    if not 0 < len(value) <= NAME_LIMIT or any(ord(c) < 32 or 127 <= ord(c) < 160 for c in value):
        raise ValueError('name')
    return value


def owner_digest(value):
    if not isinstance(value, str) or not OWNER.fullmatch(value):
        raise PermissionError('owner')
    return hashlib.sha256(value.encode()).hexdigest()


def connect(state):
    state.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(state / 'share_codes.sqlite3', timeout=5, isolation_level=None)
    db.execute('CREATE TABLE IF NOT EXISTS codes(code TEXT PRIMARY KEY, letters INTEGER NOT NULL,'
               ' owner TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, created INTEGER NOT NULL)')
    db.execute('CREATE INDEX IF NOT EXISTS codes_owner ON codes(owner)')
    db.execute('CREATE INDEX IF NOT EXISTS codes_letters ON codes(letters)')
    return db


def create(environ, state):
    size = int(environ.get('CONTENT_LENGTH') or '0')
    if not 0 < size <= 8192:
        raise ValueError('body')
    data = json.loads(environ['wsgi.input'].read(size))
    if not isinstance(data, dict) or set(data) != {'owner', 'name', 'url'}:
        raise ValueError('fields')
    owner = owner_digest(data['owner'])
    name, url = valid_name(data['name']), web_url(data['url'])
    with closing(connect(state)) as db:
        db.execute('BEGIN IMMEDIATE')
        try:
            if db.execute('SELECT count(*) FROM codes WHERE owner=?', (owner,)).fetchone()[0] >= PER_OWNER:
                db.execute('ROLLBACK')
                return '409 Conflict', {'error': 'full'}
            if db.execute('SELECT count(*) FROM codes').fetchone()[0] >= TOTAL:
                db.execute('ROLLBACK')
                return '503 Service Unavailable', {'error': 'capacity'}
            code = allocate(db)
            db.execute('INSERT INTO codes VALUES(?,?,?,?,?,?)',
                       (code, letter_count(code), owner, name, url, int(time.time())))
            db.execute('COMMIT')
        except BaseException:
            if db.in_transaction:
                db.execute('ROLLBACK')
            raise
    return '201 Created', {'code': code, 'name': name, 'url': url}


def lookup(code, state):
    if not (state / 'share_codes.sqlite3').exists():
        return '404 Not Found', {'error': 'missing'}
    with closing(connect(state)) as db:
        row = db.execute('SELECT name, url FROM codes WHERE code=?', (code,)).fetchone()
    if row is None:
        return '404 Not Found', {'error': 'missing'}
    return '200 OK', {'code': code, 'name': row[0], 'url': row[1]}


def delete(code, environ, state):
    owner = owner_digest(environ.get('HTTP_X_GENTV_OWNER'))
    if not (state / 'share_codes.sqlite3').exists():
        return '404 Not Found', None
    with closing(connect(state)) as db:
        # Another owner's code looks the same as a missing one.
        removed = db.execute('DELETE FROM codes WHERE code=? AND owner=?', (code, owner)).rowcount
    return ('204 No Content' if removed else '404 Not Found'), None


def handle(environ, start_response, state):
    method = environ.get('REQUEST_METHOD', 'GET')
    path = environ.get('PATH_INFO', '')
    code = None
    if path.startswith('/tv/share-codes/'):
        code = path[len('/tv/share-codes/'):].upper()
        if not CODE.fullmatch(code):
            return reply(start_response, '404 Not Found', {'error': 'missing'})
        allowed = ('GET', 'DELETE')
    else:
        allowed = ('POST',)
    if method not in allowed:
        start_response('405 Method Not Allowed', [('Allow', ', '.join(allowed)), ('Content-Length', '0')])
        return []
    if limited('share-lookup' if method == 'GET' else 'share', environ):
        start_response('429 Too Many Requests', [('Retry-After', '60'), ('Content-Length', '0')])
        return []
    try:
        if method == 'POST':
            status, body = create(environ, state)
        elif method == 'GET':
            status, body = lookup(code, state)
        else:
            status, body = delete(code, environ, state)
    except PermissionError:
        status, body = '403 Forbidden', {'error': 'owner'}
    except (ValueError, KeyError, TypeError, UnicodeError) as error:
        reason = str(error) if str(error) in ('name', 'url') else 'request'
        status, body = '400 Bad Request', {'error': reason}
    except (OSError, sqlite3.Error, OverflowError):
        status, body = '503 Service Unavailable', {'error': 'unavailable'}
    return reply(start_response, status, body)


def reply(start_response, status, body):
    payload = b'' if body is None else json.dumps(body, ensure_ascii=False, separators=(',', ':')).encode()
    headers = [('Cache-Control', 'no-store'), ('X-Content-Type-Options', 'nosniff'),
               ('Content-Length', str(len(payload)))]
    if payload:
        headers.insert(0, ('Content-Type', 'application/json; charset=utf-8'))
    start_response(status, headers)
    return [payload] if payload else []
