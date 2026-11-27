import io
import json
import random
import sqlite3
import tempfile
import unittest
from pathlib import Path

import app
import rate_limit
import share_codes

OWNER = 'a' * 64
OTHER = 'b' * 64


class ShareCodesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.state = Path(self.tmp.name)
        self.saved_state = app.STATE
        app.STATE = self.state
        rate_limit._WINDOWS.clear()

    def tearDown(self):
        app.STATE = self.saved_state
        rate_limit._WINDOWS.clear()
        self.tmp.cleanup()

    def call(self, method, path, payload=None, owner=None, peer='192.0.2.1'):
        raw = b'' if payload is None else json.dumps(payload).encode()
        environ = {'REQUEST_METHOD': method, 'PATH_INFO': path, 'CONTENT_LENGTH': str(len(raw)),
                   'wsgi.input': io.BytesIO(raw), 'REMOTE_ADDR': peer}
        if owner:
            environ['HTTP_X_GENTV_OWNER'] = owner
        result = {}
        body = b''.join(app.app(environ, lambda status, headers: result.update(status=status)))
        return result['status'], json.loads(body) if body else None

    def create(self, name='Movies', url='https://github.com/example/apps', owner=OWNER, peer='192.0.2.1'):
        return self.call('POST', '/tv/share-codes', dict(owner=owner, name=name, url=url), peer=peer)

    def test_created_code_opens_on_any_tv(self):
        status, body = self.create()
        self.assertEqual(status, '201 Created')
        self.assertRegex(body['code'], r'^[0-9]{4}$')
        status, found = self.call('GET', '/tv/share-codes/' + body['code'], peer='198.51.100.7')
        self.assertEqual(status, '200 OK')
        self.assertEqual(found, {'code': body['code'], 'name': 'Movies', 'url': 'https://github.com/example/apps'})

    def test_lookup_accepts_lowercase_and_reports_missing(self):
        self.assertEqual(self.call('GET', '/tv/share-codes/1234')[0], '404 Not Found')
        self.assertEqual(self.call('GET', '/tv/share-codes/12345')[0], '404 Not Found')
        status, body = self.create()
        with sqlite3.connect(self.state / 'share_codes.sqlite3') as db:
            db.execute("UPDATE codes SET code='12AB', letters=2 WHERE code=?", (body['code'],))
        self.assertEqual(self.call('GET', '/tv/share-codes/12ab')[0], '200 OK')

    def test_only_owner_can_delete(self):
        code = self.create()[1]['code']
        self.assertEqual(self.call('DELETE', '/tv/share-codes/' + code, owner=OTHER)[0], '404 Not Found')
        self.assertEqual(self.call('DELETE', '/tv/share-codes/' + code)[0], '403 Forbidden')
        self.assertEqual(self.call('DELETE', '/tv/share-codes/' + code, owner=OWNER)[0], '204 No Content')
        self.assertEqual(self.call('GET', '/tv/share-codes/' + code)[0], '404 Not Found')

    def test_owner_secret_is_not_stored(self):
        self.create()
        with sqlite3.connect(self.state / 'share_codes.sqlite3') as db:
            stored = db.execute('SELECT owner FROM codes').fetchone()[0]
        self.assertNotEqual(stored, OWNER)
        self.assertEqual(len(stored), 64)

    def test_four_codes_per_tv(self):
        for index in range(4):
            self.assertEqual(self.create(name=f'Item {index}', peer=f'192.0.2.{index + 1}')[0], '201 Created')
        status, body = self.create(peer='192.0.2.9')
        self.assertEqual((status, body), ('409 Conflict', {'error': 'full'}))
        self.assertEqual(self.create(owner=OTHER, peer='192.0.2.10')[0], '201 Created')

    def test_rejects_bad_links_names_and_fields(self):
        self.assertEqual(self.create(url='javascript:alert(1)')[1], {'error': 'url'})
        self.assertEqual(self.create(url='https://user:pw@example.com/')[1], {'error': 'url'})
        self.assertEqual(self.create(name='')[1], {'error': 'name'})
        self.assertEqual(self.create(name='x' * 16)[1], {'error': 'name'})
        self.assertEqual(self.create(owner='short')[0], '403 Forbidden')
        payload = dict(owner=OWNER, name='Movies', url='https://example.com/', extra=1)
        self.assertEqual(self.call('POST', '/tv/share-codes', payload)[0], '400 Bad Request')
        self.assertEqual(self.call('PUT', '/tv/share-codes')[0], '405 Method Not Allowed')

    def test_writes_are_rate_limited_per_source(self):
        for index in range(12):
            self.create(owner=f'{index:064x}')
        self.assertEqual(self.create(owner='c' * 64)[0], '429 Too Many Requests')
        self.assertEqual(self.create(owner='c' * 64, peer='192.0.2.99')[0], '201 Created')


class AllocationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = share_codes.connect(Path(self.tmp.name))

    def tearDown(self):
        self.db.close()
        self.tmp.cleanup()

    def fill(self, codes):
        self.db.executemany("INSERT INTO codes VALUES(?,?,'o','n','https://example.com/',0)",
                            [(code, share_codes.letter_count(code)) for code in codes])

    def test_tier_sizes(self):
        self.assertEqual([share_codes.tier_size(n) for n in range(5)], [10000, 96000, 345600, 552960, 331776])
        self.assertEqual(sum(1 for _ in share_codes.tier_codes(1)), 96000)

    def test_letters_only_after_every_digit_code_is_taken(self):
        rng = random.Random(5)
        self.fill(f'{value:04d}' for value in range(10000) if value != 4821)
        self.assertEqual(share_codes.allocate(self.db, rng), '4821')
        self.fill(['4821'])
        for _ in range(20):
            code = share_codes.allocate(self.db, rng)
            self.assertEqual(share_codes.letter_count(code), 1)
            self.assertTrue(share_codes.CODE.fullmatch(code))
            self.assertNotIn('I', code)
            self.assertNotIn('O', code)


if __name__ == '__main__':
    unittest.main()
