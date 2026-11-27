import base64
import hashlib
import os
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import report

class ReportTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.state=patch.object(report,'STATE',Path(self.temp.name));self.state.start()
        self.env=patch.dict(os.environ,{'GENTV_REPORT_USER':'admin','GENTV_REPORT_PASSWORD_SHA256':hashlib.sha256(b'test-password').hexdigest()});self.env.start()
    def tearDown(self):
        self.env.stop();self.state.stop();self.temp.cleanup()
    def request(self,query='',auth=True,path='/tv/report/',method='GET'):
        result={};environ={'PATH_INFO':path,'QUERY_STRING':query,'REQUEST_METHOD':method}
        if auth:environ['HTTP_AUTHORIZATION']='Basic '+base64.b64encode(b'admin:test-password').decode()
        body=b''.join(report.app(environ,lambda s,h:result.update(status=s,headers=dict(h))))
        return result,body
    def seed(self):
        with sqlite3.connect(report.STATE/'stats.sqlite3') as db:
            db.execute('CREATE TABLE daily(day TEXT,version INTEGER,sessions INTEGER,seconds INTEGER)')
            db.executemany('INSERT INTO daily VALUES(?,?,?,?)',[('2026-10-28',24,2,90),('2026-10-28',23,1,30),('2026-10-29',24,3,120)])
        with sqlite3.connect(report.STATE/'mobile.sqlite3') as db:
            db.execute('CREATE TABLE totals(day TEXT,version TEXT,name TEXT,value TEXT,count INTEGER)')
            db.executemany('INSERT INTO totals VALUES(?,?,?,?,?)',[('2026-10-28','3.7.1','app_open','',4),('2026-10-29','3.7.1','connection_result','connected',2),('2026-10-29','3.7.1','connection_result','failed',7),('2026-10-29','3.7.1','tidy_complete','',1)])
    def test_auth_fails_closed_and_never_reads_without_auth(self):
        with patch.object(report,'collect',side_effect=AssertionError('must not read')):
            r,b=self.request(auth=False);self.assertEqual(r['status'],'401 Unauthorized')
            with patch.dict(os.environ,{'GENTV_REPORT_PASSWORD_SHA256':''}):self.assertEqual(self.request()[0]['status'],'401 Unauthorized')
        self.assertIn('no-store',r['headers']['Cache-Control'])
    def test_aggregate_range_zero_fill_and_csv(self):
        self.seed();start=report.date(2026,10,28);end=report.date(2026,10,30)
        data=report.collect(start,end)
        self.assertEqual(data['totals'],dict(sessions=6,seconds=240,opens=4,connected=2,tidy=1))
        self.assertEqual(data['daily'][-1]['sessions'],0)
        r,b=self.request('kind=legacy&start=2026-10-28&end=2026-10-28',path='/tv/report/export.csv')
        self.assertEqual(r['status'],'200 OK');self.assertIn('2026-10-28,3,120,4,0,0',b.decode('utf-8-sig'))
        self.assertNotIn('2026-10-29',b.decode())
    def test_page_escapes_database_values_and_is_read_only(self):
        self.seed()
        with sqlite3.connect(report.STATE/'mobile.sqlite3') as db:db.execute('INSERT INTO totals VALUES(?,?,?,?,?)',('2026-10-28','<script>','screen_view','<img>',1))
        before={p.name:p.read_bytes() for p in report.STATE.iterdir()}
        r,b=self.request('start=2026-10-28&end=2026-10-30');text=b.decode()
        self.assertEqual(r['status'],'200 OK');self.assertNotIn('<script>',text);self.assertIn('&lt;script&gt;',text)
        self.assertNotIn('{{',text);self.assertIn('不能代表安装台数',text)
        self.assertEqual(before,{p.name:p.read_bytes() for p in report.STATE.iterdir()})
    def test_empty_missing_invalid_method_and_head(self):
        r,b=self.request();self.assertEqual(r['status'],'200 OK');self.assertIn('尚无数据库',b.decode())
        for q in ['start=oops','start=2026-10-30&end=2026-10-28','start=2020-01-01&end=2026-10-28','end=9999-12-31']:
            self.assertEqual(self.request(q)[0]['status'],'400 Bad Request')
        self.assertEqual(self.request(method='POST')[0]['status'],'405 Method Not Allowed')
        self.assertEqual(self.request(path='/tv/report/private')[0]['status'],'404 Not Found')
        self.assertEqual(self.request(method='HEAD')[1],b'')
    def test_corrupt_database_is_unavailable(self):
        (report.STATE/'stats.sqlite3').write_text('broken')
        self.assertEqual(self.request()[0]['status'],'503 Service Unavailable')

    def test_additional_account_and_original_account_coexist(self):
        import json
        salt = bytes.fromhex('a1' * 16)
        digest = hashlib.pbkdf2_hmac('sha256', b'additional-test-password', salt, 100000).hex()
        users = json.dumps({'reader': 'pbkdf2_sha256$100000$'+salt.hex()+'$'+digest})
        def check(value):
            return report.authorized({'HTTP_AUTHORIZATION':'Basic '+base64.b64encode(value).decode()})
        with patch.dict(os.environ, {'GENTV_REPORT_USERS_JSON': users}):
            self.assertTrue(check(b'admin:test-password'))
            self.assertTrue(check(b'reader:additional-test-password'))
            self.assertFalse(check(b'reader:incorrect'))
            self.assertFalse(check(b'unknown:additional-test-password'))
        for broken in ('invalid', '{"reader":"bad"}', '[]', '{"reader":42}'):
            with patch.dict(os.environ, {'GENTV_REPORT_USERS_JSON': broken}):
                self.assertFalse(check(b'reader:additional-test-password'))

    def test_growth_chart_uses_correct_accessible_metric_names(self):
        svg = report.chart([dict(day='2026-10-28', sessions=1, opens=2)],
                           ('新增安装', '日活跃安装'))
        self.assertIn('每日新增安装和日活跃安装趋势', svg)
        self.assertNotIn('电视会话', svg)
        self.assertIn('新增安装 1', svg)
