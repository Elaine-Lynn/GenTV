"""Private SQLite snapshots, verified by restoring into temporary databases. No row contents logged."""
import datetime
import json
import os
from pathlib import Path
import shutil
import sqlite3
import tempfile
os.umask(0o077)
state=Path(os.environ.get('GENTV_STATE_DIR','/var/lib/gentv-tv'))
root=Path(os.environ.get('GENTV_BACKUP_DIR','/var/backups/gentv-tv'))
root.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
stage=root/('.'+stamp);stage.mkdir()
try:
    count=0
    for source in state.glob('*.sqlite3'):
        target=stage/source.name
        with sqlite3.connect(f'file:{source}?mode=ro',uri=True) as src, sqlite3.connect(target) as dst:src.backup(dst)
        with tempfile.TemporaryDirectory() as tmp:
            restored=Path(tmp)/source.name
            shutil.copyfile(target,restored)
            with sqlite3.connect(restored) as check:
                if check.execute('PRAGMA integrity_check').fetchone()[0]!='ok':raise RuntimeError('Restore verification failed')
        count+=1
    for source in state.glob('update*.json'):
        json.loads(source.read_text());shutil.copyfile(source,stage/source.name)
    stage.rename(root/stamp)
    for old in sorted(p for p in root.iterdir() if p.is_dir() and not p.name.startswith('.'))[:-14]:shutil.rmtree(old)
    print(f'Backup and restore verification passed: {count} databases')
except BaseException:
    shutil.rmtree(stage,ignore_errors=True);raise
