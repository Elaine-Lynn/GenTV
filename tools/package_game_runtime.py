#!/usr/bin/env python3
"""Verify and package one signed, uninstalled game plugin APK per ABI.

Build :app:assembleRelease :game:assembleRelease with the same signing key,
then publish the two generated APKs and their manifests. Use --allow-debug
only for an isolated local QA build; never publish debug-signed artifacts.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def run(*args):
    return subprocess.check_output([str(part) for part in args], text=True)


def identity(aapt, apk):
    line = run(aapt, 'dump', 'badging', apk).splitlines()[0]
    values = dict(re.findall(r"(?:^|\s)(name|versionCode|versionName|split)='([^']+)'", line))
    if 'split' in values:
        raise ValueError(f'Not a standalone APK: {apk}')
    return values


def signer(apksigner, apk, allow_debug):
    output = run(apksigner, 'verify', '--print-certs', apk)
    digest = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})', output)
    if not digest or not allow_debug and 'Android Debug' in output:
        raise ValueError(f'Unsigned or debug-signed APK: {apk}')
    return digest[1]


def validate_contents(base_apk, game_apk, abi):
    with zipfile.ZipFile(base_apk) as base, zipfile.ZipFile(game_apk) as game:
        base_names, game_names = set(base.namelist()), set(game.namelist())
        if any(name == 'assets/omni.ja' or name.startswith('assets/game_')
               or name.endswith('/libxul.so') for name in base_names):
            raise ValueError('Launcher APK still contains game resources or Gecko libraries')
        required = {'assets/omni.ja', 'assets/game_base_catalog.json',
                    'assets/game_base_input/manifest.json', 'assets/game_base_input/game_base_controls.js'}
        if not required.issubset(game_names) or not any(re.fullmatch(r'classes\d*\.dex', n) for n in game_names):
            raise ValueError(f'Game code or assets missing: {game_apk}')
        if f'lib/{abi}/libxul.so' not in game_names:
            raise ValueError(f'Gecko library missing for {abi}')
        if any(n.startswith('lib/') and not n.startswith(f'lib/{abi}/') for n in game_names):
            raise ValueError(f'Other ABI libraries present: {game_apk}')


def package(base_apk, game_dir, output, sdk, allow_debug, min_host=40, max_host=2147483647):
    if min_host <= 0 or max_host < min_host: raise ValueError("Invalid host compatibility range")
    tools = sdk / 'build-tools' / '34.0.0'
    aapt = tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
    apksigner = tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    if not aapt.is_file() or not apksigner.is_file():
        raise ValueError('Android SDK build-tools 34.0.0 not found')
    host = identity(aapt, base_apk)
    if host.get('name') not in ('com.gentv.tv', 'com.gentv.tv.moduleqa'):
        raise ValueError('Unexpected launcher application ID')
    certificate = signer(apksigner, base_apk, allow_debug)
    variant = 'debug' if allow_debug else 'release'
    for abi in ('armeabi-v7a', 'arm64-v8a'):
        source = game_dir / f'game-{abi}-{variant}.apk'
        plugin = identity(aapt, source)
        if plugin.get('name') != 'com.gentv.game.plugin' or int(plugin.get('versionCode', '0')) <= 0:
            raise ValueError(f'Wrong game plugin identity or version: {source}')
        if signer(apksigner, source, allow_debug) != certificate:
            raise ValueError(f'Game plugin signer differs from launcher: {source}')
        validate_contents(base_apk, source, abi)
        folder = output / plugin['versionCode'] / abi
        folder.mkdir(parents=True, exist_ok=True)
        name = f"game-{abi.replace('-', '_')}.apk"
        target = folder / name
        if target.exists() and target.read_bytes() != source.read_bytes():
            raise ValueError(f'Refusing to overwrite different plugin: {target}')
        if not target.exists():
            shutil.copyfile(source, target)
        data = target.read_bytes()
        manifest = {
            'versionCode': int(plugin['versionCode']), 'versionName': plugin['versionName'],
            'hostApiVersion': 2, 'minHostVersionCode': min_host, 'maxHostVersionCode': max_host,
            'hostPackageName': host['name'], 'packageName': plugin['name'],
            'signingCertificateSha256': certificate, 'abi': abi,
            'plugin': {'name': name,
                       'url': f"https://github.com/Elaine-Lynn/GenTV/releases/download/game-runtime-v{plugin['versionName']}/{name}",
                       'sha256': hashlib.sha256(data).hexdigest(), 'sizeBytes': len(data)},
        }
        manifest_file = folder / 'manifest.json'
        encoded = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        if manifest_file.exists() and manifest_file.read_bytes() != encoded:
            raise ValueError(f'Refusing to overwrite different manifest: {manifest_file}')
        manifest_file.write_bytes(encoded)
        latest = output / 'latest' / abi
        latest.mkdir(parents=True, exist_ok=True)
        (latest / 'manifest.json').write_bytes(encoded)
        server = output / 'server'
        server.mkdir(parents=True, exist_ok=True)
        (server / f'update-{abi}.json').write_bytes(encoded)
        print(f'{abi}: {len(data):,} bytes -> {target}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-apk', type=Path, required=True)
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=Path('work/game-runtime-release'))
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '')))
    parser.add_argument('--min-host', type=int, default=40)
    parser.add_argument('--max-host', type=int, default=2147483647)
    parser.add_argument('--allow-debug', action='store_true')
    args = parser.parse_args()
    package(args.base_apk, args.game_dir, args.output, args.sdk, args.allow_debug, args.min_host, args.max_host)
