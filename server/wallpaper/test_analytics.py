import hashlib,hmac,io,json,os,sqlite3,tempfile,time,unittest,uuid
from pathlib import Path
from unittest.mock import patch
from datetime import date,datetime,timezone
import analytics,analytics_report,mobile,rate_limit

class AnalyticsTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.state=Path(self.tmp.name);self.now=int(datetime(2026,11,3,12,tzinfo=timezone.utc).timestamp()*1000)
        self.clock=patch.object(analytics.time,'time',return_value=self.now/1000);self.clock.start();rate_limit._WINDOWS.clear()
        self.install=str(uuid.uuid4());self.session=str(uuid.uuid4())
        self.keys={app:('fixture-key-for-'+app).encode()*2 for app in analytics.APPS}
        env={}
        for app,key in self.keys.items():
            path=self.state/app
            path.write_bytes(key)
            path.chmod(0o600)
            env['GENTV_ANALYTICS_'+app.upper()+'_KEY_FILE']=str(path)
        self.env=patch.dict(os.environ,env)
        self.env.start()
        self.addCleanup(self.env.stop)
    def tearDown(self):self.clock.stop();self.tmp.cleanup()
    def event(self,seq=0,name='app_open',value='',seconds=0,at=None):return dict(event_id=str(uuid.uuid4()),occurred_at=at or self.now,sequence=seq,name=name,value=value,foreground_seconds=seconds)
    def batch(self,rows=None,traffic='production'):
        return dict(schema_version=2,app='gentv_tv',app_version='3.7.3',android_sdk=34,install_id=self.install,session_id=self.session,traffic=traffic,events=rows or [self.event()])
    def post(self,batch,source='matching'):
        raw=json.dumps(batch).encode();result={}
        headers={'REQUEST_METHOD':'POST','CONTENT_LENGTH':str(len(raw)),'wsgi.input':io.BytesIO(raw)}
        if source and batch['traffic']=='production':
            key=self.keys[batch['app'] if source=='matching' else source]
            headers['HTTP_X_GENTV_ANALYTICS_SIGNATURE']=hmac.new(key,raw,hashlib.sha256).hexdigest()
        mobile.handle_analytics(headers,lambda s,h:result.update(status=s),self.state)
        return result['status']
    def data(self,traffic='production'):
        return analytics_report.collect(self.state,date(2026,11,1),date(2026,11,3),traffic=traffic)
    def test_retries_deduplicate_and_same_install_is_one_active(self):
        b=self.batch([self.event(),self.event(1,'heartbeat',seconds=60)])
        self.assertEqual(self.post(b),'204 No Content');self.assertEqual(self.post(b),'204 No Content')
        self.session=str(uuid.uuid4());self.assertEqual(self.post(self.batch()),'204 No Content')
        d=self.data();self.assertEqual(d['totals']['active'],1);self.assertEqual(d['totals']['new'],1)
        self.assertEqual(d['totals']['seconds'],60);self.assertEqual(d['totals']['sessions'],2)
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:self.assertNotIn(self.install,str(db.execute('SELECT * FROM installs').fetchall()))
    def test_test_traffic_separate_and_two_apps_not_combined(self):
        self.post(self.batch(traffic='test'));self.assertEqual(self.data()['totals']['active'],0);self.assertEqual(self.data('test')['totals']['active'],1)
        b=self.batch();b['app']='gentv_mobile';self.post(b);self.assertEqual(self.data()['totals']['active'],0)
    def test_invalid_batch_atomic_and_durations_bounded(self):
        for mutation in [dict(name='private'),dict(value='192.168.1.1'),dict(foreground_seconds=121),dict(sequence=-1),dict(occurred_at=0)]:
            e=self.event();e.update(mutation);self.assertEqual(self.post(self.batch([self.event(),e])),'400 Bad Request')
        self.assertFalse((self.state/'analytics.sqlite3').exists())
        b=self.batch();b['device_id']='secret';self.assertEqual(self.post(b),'400 Bad Request')
    def test_midnight_duration_split_and_cohort_not_reset_by_upgrade(self):
        at=int(datetime(2026,11,2,0,0,30,tzinfo=timezone.utc).timestamp()*1000)
        self.post(self.batch([self.event(name='heartbeat',seconds=60,at=at)]))
        b=self.batch([self.event(1)]);b['app_version']='3.7.4';self.post(b)
        d=self.data();self.assertEqual(d['daily'][0]['seconds'],30);self.assertEqual(d['daily'][1]['seconds'],30)
        self.assertEqual(d['totals']['new'],1)
    def test_paths_across_batches_and_retention(self):
        yesterday=self.now-86400000
        self.post(self.batch([self.event(0,'screen_view','home',at=yesterday)]))
        self.post(self.batch([self.event(1,'screen_view','inputs',at=yesterday+1000)]))
        self.session=str(uuid.uuid4());self.post(self.batch([self.event()]))
        d=self.data();self.assertEqual(d['paths'][0],('screen_view:home','screen_view:inputs',1))
        cohort=next(c for c in d['cohorts'] if c[0]=='2026-11-02')
        # Mock report clock so the D1 target is a completed day.
        with patch.object(analytics_report,'datetime') as clock:
            clock.now.return_value=datetime(2026,11,4,tzinfo=timezone.utc)
            clock.fromisoformat=datetime.fromisoformat;clock.combine=datetime.combine;clock.min=datetime.min
            cohort=next(c for c in self.data()['cohorts'] if c[0]=='2026-11-02')
            self.assertEqual(cohort[2],(1,1));self.assertIsNone(cohort[3])
    def test_retention_prunes_raw_paths_but_keeps_daily_counts(self):
        self.post(self.batch())
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:
            analytics.prune(db,self.now+31*86400000)
            self.assertEqual(db.execute('SELECT count(*) FROM events').fetchone()[0],0)
            self.assertEqual(db.execute('SELECT sum(events) FROM activity').fetchone()[0],1)

    def test_out_of_order_session_start_moves_daily_session_count(self):
        yesterday=self.now-86400000
        self.post(self.batch([self.event(1,at=self.now)]))
        self.post(self.batch([self.event(0,at=yesterday)]))
        data=self.data()
        self.assertEqual(data['daily'][1]['sessions'],1)
        self.assertEqual(data['daily'][2]['sessions'],0)
        self.assertEqual(data['totals']['sessions'],1)

    def test_one_install_across_versions_and_new_session_on_another_day(self):
        self.post(self.batch())
        b=self.batch([self.event(1)]);b['app_version']='3.7.4';self.post(b)
        self.assertEqual(self.data()['totals']['active'],1)
        self.assertEqual(len(self.data()['versions']),2)

    def test_mature_d1_d7_d30_cohorts_use_exact_day_and_correct_denominator(self):
        start=int(datetime(2026,10,1,12,tzinfo=timezone.utc).timestamp()*1000)
        installs=[str(uuid.uuid4()) for _ in range(3)]
        visits=[(0,i) for i in range(3)]+[(1,0),(1,1),(7,0),(30,0)]
        for offset,index in visits:
            self.now=start+offset*86400000
            analytics.time.time.return_value=self.now/1000
            self.install=installs[index];self.session=str(uuid.uuid4())
            self.assertEqual(self.post(self.batch()),'204 No Content')
        result=analytics_report.collect(self.state,date(2026,10,1),date(2026,11,3))
        self.assertEqual(result['cohorts'][0],['2026-10-01',3,(2,3),(1,3),(1,3)])
        self.assertEqual(result['totals']['active'],3)
        self.assertEqual(result['totals']['wau'],1)
        self.assertEqual(result['totals']['mau'],1)

    def mac_batch(self, mac, traffic='production', app='gentv_tv'):
        b=self.batch(traffic=traffic)
        b.update(schema_version=3,mac_address=mac,app=app)
        return b

    def test_mac_reinstalls_merge_but_preserve_events_sessions_and_duration(self):
        for mac in ('00:1A:2B:3C:4D:5E','00-1a-2b-3c-4d-5e'):
            self.install=str(uuid.uuid4());self.session=str(uuid.uuid4())
            b=self.mac_batch(mac)
            b['events']=[self.event(),self.event(1,'heartbeat',seconds=60)]
            self.assertEqual(self.post(b),'204 No Content')
            self.assertEqual(self.post(b),'204 No Content')
        totals=self.data()['totals']
        self.assertEqual(totals['new'],1);self.assertEqual(totals['active'],1)
        self.assertEqual(totals['sessions'],2);self.assertEqual(totals['seconds'],120)
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:
            dump=' '.join(db.iterdump()).lower()
            self.assertNotIn('00:1a:2b:3c:4d:5e',dump)
            self.assertNotIn('001a2b3c4d5e',dump)
            self.assertNotIn(self.install,dump)

    def test_unavailable_or_random_macs_fall_back_to_installation(self):
        for mac in ('','02:00:00:00:00:00','00:00:00:00:00:00','ff:ff:ff:ff:ff:ff','DA:11:22:33:44:55','invalid'):
            self.install=str(uuid.uuid4());self.session=str(uuid.uuid4())
            b=self.mac_batch(mac)
            self.assertEqual(self.post(b),'204 No Content')
            self.assertEqual(self.post(b),'204 No Content')
        self.assertEqual(self.data()['totals']['active'],6)
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:
            self.assertEqual(db.execute('SELECT count(*) FROM device_aliases').fetchone()[0],0)

    def test_later_mac_resolves_old_history_and_empty_mac_retains_identity(self):
        self.post(self.batch([self.event(at=self.now-86400000)]))
        self.session=str(uuid.uuid4());self.post(self.mac_batch('00:1a:2b:3c:4d:5e'))
        self.install=str(uuid.uuid4());self.session=str(uuid.uuid4())
        self.post(self.mac_batch('00:1a:2b:3c:4d:5e'))
        self.session=str(uuid.uuid4());self.post(self.mac_batch(''))
        d=self.data()
        self.assertEqual(d['totals']['new'],1);self.assertEqual(d['totals']['active'],1)
        self.assertEqual(d['cohorts'][0][0],'2026-11-02')
        self.assertEqual(d['totals']['sessions'],4)

    def test_mac_does_not_merge_apps_traffic_or_different_addresses(self):
        for app,traffic,mac in [('gentv_tv','production','00:1a:2b:3c:4d:5e'),
                                 ('gentv_tv','test','00:1a:2b:3c:4d:5e'),
                                 ('gentv_mobile','production','00:1a:2b:3c:4d:5e'),
                                 ('gentv_tv','production','00:1a:2b:3c:4d:60')]:
            self.install=str(uuid.uuid4());self.session=str(uuid.uuid4())
            self.assertEqual(self.post(self.mac_batch(mac,traffic,app)),'204 No Content')
        self.assertEqual(self.data()['totals']['active'],2)
        self.assertEqual(self.data('test')['totals']['active'],1)

    def test_invalid_schema3_is_atomic_and_old_database_remains_readable(self):
        b=self.mac_batch(None)
        self.assertEqual(self.post(b),'400 Bad Request')
        self.assertFalse((self.state/'analytics.sqlite3').exists())
        self.post(self.batch())
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:
            db.execute('DROP TABLE device_aliases');db.execute('DROP TABLE device_secret')
        self.assertEqual(self.data()['totals']['active'],1)
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:
            analytics.prune(db,self.now)

    def test_desktop_uses_separate_app_and_fixed_anonymous_question_records(self):
        batch=self.batch([self.event(),self.event(1,'screen_view','chat'),self.event(2,'question','network:answered')],traffic='test')
        batch.update(app='gentv_agent',schema_version=4,app_version='0.1.0')
        del batch['android_sdk']
        self.assertEqual(self.post(batch),'204 No Content')
        self.assertEqual(self.post(batch),'204 No Content')
        bad=dict(batch,events=[self.event(3,'question','raw user text')])
        self.assertEqual(self.post(bad),'400 Bad Request')
        self.assertEqual(self.post(self.batch([self.event(4,'question','network:answered')])),'400 Bad Request')
        args=(self.state,date(2026,11,1),date(2026,11,3))
        with patch.object(analytics_report,'datetime',wraps=datetime) as clock:
            clock.now.return_value=datetime(2026,11,3,12,tzinfo=timezone.utc)
            result=analytics_report.collect(*args,app='gentv_agent',traffic='test')
        self.assertEqual(result['totals']['new'],1)
        self.assertEqual(result['totals']['active'],1)
        self.assertIn(('screen_view:chat','question:network:answered',1),result['paths'])
        self.assertEqual(result['questions'],[(self.now,'network:answered','0.1.0')])
        self.assertFalse(analytics_report.collect(*args,app='gentv_tv',traffic='test')['observed'])
        self.assertFalse(analytics_report.collect(*args,app='gentv_agent',traffic='production')['observed'])

    def test_provenance_and_dedup_are_independent_for_each_app_and_traffic(self):
        launcher=self.batch()
        event=launcher['events'][0]
        agent=dict(launcher,app='gentv_agent',schema_version=4)
        del agent['android_sdk']
        self.assertEqual(self.post(agent,source=None),'204 No Content')
        self.assertEqual(self.post(agent,source='gentv_tv'),'401 Unauthorized')
        self.assertEqual(self.post(dict(agent,traffic='test')),'204 No Content')
        self.assertEqual(self.post(agent),'204 No Content')
        self.assertEqual(self.post(agent),'204 No Content')
        self.assertEqual(self.post(launcher),'204 No Content')
        with sqlite3.connect(self.state/'analytics.sqlite3') as db:
            rows=db.execute('SELECT app,traffic,id FROM events ORDER BY app,traffic').fetchall()
            self.assertEqual(rows,[('gentv_agent',traffic,event['event_id']) for traffic in ('production','test','unverified')]+[('gentv_tv','production',event['event_id'])])

    def test_migrates_legacy_production_and_preserves_test_and_new_verified_rows(self):
        path=self.state/'analytics.sqlite3'
        with sqlite3.connect(path) as db:
            db.executescript(analytics.SCHEMA.replace('PRIMARY KEY(app,traffic,id)','PRIMARY KEY(app,id)'))
            install='old-install'
            for index,traffic in enumerate(('production','test')):
                db.execute('INSERT INTO installs VALUES(?,?,?,?,?)',('gentv_tv',traffic,install,'2026-11-03','2026-11-03'))
                db.execute('INSERT INTO activity VALUES(?,?,?,?,?,?,?,?)',('2026-11-03','gentv_tv',traffic,install,'3.7.3',10,1,1))
                db.execute('INSERT INTO events VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',('gentv_tv',str(index),traffic,install,'old-session',self.now,self.now,0,'3.7.3','app_open','',0))
        self.assertEqual(self.data('production')['totals']['active'],0)
        self.assertEqual(self.data('unverified')['totals']['active'],1)
        self.assertEqual(self.post(self.batch()),'204 No Content')
        for traffic in ('production','test','unverified'):
            self.assertEqual(self.data(traffic)['totals']['active'],1)
        with sqlite3.connect(path) as db:
            self.assertTrue(analytics.has_verified_traffic(db))
            self.assertEqual(db.execute('SELECT traffic FROM events WHERE id=?',('0',)).fetchone(),('unverified',))
