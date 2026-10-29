"""Local operational signal; failures are exposed through systemd and the journal."""
import json,os,sqlite3,time,urllib.request
from pathlib import Path
state=Path(os.environ.get('GENTV_STATE_DIR','/var/lib/gentv-tv'))
try:
    with urllib.request.urlopen('http://127.0.0.1:18765/tv/wallpaper',timeout=10) as response:
        if not json.load(response)['wallpapers']:raise RuntimeError('Empty wallpaper feed')
    backups=Path('/var/backups/gentv-tv')
    if not any(p.is_dir() and time.time()-p.stat().st_mtime<172800 for p in backups.iterdir()):raise RuntimeError('Verified backup is stale')
    for database in state.glob('*.sqlite3'):
        with sqlite3.connect(f'file:{database}?mode=ro',uri=True,timeout=5) as db:
            if db.execute('PRAGMA quick_check').fetchone()[0]!='ok':raise RuntimeError('Database check failed')
    status={'ok':True,'checkedAt':int(time.time())}
except Exception:
    status={'ok':False,'checkedAt':int(time.time())}
    raise
finally:
    target=state/'health.json.next';target.write_text(json.dumps(status));target.replace(state/'health.json')
