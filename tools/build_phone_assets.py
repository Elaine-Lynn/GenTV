#!/usr/bin/env python3
"""Build and verify the single GenTV TV asset. Never connects to a television."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', required=True)
p.add_argument('--offline', action='store_true')
p.add_argument('--home-java', default=os.environ.get('GENTV_HOME_JAVA_HOME') or os.environ.get('JAVA_HOME'))
p.add_argument('--debug-signing', action='store_true', help='Contributor builds without the private release key: embed a debug-signed TV preview.')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
output = Path(a.output).resolve() / 'launcher'
if not output.is_relative_to(root / 'phone/app/build'):
    raise SystemExit('Generated assets must stay under phone/app/build/.')
env = os.environ.copy()
if not a.home_java:
    raise SystemExit('Set GENTV_HOME_JAVA_HOME to JDK 17.')
version = subprocess.run([str(Path(a.home_java) / 'bin/java'), '-version'], capture_output=True, text=True, check=True)
if 'version "17.' not in version.stderr + version.stdout:
    raise SystemExit('The TV source build requires JDK 17.')
env['JAVA_HOME'] = str(Path(a.home_java).resolve())
common = ['--no-daemon'] + (['--offline'] if a.offline else [])
project = root / 'launcher'
sdk = Path(env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT') or '')
if a.debug_signing:
    subprocess.run([str(project/'gradlew'), '-p', str(project), *common, ':app:assembleDebug'], env=env, check=True)
    apk = project / 'app/build/outputs/apk/debug/app-debug.apk'
else:
    subprocess.run([str(project/'gradlew'), '-p', str(project), *common, ':app:assembleRelease'], env=env, check=True)
    apk = project / 'app/build/outputs/apk/release/app-release.apk'
    certificate_output = subprocess.check_output([str(project/'gradlew'), '-p', str(project), *common, '-q', ':app:releaseCertificateSha256'], env=env, text=True)
    expected = re.search(r'GENTV_RELEASE_CERT=([0-9a-f]{64})', certificate_output)[1]
    signed = subprocess.check_output([str(sdk/'build-tools/34.0.0/apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    actual = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', signed)[1]
    if actual != expected:
        raise RuntimeError('The GenTV TV signing certificate does not match the configured release certificate.')
badging = subprocess.check_output([str(sdk/'build-tools/34.0.0/aapt'), 'dump', 'badging', str(apk)], text=True)
if "package: name='com.gentv.tv'" not in badging:
    raise RuntimeError('Unexpected TV application identity.')
# Drop obsolete generated APKs so an incremental build cannot repackage them.
if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True)
filename = 'gentv-home.apk'
shutil.copyfile(apk, output/filename)
manifest = {'sourceBuilt': True, 'signing': 'debug' if a.debug_signing else 'release', 'sha256': {filename: hashlib.sha256(apk.read_bytes()).hexdigest()}}
(output/'build-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
print('Built and verified the GenTV TV asset; no device operations performed.')
