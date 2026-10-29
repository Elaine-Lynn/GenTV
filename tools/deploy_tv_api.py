#!/usr/bin/env python3
import subprocess,hashlib,shlex
from pathlib import Path
import argparse,tempfile
p=argparse.ArgumentParser(description='Deploy GenTV TV API with automatic rollback; uses existing SSH authentication.')
p.add_argument('--host',required=True);p.add_argument('--ssh-option',action='append',default=[])
a=p.parse_args()
remote=['-o','BatchMode=yes','-o','ConnectTimeout=15']
for option in a.ssh_option:remote+=['-o',option]
host=a.host
root=Path(__file__).resolve().parents[1]
output=tempfile.TemporaryDirectory(prefix='gentv-deploy-')
archive=Path(output.name)/'source.tar.gz'
subprocess.run(['tar','-czf',str(archive),'-C',str(root/'server/wallpaper'),'app.py','mobile.py','rate_limit.py','backup.py','health.py','retention.py','requirements.txt','deploy'],check=True)
subprocess.run(['scp',*remote,str(archive),host+':/var/tmp/gentv-tv-api-source.tar.gz'],check=True)
sha=hashlib.sha256(archive.read_bytes()).hexdigest()
script='''set -eu
stage=$(mktemp -d /var/tmp/gentv-deploy.XXXXXX)
trap 'rm -rf "$stage"' EXIT
tar -xzf /var/tmp/gentv-tv-api-source.tar.gz -C "$stage"
sh "$stage/deploy/install-on-server.sh" /var/tmp/gentv-tv-api-source.tar.gz '''+shlex.quote(sha)+'\n'
subprocess.run(['ssh',*remote,host,'sh -s'],input=script,text=True,check=True)
