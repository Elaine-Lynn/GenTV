#!/usr/bin/env python3
"""Verify and publish an immutable TV release using the existing SSH authentication.
Usage: ANDROID_HOME=... GENTV_API_BASE_URL=https://your-api/tv/ python3 tools/publish_tv_release.py --host admin@host
No private key/password is copied. Published policy remains optional unless explicitly changed server-side.
"""
import argparse,hashlib,json,os,re,shlex,subprocess,shutil,sys
from tv_update_backup import prepare, publish
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--host');p.add_argument('--ssh-option',action='append',default=[])
p.add_argument('--api-base-url', default=os.environ.get('GENTV_API_BASE_URL', ''))
p.add_argument('--prepare-only', action='store_true', help='Generate GitHub APK/config files without publishing')
p.add_argument('--github-only', action='store_true', help='Publish GitHub backup without changing the primary server')
p.add_argument('--server-only', action='store_true', help='Explicit recovery option: publish primary server only')
a=p.parse_args();root=Path(__file__).resolve().parents[1]
if not a.prepare_only and not a.github_only and not a.host:p.error('--host is required for primary publication')
if not a.prepare_only and not a.server_only and not shutil.which('gh'):p.error('GitHub CLI authentication is required; use --prepare-only to generate files')
if a.api_base_url and not re.fullmatch(r'https://[A-Za-z0-9.-]+(?::[0-9]+)?/(?:[A-Za-z0-9._~-]+/)*',a.api_base_url):p.error('API base must be HTTPS and end in /')
if not a.prepare_only and not a.github_only and not a.api_base_url:p.error('Configure --api-base-url or GENTV_API_BASE_URL')
if not a.prepare_only and subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True).strip():
    p.error('Commit release source changes before publishing; private keys and build output must remain ignored')

sdk=Path(os.environ['ANDROID_HOME']);apk=root/'launcher/app/build/outputs/apk/release/app-release.apk'
size = apk.stat().st_size
print(f'TV release APK size: {size:,} bytes')
certs=subprocess.check_output([str(sdk/('build-tools/34.0.0/apksigner.bat' if os.name=='nt' else 'build-tools/34.0.0/apksigner')),'verify','--print-certs',str(apk)],text=True)
assert 'Android Debug' not in certs,'Refusing debug-signed release'
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',certs)[1]
expected=subprocess.check_output([str(root/('launcher/gradlew.bat' if os.name=='nt' else 'launcher/gradlew')),'-p',str(root/'launcher'),'--offline','--no-daemon','-q',':app:releaseCertificateSha256'],text=True)
assert 'GENTV_RELEASE_CERT='+cert in expected,'Unexpected release certificate'
badging=subprocess.check_output([str(sdk/('build-tools/34.0.0/aapt.exe' if os.name=='nt' else 'build-tools/34.0.0/aapt')),'dump','badging',str(apk)],text=True)
identity=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",badging)
assert identity and identity[1]=='com.gentv.tv'
code,version=int(identity[2]),identity[3];assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',version)
min_sdk_match=re.search(r"sdkVersion:'([0-9]+)'",badging)
assert min_sdk_match,'Cannot verify TV APK minimum SDK'
min_sdk=int(min_sdk_match[1])
name='gentv-tv-'+version+'.apk';sha=hashlib.sha256(apk.read_bytes()).hexdigest()
metadata=dict(versionCode=code,versionName=version,packageName='com.gentv.tv',url=a.api_base_url+'releases/'+name,sha256=sha,signingCertificateSha256=cert,minSdk=min_sdk,sizeBytes=apk.stat().st_size,forceUpdate=False,minSupportedVersionCode=0)
backup, assets = prepare(apk, metadata, root/'work'/'tv-release-github'/version,
                         root/'launcher/app/build/outputs/distribution/GenTV-TV-release.zip')
print('Prepared GitHub APK and config.json: '+str(assets[1].parent))
if a.prepare_only:sys.exit(0)
if not a.server_only:publish(backup, assets)
if a.github_only:sys.exit(0)
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
