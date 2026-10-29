import io
import json
import tempfile
import unittest
from pathlib import Path
import app

class WallpaperApiTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.old=app.MEDIA;self.oldstate=app.STATE;app.MEDIA=Path(self.tmp.name);app.STATE=app.MEDIA/"state"
    def tearDown(self):
        app.MEDIA=self.old;app.STATE=self.oldstate;self.tmp.cleanup()
    def get(self,path='/tv/wallpaper',method='GET',etag=''):
        result={}
        def start(status,headers):result.update(status=status,headers=dict(headers))
        body=b''.join(app.app({'PATH_INFO':path,'QUERY_STRING':'platform=tv','REQUEST_METHOD':method,'HTTP_IF_NONE_MATCH':etag},start))
        return result,body
    def test_old_routes_are_not_exposed(self):
        self.assertEqual(self.get("/wallpaper")[0]["status"], "404 Not Found")
        self.assertEqual(self.get("/wallpapers/one.jpg")[0]["status"], "404 Not Found")
    def test_update_manifest_and_missing(self):
        self.assertEqual(self.get('/tv/update')[0]['status'], '503 Service Unavailable')
        app.STATE.mkdir();(app.STATE/'update.json').write_text('{"versionCode":13}')
        self.assertEqual(json.loads(self.get('/tv/update')[1])['versionCode'],13)
    def test_statistics_aggregate_and_reject_identifiers(self):
        def post(payload):
            raw=json.dumps(payload).encode();result={}
            app.app({'PATH_INFO':'/tv/stats','REQUEST_METHOD':'POST','CONTENT_LENGTH':str(len(raw)),'wsgi.input':io.BytesIO(raw)},
                    lambda status,headers:result.update(status=status))
            return result['status']
        event={'event':'session','versionCode':13,'foregroundSeconds':42}
        self.assertEqual(post(event),'204 No Content');self.assertEqual(post(event),'204 No Content')
        with app.sqlite3.connect(app.STATE/'stats.sqlite3') as db:
            self.assertEqual(db.execute('SELECT sessions,seconds FROM daily').fetchone(),(2,84))
        self.assertEqual(post(dict(event,deviceId='private')),'400 Bad Request')
        self.assertEqual(post(dict(event,foregroundSeconds=-1)),'400 Bad Request')
        self.assertEqual(self.get('/tv/stats')[0]['status'],'405 Method Not Allowed')
    def test_empty(self):
        r,b=self.get();self.assertEqual(r['status'],'200 OK');self.assertEqual(json.loads(b)['wallpapers'],[])
    def test_list_and_media(self):
        (app.MEDIA/'one.jpg').write_bytes(b'fixture')
        r,b=self.get();items=json.loads(b)['wallpapers'];self.assertEqual(len(items),1)
        self.assertTrue(items[0]['url'].startswith('https://api.gentv.app/tv/wallpapers/one.jpg?v='))
        self.assertEqual(self.get('/tv/wallpapers/one.jpg')[1],b'fixture')
    def test_utf8_filenames(self):
        (app.MEDIA/'海岸.jpg').write_bytes(b'fixture')
        self.assertIn('%E6%B5%B7',json.loads(self.get()[1])['wallpapers'][0]['url'])
        path=('/tv/wallpapers/海岸.jpg').encode('utf-8').decode('latin-1')
        self.assertEqual(self.get(path)[1],b'fixture')
    def test_head_and_etag(self):
        r,b=self.get();self.assertEqual(self.get(method='HEAD')[1],b'')
        self.assertEqual(self.get(etag=r['headers']['ETag'])[0]['status'],'304 Not Modified')
    def test_traversal_and_writes_refused(self):
        self.assertEqual(self.get('/tv/wallpapers/../app.py')[0]['status'],'404 Not Found')
        self.assertEqual(self.get(method='POST')[0]['status'],'405 Method Not Allowed')
    def test_symlinks_and_non_images_ignored(self):
        (app.MEDIA/'file.txt').write_text('private');(app.MEDIA/'link.jpg').symlink_to(app.MEDIA/'file.txt')
        self.assertEqual(json.loads(self.get()[1])['wallpapers'],[])

if __name__=='__main__':unittest.main()

class LegacyReleaseCompatibilityTest(unittest.TestCase):
    def test_debug_user_agent_uses_compatible_channel(self):
        import tempfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            state=Path(directory)
            (state/'update.json').write_text('{"versionCode":22}')
            (state/'update-legacy.json').write_text('{"versionCode":21}')
            for agent,expected in [('GenTV/3.6.1',21),('GenTV/3.7.0',22),('curl',22)]:
                result={}
                with patch.object(app,'STATE',state):
                    body=b''.join(app.app({'PATH_INFO':'/tv/update','QUERY_STRING':'platform=tv','REQUEST_METHOD':'GET','HTTP_USER_AGENT':agent},lambda s,h:result.update(status=s)))
                self.assertEqual(result['status'],'200 OK')
                self.assertEqual(json.loads(body)['versionCode'],expected)
