#!/usr/bin/env python3
"""Verify and publish an immutable TV release using the existing SSH authentication.
Usage: ANDROID_HOME=... python3 tools/publish_tv_release.py --host admin@host [--ssh-option ...]
No private key/password is copied. Published policy remains optional unless explicitly changed server-side.
"""
import argparse,hashlib,json,os,re,shlex,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--host',required=True);p.add_argument('--ssh-option',action='append',default=[])
a=p.parse_args();root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ['ANDROID_HOME']);apk=root/'launcher/app/build/outputs/apk/release/app-release.apk'
# Match the repository's 1 MB (decimal) Release APK budget.
size = apk.stat().st_size
print(f'TV release APK size: {size:,} / 1,000,000 bytes')
assert size <= 1_000_000, f'APK exceeds the 1 MB size budget: {size:,} bytes'
certs=subprocess.check_output([str(sdk/'build-tools/34.0.0/apksigner'),'verify','--print-certs',str(apk)],text=True)
assert 'Android Debug' not in certs,'Refusing debug-signed release'
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',certs)[1]
expected=subprocess.check_output([str(root/'launcher/gradlew'),'-p',str(root/'launcher'),'--offline','--no-daemon','-q',':app:releaseCertificateSha256'],text=True)
assert 'GENTV_RELEASE_CERT='+cert in expected,'Unexpected release certificate'
badging=subprocess.check_output([str(sdk/'build-tools/34.0.0/aapt'),'dump','badging',str(apk)],text=True)
identity=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",badging)
assert identity and identity[1]=='com.gentv.tv'
code,version=int(identity[2]),identity[3];assert re.fullmatch(r'[0-9.]+',version)
name='gentv-tv-'+version+'.apk';sha=hashlib.sha256(apk.read_bytes()).hexdigest()
metadata=dict(versionCode=code,versionName=version,packageName='com.gentv.tv',url='https://api.gentv.app/tv/releases/'+name,sha256=sha,signingCertificateSha256=cert,minSdk=26,sizeBytes=apk.stat().st_size,forceUpdate=False,minSupportedVersionCode=0)
options=['-o','BatchMode=yes','-o','ConnectTimeout=15']
for option in a.ssh_option:options+=['-o',option]
subprocess.run(['scp',*options,str(apk),a.host+':/var/tmp/'+name],check=True)
script='''set -eu
src=/var/tmp/NAME
dst=/var/lib/gentv-tv/releases/NAME
printf '%s  %s\\n' SHA "$src" | sha256sum -c -
if sudo -n test -f "$dst"; then
  printf '%s  %s\\n' SHA "$dst" | sudo -n sha256sum -c -
else sudo -n install -m 644 "$src" "$dst"; fi
if ! sudo -n test -e /var/lib/gentv-tv/update-legacy.json; then sudo -n cp /var/lib/gentv-tv/update.json /var/lib/gentv-tv/update-legacy.json; fi
printf %s MANIFEST | sudo -n tee /var/lib/gentv-tv/update.json.next >/dev/null
sudo -n mv /var/lib/gentv-tv/update.json.next /var/lib/gentv-tv/update.json
'''.replace('NAME',name).replace('SHA',shlex.quote(sha)).replace('MANIFEST',shlex.quote(json.dumps(metadata,separators=(',',':'))))
subprocess.run(['ssh',*options,a.host,'sh -s'],input=script,text=True,check=True)
print(json.dumps(metadata,indent=2))
