#!/usr/bin/env python3
"""Package one independently updated, uninstalled game APK for the shared game foundation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import zipfile
from package_game_runtime import identity, signer


def validate_contents(apk, game_id):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if any(n.startswith('lib/') or n.endswith('omni.ja') for n in names):
            raise ValueError('Individual games must reuse the foundation browser')
        assets = [n for n in names if n.startswith('assets/') and not n.endswith('/')]
        if not assets or any(not n.startswith('assets/game_' + game_id + '_') for n in assets):
            raise ValueError('Game assets must use their module prefix')
        if not any(re.fullmatch(r'classes\d*\.dex', n) for n in names):
            raise ValueError('Game entry code is missing')


def package(apk, base_apk, game_id, entry_class, output, sdk, allow_debug=False, min_base=100, max_base=2147483647):
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,39}', game_id) or not entry_class.startswith('com.gentv.game.' + game_id + '.'):
        raise ValueError('Invalid game identity')
    if min_base <= 0 or max_base < min_base: raise ValueError('Invalid foundation compatibility range')
    tools = sdk / 'build-tools' / '34.0.0'
    aapt = tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
    apksigner = tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    info = identity(aapt, apk)
    host = identity(aapt, base_apk)
    certificate = signer(apksigner, apk, allow_debug)
    if host.get('name') not in ('com.gentv.tv', 'com.gentv.tv.moduleqa') or signer(apksigner, base_apk, allow_debug) != certificate:
        raise ValueError('Game signer must match its launcher')
    if info['name'] != f'com.gentv.game.{game_id}.plugin' or int(info['versionCode']) <= 0 or not re.fullmatch(r'\d+\.\d+\.\d+', info['versionName']):
        raise ValueError('Invalid game package or version')
    if not 0 < apk.stat().st_size <= 32 * 1024 * 1024: raise ValueError('Game exceeds size limit')
    validate_contents(apk, game_id)
    name = f'game_{game_id}.apk'
    data = apk.read_bytes()
    manifest = {
        'id': game_id, 'versionCode': int(info['versionCode']), 'versionName': info['versionName'],
        'baseApiVersion': 1, 'minBaseVersionCode': min_base, 'maxBaseVersionCode': max_base,
        'packageName': info['name'], 'entryClass': entry_class, 'signingCertificateSha256': certificate,
        'plugin': {'name': name, 'url': f"https://github.com/Elaine-Lynn/GenTV/releases/download/game-{game_id}-v{info['versionName']}/{name}",
                   'sha256': hashlib.sha256(data).hexdigest(), 'sizeBytes': len(data)},
    }
    folder = output / 'games' / game_id / info['versionCode']
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / name
    if target.exists() and target.read_bytes() != data: raise ValueError('Refusing to replace an immutable game version')
    shutil.copyfile(apk, target)
    encoded = json.dumps(manifest, ensure_ascii=False, indent=2) + '\n'
    (folder / 'manifest.json').write_text(encoded, encoding='utf-8')
    latest = output / 'games' / game_id / 'latest'
    latest.mkdir(parents=True, exist_ok=True)
    (latest / 'manifest.json').write_text(encoded, encoding='utf-8')
    server = output / 'server' / 'games' / game_id
    server.mkdir(parents=True, exist_ok=True)
    (server / 'update.json').write_text(encoded, encoding='utf-8')
    print(f'{game_id}: {len(data):,} bytes -> {target}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--base-apk', type=Path, required=True)
    parser.add_argument('--id', required=True)
    parser.add_argument('--entry-class', required=True)
    parser.add_argument('--output', type=Path, default=Path('work/game-runtime-release'))
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '')))
    parser.add_argument('--min-base', type=int, default=100)
    parser.add_argument('--max-base', type=int, default=2147483647)
    parser.add_argument('--allow-debug', action='store_true')
    args = parser.parse_args()
    package(args.apk, args.base_apk, args.id, args.entry_class, args.output, args.sdk, args.allow_debug, args.min_base, args.max_base)
