import json
from pathlib import Path
import tempfile
import unittest
import app


class GameUpdateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old = app.STATE
        app.STATE = Path(self.temp.name)

    def tearDown(self):
        app.STATE = self.old
        self.temp.cleanup()

    def get(self, abi, method='GET'):
        result = {}
        body = b''.join(app.app({'PATH_INFO': '/tv/plugins/game/update',
            'QUERY_STRING': 'abi=' + abi, 'REQUEST_METHOD': method},
            lambda status, headers: result.update(status=status, headers=dict(headers))))
        return result, body

    def test_independent_version_and_abi_selection(self):
        folder = app.STATE / 'plugins' / 'game'
        folder.mkdir(parents=True)
        for abi, version in [('armeabi-v7a', 2), ('arm64-v8a', 3)]:
            (folder / ('update-' + abi + '.json')).write_text(json.dumps({'versionCode': version, 'abi': abi}))
            result, body = self.get(abi)
            self.assertEqual(result['status'], '200 OK')
            self.assertEqual(json.loads(body)['versionCode'], version)
            self.assertEqual(result['headers']['Cache-Control'], 'no-cache')
            self.assertEqual(self.get(abi, 'HEAD')[1], b'')

    def test_missing_malformed_and_unknown_abi(self):
        self.assertEqual(self.get('armeabi-v7a')[0]['status'], '503 Service Unavailable')
        self.assertEqual(self.get('../update')[0]['status'], '400 Bad Request')
        folder = app.STATE / 'plugins' / 'game'
        folder.mkdir(parents=True)
        manifest = folder / 'update-armeabi-v7a.json'
        manifest.write_text('{')
        self.assertEqual(self.get('armeabi-v7a')[0]['status'], '503 Service Unavailable')
        manifest.write_text('{"abi":"arm64-v8a"}')
        self.assertEqual(self.get('armeabi-v7a')[0]['status'], '503 Service Unavailable')

    def request_path(self, path, method='GET', etag=''):
        result = {}
        body = b''.join(app.app({'PATH_INFO': path, 'REQUEST_METHOD': method, 'HTTP_IF_NONE_MATCH': etag},
            lambda status, headers: result.update(status=status, headers=dict(headers))))
        return result, body

    def test_catalog_and_independent_child_update(self):
        result, body = self.request_path('/tv/plugins/games/catalog')
        self.assertEqual(result['status'], '200 OK')
        self.assertEqual(json.loads(body)['games'][0]['id'], 'kart')
        self.assertEqual(self.request_path('/tv/plugins/games/catalog', etag=result['headers']['ETag'])[0]['status'], '304 Not Modified')
        directory = app.STATE / 'plugins' / 'games' / 'kart'
        directory.mkdir(parents=True)
        for version in (1, 2):
            (directory / 'update.json').write_text(json.dumps({'id': 'kart', 'packageName': 'com.gentv.game.kart.plugin', 'versionCode': version}))
            result, body = self.request_path('/tv/plugins/games/kart/update')
            self.assertEqual(result['status'], '200 OK')
            self.assertEqual(json.loads(body)['versionCode'], version)
            self.assertEqual(self.request_path('/tv/plugins/games/kart/update', 'HEAD')[1], b'')
        self.assertFalse((app.STATE / 'update.json').exists())
        self.assertFalse((app.STATE / 'plugins' / 'game').exists())

    def test_child_missing_identity_and_invalid_catalog(self):
        self.assertEqual(self.request_path('/tv/plugins/games/kart/update')[0]['status'], '503 Service Unavailable')
        self.assertEqual(self.request_path('/tv/plugins/games/../update')[0]['status'], '404 Not Found')
        self.assertEqual(self.request_path('/tv/plugins/games/kart/update', 'POST')[0]['status'], '405 Method Not Allowed')
        directory = app.STATE / 'plugins' / 'games' / 'kart'
        directory.mkdir(parents=True)
        (directory / 'update.json').write_text('{"id":"other"}')
        self.assertEqual(self.request_path('/tv/plugins/games/kart/update')[0]['status'], '503 Service Unavailable')
        for bad in ('{', '[]', '{"schemaVersion":1,"games":[null]}', '{"schemaVersion":1,"games":[{"id":"../kart"}]}'):
            (directory.parent / 'catalog.json').write_text(bad)
            self.assertEqual(self.request_path('/tv/plugins/games/catalog')[0]['status'], '503 Service Unavailable')
