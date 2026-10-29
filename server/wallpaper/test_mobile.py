import io,json,tempfile,time,uuid,sqlite3,unittest
from pathlib import Path
import mobile
import rate_limit
class MobileApiTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.state=Path(self.tmp.name);rate_limit._WINDOWS.clear()
    def tearDown(self):self.tmp.cleanup()
    def post(self,path,payload):
        raw=json.dumps(payload).encode();result={}
        body=b''.join(mobile.handle({'PATH_INFO':path,'REQUEST_METHOD':'POST','CONTENT_LENGTH':str(len(raw)),'wsgi.input':io.BytesIO(raw)},lambda status,headers:result.update(status=status),self.state))
        return result['status'],body
    def batch(self):return dict(schema_version=1,app='gentv_mobile',app_version='0.9.0',android_sdk=35,session_id=str(uuid.uuid4()),events=[dict(event_id=str(uuid.uuid4()),occurred_at=int(time.time()*1000),name='app_open',value='')])
    def test_duplicate_is_atomic_and_counted_once(self):
        data=self.batch();self.assertEqual(self.post('/tv/events',data)[0],'204 No Content');self.assertEqual(self.post('/tv/events',data)[0],'204 No Content')
        with sqlite3.connect(self.state/'mobile.sqlite3') as db:self.assertEqual(db.execute('SELECT count FROM totals').fetchone()[0],1)
    def test_bad_batch_is_fully_rejected(self):
        data=self.batch();data['events'].append(dict(event_id=str(uuid.uuid4()),occurred_at=int(time.time()*1000),name='private_data',value='secret'))
        self.assertEqual(self.post('/tv/events',data)[0],'400 Bad Request');self.assertFalse((self.state/'mobile.sqlite3').exists())
    def test_feedback_confirmed_only_after_save(self):
        data=dict(app='GenTV',type='question',feedback='Integration test',page='settings',platform='android',version='0.9.0',device='test',os='35',email='',user_id='test',source='in-app')
        status,body=self.post('/tv/feedback',data);self.assertEqual(status,'201 Created');self.assertTrue(json.loads(body)['success'])
        data['extra']='unknown';self.assertEqual(self.post('/tv/feedback',data)[0],'400 Bad Request')
    def test_rate_limit(self):
        for _ in range(12):mobile.limited('feedback',{})
        self.assertTrue(mobile.limited('feedback',{}))

    def test_quota_isolated_and_proxy_header_only_trusted_locally(self):
        for _ in range(12): self.assertFalse(mobile.limited('feedback',{'REMOTE_ADDR':'192.0.2.1'}))
        self.assertTrue(mobile.limited('feedback',{'REMOTE_ADDR':'192.0.2.1','HTTP_X_GENTV_PEER':'forged'}))
        self.assertFalse(mobile.limited('feedback',{'REMOTE_ADDR':'192.0.2.2'}))
        self.assertFalse(mobile.limited('feedback',{'REMOTE_ADDR':'127.0.0.1','HTTP_X_GENTV_PEER':'192.0.2.3'}))
        self.assertTrue(all(isinstance(key[1],bytes) for key in rate_limit._WINDOWS))
