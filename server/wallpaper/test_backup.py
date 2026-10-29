import os,sqlite3,subprocess,sys,tempfile,unittest
from pathlib import Path
class BackupTest(unittest.TestCase):
    def test_live_database_snapshot_and_restore(self):
        with tempfile.TemporaryDirectory() as folder:
            state=Path(folder)/'state';state.mkdir();backup=Path(folder)/'backup'
            with sqlite3.connect(state/'stats.sqlite3') as db:
                db.execute('PRAGMA journal_mode=WAL');db.execute('CREATE TABLE test(value TEXT)');db.execute("INSERT INTO test VALUES('preserved')");db.commit()
                subprocess.run([sys.executable,str(Path(__file__).with_name('backup.py'))],env=dict(os.environ,GENTV_STATE_DIR=str(state),GENTV_BACKUP_DIR=str(backup)),check=True,capture_output=True)
            snapshot=next(backup.glob('*/*.sqlite3'))
            with sqlite3.connect(snapshot) as restored:self.assertEqual(restored.execute('SELECT value FROM test').fetchone()[0],'preserved')
            self.assertEqual(snapshot.stat().st_mode & 0o077,0)
