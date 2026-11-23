#!/usr/bin/env python3
"""Extract signed game splits from a TV .apks archive for static HTTPS hosting.

Example:
  python tools/package_game_modules.py \
      --base-apk launcher/app/build/outputs/apk/release/app-release.apk \
      --apks launcher/app/build/intermediates/apks_from_bundle/release/makeApkFromBundleForRelease/bundle.apks

Upload the split APKs as assets of the matching GitHub game-modules release.
Publish the generated manifests to this repository's update-manifest branch.
The base APK must be built from the same source revision and signing key.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def run(*args):
    return subprocess.check_output([str(part) for part in args], text=True)


def identity(aapt, apk, expected_split):
    output = run(aapt, 'dump', 'badging', apk)
    line = output.splitlines()[0]
    package = re.search(r"name='([^']+)'", line)
    version = re.search(r"versionCode='(\d+)'", line)
    split = re.search(r"split='([^']+)'", line)
    if not package or not version or package[1] != 'com.gentv.tv' or (split[1] if split else None) != expected_split:
        raise ValueError(f'Unexpected package or split: {apk.name}')
    return int(version[1])


def signer(apksigner, apk):
    output = run(apksigner, 'verify', '--print-certs', apk)
    digest = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', output)
    if not digest or 'Android Debug' in output:
        raise ValueError(f'Unsigned or debug-signed split: {apk.name}')
    return digest[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apks', type=Path, required=True)
    parser.add_argument('--base-apk', type=Path, required=True,
                        help='The standalone base APK built from the same revision')
    parser.add_argument('--output', type=Path, default=Path('work/game-modules'))
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '')))
    args = parser.parse_args()
    tools = args.sdk / 'build-tools' / '34.0.0'
    aapt = tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
    apksigner = tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    if not aapt.is_file() or not apksigner.is_file():
        parser.error('Android SDK build-tools 34.0.0 not found; set ANDROID_HOME or --sdk')
    with zipfile.ZipFile(args.apks) as archive:
        chosen = {
            'base-master.apk': 'splits/base-master.apk',
            'game-master.apk': 'splits/game-master.apk',
            'game-armeabi_v7a.apk': 'splits/game-armeabi_v7a.apk',
            'game-arm64_v8a.apk': 'splits/game-arm64_v8a.apk',
        }
        entries = {item.filename: item for item in archive.infolist()}
        if any(name not in entries for name in chosen.values()):
            raise ValueError('The APK set lacks a required base or game split')
        scratch = args.output / '.verified'
        scratch.mkdir(parents=True, exist_ok=True)
        extracted = {}
        for name, source in chosen.items():
            file = scratch / name
            with archive.open(source) as incoming, file.open('wb') as outgoing:
                while chunk := incoming.read(1024 * 1024):
                    outgoing.write(chunk)
            extracted[name] = file
    code = identity(aapt, extracted['base-master.apk'], None)
    version_name = re.search(r"versionName='([^']+)'", run(aapt, 'dump', 'badging', args.base_apk).splitlines()[0])
    if not version_name or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version_name[1]):
        raise ValueError('A numeric release versionName is required')
    version_name = version_name[1]
    release_url = 'https://github.com/Elaine-Lynn/GenTV/releases/download/game-modules-v' + version_name + '/'
    certificate = signer(apksigner, extracted['base-master.apk'])
    if identity(aapt, args.base_apk, None) != code or signer(apksigner, args.base_apk) != certificate:
        raise ValueError('Standalone base APK version or signer differs from the bundle')
    with zipfile.ZipFile(args.base_apk) as base, zipfile.ZipFile(extracted['game-master.apk']) as game:
        if any('libxul.so' in item.filename for item in base.infolist()):
            raise ValueError('Standalone base APK still contains GeckoView native libraries')
        # GeckoView resolves omni.ja from the base APK even when its code lives in a feature.
        if 'assets/omni.ja' not in base.namelist() or base.read('assets/omni.ja') != game.read('assets/omni.ja'):
            raise ValueError('Base APK must carry the exact GeckoView omni.ja from the game split')
    for name, expected in [('game-master.apk', 'game'),
                           ('game-armeabi_v7a.apk', 'game.config.armeabi_v7a'),
                           ('game-arm64_v8a.apk', 'game.config.arm64_v8a')]:
        if identity(aapt, extracted[name], expected) != code or signer(apksigner, extracted[name]) != certificate:
            raise ValueError(f'Version or signing certificate mismatch: {name}')
    for abi in ('armeabi-v7a', 'arm64-v8a'):
        folder = args.output / str(code) / abi
        folder.mkdir(parents=True, exist_ok=True)
        names = ['game-master.apk', 'game-' + abi.replace('-', '_') + '.apk']
        manifest = {'versionCode': code, 'versionName': version_name, 'packageName': 'com.gentv.tv',
                    'signingCertificateSha256': certificate, 'abi': abi, 'splits': []}
        for name in names:
            source = extracted[name]
            target = folder / name
            content = source.read_bytes()
            if target.exists() and target.read_bytes() != content:
                raise ValueError(f'Refusing to replace different published content: {target}')
            if not target.exists(): target.write_bytes(content)
            manifest['splits'].append({'name': name,
                                      'url': release_url + name,
                                      'sha256': hashlib.sha256(content).hexdigest(),
                                      'sizeBytes': len(content)})
        target = folder / 'manifest.json'
        encoded = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        if target.exists() and target.read_bytes() != encoded:
            raise ValueError(f'Refusing to replace different published manifest: {target}')
        target.write_bytes(encoded)
        print(f'{abi}: {sum(item["sizeBytes"] for item in manifest["splits"]):,} bytes -> {folder}')


if __name__ == '__main__':
    main()
