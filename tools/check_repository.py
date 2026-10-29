#!/usr/bin/env python3
"""Verify GenTV source integrity, delivery boundaries and protected components."""
import hashlib
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
lock = json.loads((root/'phone/framework-lock.json').read_text())
for name, expected in lock['sha256'].items():
    path = root/'phone/framework'/name
    assert path.is_file(), f'Missing framework source: {name}'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, f'Changed framework source: {name}'
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root/'launcher/app/src/main/AndroidManifest.xml').getroot()
assert 'android.permission.INTERNET' in {n.get(ns+'name') for n in manifest.findall('uses-permission')}
assert 'BuildConfig.API_BASE_URL' in (root/'launcher/app/src/main/java/com/gentv/tv/TvCloud.java').read_text()
# The GenTV API is build configuration: forks without one never contact the official service.
for source in ('launcher/app/src/main', 'phone/app/src/main', 'phone/framework'):
    for path in (root/source).rglob('*'):
        if path.suffix in {'.java', '.kt', '.kts', '.xml'}:
            assert 'api.gentv.app' not in path.read_text(), f'Hard-coded GenTV API: {path.relative_to(root)}'
for name in ('launcher/app/build.gradle', 'phone/app/build.gradle.kts'):
    assert 'api.gentv.app' not in (root/name).read_text(), f'Hard-coded GenTV API: {name}'
receiver = manifest.find("application/receiver[@"+ns+"name='.DesktopReceiver']")
assert receiver is not None and receiver.get(ns+'permission') == 'android.permission.WRITE_SECURE_SETTINGS'
assert re.search(r'\bminSdk\s+26\b', (root/'launcher/app/build.gradle').read_text())
assert "applicationId 'com.gentv.tv'" in (root/'launcher/app/build.gradle').read_text()
assert 'applicationId = "com.gentv.mobile"' in (root/'phone/app/build.gradle.kts').read_text()
assert 'com.gentv.tv.category.DOCK_CARD' in (root/'launcher/app/src/main/AndroidManifest.xml').read_text()
assert '<string name="app_name">GenTV</string>' in (root/'launcher/app/src/main/res/values/strings.xml').read_text()
assert 'com.gentv.core' in (root/'phone/framework/core/src/main/kotlin/com/gentv/core/lists/Protected.kt').read_text()
for name in ('phone/settings.gradle.kts','phone/app/build.gradle.kts'):
    assert 'TVcleaner' not in (root/name).read_text()
blocked = {'.apk','.aab','.jks','.keystore','.pem','.class','.dex'}
skip = {'.git','.gradle','.kotlin','build','out','work','shots','keys','.idea','__pycache__'}
for directory, names, files in os.walk(root):
    names[:] = [n for n in names if n not in skip]
    for name in files:
        path = Path(directory)/name
        assert path.suffix not in blocked, f'Generated/private file outside ignored output: {path.relative_to(root)}'
apk = root/'launcher/app/build/outputs/apk/release/app-release.apk'
if apk.exists():
    # Keep visual assets within the 1 MB (decimal) Release APK budget.
    size = apk.stat().st_size
    print(f'TV release APK size: {size:,} / 1,000,000 bytes')
    assert size <= 1_000_000, f'Launcher exceeds the 1 MB size budget: {size:,} bytes'
bundle = root/'phone/app/build/generated/homes/launcher/build-manifest.json'
if bundle.exists():
    delivery = json.loads(bundle.read_text())
    assert set(delivery['sha256']) == {'gentv-home.apk'}, 'Only the GenTV TV APK may be bundled'
    for name, digest in delivery['sha256'].items():
        assert hashlib.sha256((bundle.parent/name).read_bytes()).hexdigest() == digest
print(f'PASS: {len(lock["sha256"])} framework sources; stable application IDs, restricted receiver, API level, single-TV delivery and source-tree boundaries')
