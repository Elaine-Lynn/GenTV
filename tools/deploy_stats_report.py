#!/usr/bin/env python3
"""Deploy the separate authenticated report; no ingestion or release manifests change.
A new random password is saved only to --credentials-file (mode 600). Existing
credentials are reused by later deploys. Do not commit or print that file.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import sys
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--host',required=True)
    p.add_argument('--ssh-option',action='append',default=[])
    p.add_argument('--credentials-file',type=Path,required=True)
    p.add_argument('--add-user', help='Add a user without changing existing accounts')
    p.add_argument('--password-stdin', action='store_true', help='Read the new password privately from stdin')
    a=p.parse_args()
    if bool(a.add_user) != a.password_stdin:
        p.error('--add-user and --password-stdin must be supplied together')
    additional = {}
    if a.add_user:
        if not a.add_user.isascii() or not a.add_user.replace('_','').replace('-','').isalnum():
            p.error('Username must contain ASCII letters, digits, underscores or hyphens')
        password = sys.stdin.readline().rstrip('\r\n')
        if len(password) < 8:
            p.error('Password must contain at least 8 characters')
        salt = secrets.token_bytes(16)
        digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 600000).hex()
        additional[a.add_user] = 'pbkdf2_sha256$600000$'+salt.hex()+'$'+digest
        del password
    options=['-o','BatchMode=yes','-o','ConnectTimeout=15']
    for option in a.ssh_option:options+=['-o',option]
    a.credentials_file.parent.mkdir(parents=True,exist_ok=True)
    if a.credentials_file.exists():
        credentials=json.loads(a.credentials_file.read_text())
    else:
        credentials={'username':'admin','password':secrets.token_urlsafe(24),'url':'https://api.gentv.app/tv/report/'}
        fd=os.open(a.credentials_file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as f:json.dump(credentials,f,indent=2)
    os.chmod(a.credentials_file,0o600)
    env=('GENTV_REPORT_USER='+credentials['username']+'\nGENTV_REPORT_PASSWORD_SHA256='+hashlib.sha256(credentials['password'].encode()).hexdigest()+'\n').encode()
    with tempfile.TemporaryDirectory() as tmp:
        archive=Path(tmp)/'report.tar.gz'
        with tarfile.open(archive,'w:gz') as tar:
            for name in ['report.py','analytics_report.py','report.html','requirements.txt','deploy/gentv-report.service','deploy/nginx-report.locations.conf']:
                tar.add(ROOT/'server/wallpaper'/name,arcname=name)
        subprocess.run(['scp',*options,str(archive),a.host+':/var/tmp/gentv-report-source.tar.gz'],check=True)
        script=REMOTE.replace('ARCHIVE_SHA',hashlib.sha256(archive.read_bytes()).hexdigest()).replace('ENV_B64',base64.b64encode(env).decode()).replace('USERS_B64',base64.b64encode(json.dumps(additional).encode()).decode())
        subprocess.run(['ssh',*options,a.host,'sudo -n python3 -'],input=script,text=True,check=True)
    print('Credentials saved privately to '+str(a.credentials_file.resolve()))

REMOTE=r'''
import base64,datetime,hashlib,os,pathlib,shutil,subprocess,tarfile,time,urllib.request
base=pathlib.Path('/srv/gentv/report');base.mkdir(parents=True,exist_ok=True)
lock=base/'deploy.lock';lock.mkdir()
backup=None;previous=None;enabled=False;active=False
config_names=['/etc/gentv-report.env','/etc/systemd/system/gentv-report.service','/etc/nginx/snippets/gentv-report.locations.conf','/etc/nginx/sites-available/api.gentv.app']
def run(*args):subprocess.run(args,check=True)
def switch(path):
 link=base/'current.next'
 if link.is_symlink():link.unlink()
 link.symlink_to(path);os.replace(link,base/'current')
try:
 archive=pathlib.Path('/var/tmp/gentv-report-source.tar.gz')
 assert hashlib.sha256(archive.read_bytes()).hexdigest()=='ARCHIVE_SHA'
 stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
 release=base/'releases'/stamp;release.mkdir(parents=True)
 with tarfile.open(archive) as t:
  for m in t.getmembers():
   assert not m.name.startswith('/') and '..' not in pathlib.PurePosixPath(m.name).parts and m.isfile()
  t.extractall(release)
 run('python3','-m','venv',str(release/'venv'))
 run(str(release/'venv/bin/pip'),'install','-r',str(release/'requirements.txt'))
 backup=pathlib.Path('/var/backups/gentv-report')/stamp;backup.mkdir(parents=True,mode=0o700)
 previous=(base/'current').resolve() if (base/'current').exists() else None
 enabled=subprocess.run(['systemctl','is-enabled','--quiet','gentv-report'],check=False).returncode==0
 active=subprocess.run(['systemctl','is-active','--quiet','gentv-report'],check=False).returncode==0
 (backup/'previous.json').write_text(__import__('json').dumps({'previous':str(previous) if previous else None,'enabled':enabled,'active':active}))
 for name in config_names:
  p=pathlib.Path(name)
  if p.exists():
   dst=backup/name.lstrip('/');dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dst)
 # Preserve all existing authentication on every redeploy. Additive accounts
 # are merged only on explicit request; refuse accidental password rotation.
 auth=pathlib.Path('/etc/gentv-report.env')
 content=auth.read_text() if auth.exists() else base64.b64decode('ENV_B64').decode()
 values=dict(line.split('=',1) for line in content.splitlines() if '=' in line and not line.startswith('#'))
 extra=__import__('json').loads(base64.b64decode('USERS_B64'))
 users=__import__('json').loads(values.get('GENTV_REPORT_USERS_JSON','{}'))
 for username,digest in extra.items():
  if username in users or username==values.get('GENTV_REPORT_USER','admin'):
   raise RuntimeError('Requested account already exists; rotation requires a separate explicit action')
  users[username]=digest
 if extra:
  content='\n'.join(line for line in content.splitlines() if not line.startswith('GENTV_REPORT_USERS_JSON='))+'\n'
  content+='GENTV_REPORT_USERS_JSON='+__import__('json').dumps(users,separators=(',',':'))+'\n'
 # Atomic replacement never creates a broadly readable credential file.
 fd=os.open('/etc/gentv-report.env.next',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as f:f.write(content)
 os.replace('/etc/gentv-report.env.next',auth);os.chmod(auth,0o600)
 shutil.copy2(release/'deploy/gentv-report.service','/etc/systemd/system/gentv-report.service')
 shutil.copy2(release/'deploy/nginx-report.locations.conf','/etc/nginx/snippets/gentv-report.locations.conf')
 vhost=pathlib.Path('/etc/nginx/sites-available/api.gentv.app');text=vhost.read_text()
 include='    include /etc/nginx/snippets/gentv-report.locations.conf;'
 if include not in text:
  anchor='    include /etc/nginx/snippets/gentv-tv-api.locations.conf;'
  assert text.count(anchor)==1
  vhost.write_text(text.replace(anchor,anchor+'\n'+include))
 run('nginx','-t')
 switch(release)
 run('systemctl','daemon-reload');run('systemctl','restart','gentv-report')
 for attempt in range(15):
  try:
   urllib.request.urlopen('http://127.0.0.1:18766/tv/report/',timeout=2)
   raise RuntimeError('Report unexpectedly unauthenticated')
  except urllib.error.HTTPError as e:
   if e.code==401:break
   raise
  except (urllib.error.URLError,ConnectionError):
   if attempt==14:raise
   time.sleep(1)
 run('systemctl','reload','nginx');run('systemctl','enable','gentv-report')
 print('report_release='+str(release));print('rollback_snapshot='+str(backup))
except BaseException:
 if backup is not None:
  for name in config_names:
   saved=backup/name.lstrip('/');p=pathlib.Path(name)
   if saved.exists():shutil.copy2(saved,p)
   elif p.exists():p.unlink()
  run('systemctl','stop','gentv-report')
  if previous:switch(previous)
  elif (base/'current').is_symlink():(base/'current').unlink()
  run('systemctl','daemon-reload')
  if enabled:run('systemctl','enable','gentv-report')
  else:subprocess.run(['systemctl','disable','gentv-report'],check=False)
  if active:run('systemctl','start','gentv-report')
  run('nginx','-t');run('systemctl','reload','nginx')
 raise
finally:lock.rmdir()
'''

if __name__=='__main__':main()
