import json
import struct
import tempfile
import unittest
from pathlib import Path

import app
import app_icons


def png(width=640, height=360, salt=b''):
    return b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', width, height) + b'\x08\x06\x00\x00\x00' + salt


def jpeg(width=410, height=231):
    app0 = b'\xff\xe0' + struct.pack('>H', 16) + b'JFIF\x00' + b'\x00' * 9
    sof = b'\xff\xc0' + struct.pack('>HBHHB', 11, 8, height, width, 1) + b'\x01\x11\x00'
    return b'\xff\xd8' + app0 + sof + b'\xff\xd9'


class Upstream:
    def __init__(self, items, images):
        self.items, self.images, self.calls = items, images, []

    def __call__(self, url, limit):
        self.calls.append(url)
        if url == 'list':
            if isinstance(self.items, Exception):
                raise self.items
            return json.dumps({'list': self.items}).encode()
        if url not in self.images or isinstance(self.images[url], Exception):
            raise OSError('unreachable')
        return self.images[url]


A = 'https://imgzm.qun7.com/uploads/a.png'
B = 'https://imgzm.qun7.com/uploads/b.jpg'


class AppIconsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.state = Path(self.tmp.name)
        self.old_state = app.STATE
        app.STATE = self.state

    def tearDown(self):
        app.STATE = self.old_state
        self.tmp.cleanup()

    def sync(self, upstream):
        return app_icons.sync(self.state, 'list', fetch=upstream, log=lambda *a, **k: None)

    def get(self, path):
        result = {}
        body = b''.join(app.app({'PATH_INFO': path, 'REQUEST_METHOD': 'GET'},
                                lambda status, headers: result.update(status=status, headers=dict(headers))))
        return result, body

    def test_image_info(self):
        self.assertEqual(app_icons.image_info(png()), ('png', 640, 360))
        self.assertEqual(app_icons.image_info(jpeg()), ('jpg', 410, 231))
        vp8x = b'RIFF' + b'\0' * 4 + b'WEBPVP8X' + b'\0' * 8 + (639).to_bytes(3, 'little') + (359).to_bytes(3, 'little')
        self.assertEqual(app_icons.image_info(vp8x), ('webp', 640, 360))
        self.assertIsNone(app_icons.image_info(b'<html>not an image</html>'))

    def test_only_allowed_hosts_and_package_names_are_mirrored_over_https(self):
        items = [{'packname': 'com.dangbeimarket', 'longicon': A.replace('https', 'http')},
                 {'packname': 'com.dangbeimarket', 'longicon': B},          # duplicate package
                 {'packname': 'cn.beevideo', 'longicon': B},
                 {'packname': 'com.evil', 'longicon': 'http://example.com/x.png'},
                 {'packname': '../etc', 'longicon': A},
                 {'packname': 'com.port', 'longicon': 'http://imgzm.qun7.com:8080/x.png'},
                 'junk']
        upstream = Upstream(items, {A: png(), B: jpeg()})
        index = self.sync(upstream)
        self.assertEqual([i['package'] for i in index['icons']], ['com.dangbeimarket', 'cn.beevideo'])
        self.assertEqual(upstream.calls, ['list', A, B])
        self.assertEqual(index['icons'][1]['width'], 410)
        for icon in index['icons']:
            self.assertTrue((app_icons.files_dir(self.state) / icon['file']).is_file())

    def test_failures_keep_the_last_good_copy(self):
        self.sync(Upstream([{'packname': 'com.a.tv', 'longicon': A}, {'packname': 'com.b.tv', 'longicon': B}],
                           {A: png(), B: jpeg()}))
        first = app_icons.load_index(self.state)
        # The upstream list is down: nothing changes.
        with self.assertRaises(OSError):
            self.sync(Upstream(OSError('down'), {}))
        self.assertEqual(app_icons.load_index(self.state), first)
        with self.assertRaises(ValueError):
            self.sync(Upstream([], {}))
        self.assertEqual(app_icons.load_index(self.state), first)
        # One image fails and one is not an image: both keep their previous file.
        self.sync(Upstream([{'packname': 'com.a.tv', 'longicon': A}, {'packname': 'com.b.tv', 'longicon': B}],
                           {A: OSError('timeout'), B: b'<html>'}))
        self.assertEqual([i['file'] for i in app_icons.load_index(self.state)['icons']],
                         [i['file'] for i in first['icons']])

    def test_replaced_artwork_drops_the_old_file(self):
        self.sync(Upstream([{'packname': 'com.a.tv', 'longicon': A}], {A: png()}))
        old = app_icons.load_index(self.state)['icons'][0]['file']
        self.sync(Upstream([{'packname': 'com.a.tv', 'longicon': A}], {A: png(salt=b'new')}))
        new = app_icons.load_index(self.state)['icons'][0]['file']
        self.assertNotEqual(old, new)
        self.assertEqual([p.name for p in app_icons.files_dir(self.state).iterdir()], [new])

    def test_api_serves_manifest_and_images(self):
        result, body = self.get('/tv/app-icons')
        self.assertEqual(result['status'], '200 OK')
        self.assertEqual(json.loads(body), {'icons': []})
        self.sync(Upstream([{'packname': 'com.a.tv', 'longicon': A}], {A: png()}))
        result, body = self.get('/tv/app-icons')
        icon = json.loads(body)['icons'][0]
        self.assertEqual(set(icon), {'package', 'url', 'width', 'height'})   # upstream address stays private
        self.assertTrue(icon['url'].startswith(app.BASE + '/app-icons/'))
        result, body = self.get('/tv/app-icons/' + icon['url'].rsplit('/', 1)[1])
        self.assertEqual(result['status'], '200 OK')
        self.assertEqual(result['headers']['Content-Type'], 'image/png')
        self.assertIn('immutable', result['headers']['Cache-Control'])
        self.assertEqual(body, png())
        for bad in ('../index.json', 'index.json', '0' * 64 + '.png', 'x/' + '0' * 64 + '.png'):
            self.assertEqual(self.get('/tv/app-icons/' + bad)[0]['status'], '404 Not Found', bad)


if __name__ == '__main__':
    unittest.main()
