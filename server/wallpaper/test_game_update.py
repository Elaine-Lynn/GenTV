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
