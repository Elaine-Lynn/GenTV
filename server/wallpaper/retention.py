"""Daily deletion of expired mobile raw events and feedback; aggregates remain."""
import os,sqlite3,time
from pathlib import Path
state=Path(os.environ.get('GENTV_STATE_DIR','/var/lib/gentv-tv'))
for name,table,column,cutoff in [('mobile.sqlite3','events','received',int(time.time()*1000)-30*86400000),('feedback.sqlite3','feedback','received',int(time.time())-30*86400)]:
    path=state/name
    if path.exists():
        with sqlite3.connect(path,timeout=30) as db:
            if db.execute('SELECT name FROM sqlite_master WHERE type="table" AND name=?',(table,)).fetchone():
                db.execute(f'DELETE FROM {table} WHERE {column} < ?',(cutoff,))
