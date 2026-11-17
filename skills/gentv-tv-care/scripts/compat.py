#!/usr/bin/env python3
"""Inspect a local GenTV checkout and search its device records. No network or TV writes."""
import argparse
import json
from pathlib import Path
import shutil

CAPABILITIES = {
    'backup': 'phone/framework/core/src/main/kotlin/com/gentv/core/ops/Backup.kt',
    'cleanup': 'phone/framework/core/src/main/kotlin/com/gentv/core/ops/Debloater.kt',
    'protection': 'phone/framework/core/src/main/kotlin/com/gentv/core/lists/Protected.kt',
    'restore': 'phone/framework/core/src/main/kotlin/com/gentv/core/ops/Restorer.kt',
    'handover': 'phone/framework/core/src/main/kotlin/com/gentv/core/ops/LauncherHandover.kt',
    'dns': 'phone/framework/core/src/main/kotlin/com/gentv/core/ops/PrivateDns.kt',
    'storage': 'phone/framework/core/src/main/kotlin/com/gentv/core/ops/TvFacts.kt',
    'matching': 'phone/framework/core/src/main/kotlin/com/gentv/core/lists/ModelMatcher.kt',
    'screenshot': 'phone/app/src/main/java/com/gentv/mobile/data/TvScreenCapture.kt',
    'transport': 'phone/framework/adb/src/main/kotlin/com/gentv/adb/KadbTvShell.kt',
}


def checkout(value):
    root = Path(value).expanduser().resolve()
    for name in ('AGENTS.md', 'docs/getting-started.md', CAPABILITIES['cleanup']):
        if not (root / name).is_file():
            raise ValueError(f'Not a complete GenTV checkout: missing {name}')
    return root


def search(root, query):
    query = query.strip().casefold()
    if not query:
        raise ValueError('Search query cannot be empty')
    bases = [root / 'phone/framework/data/devices', root / 'docs/devices']
    found = []
    for base in bases:
        for path in sorted(base.rglob('*')):
            if not path.is_file() or path.is_symlink() or path.suffix not in {'.txt', '.md', '.json'}:
                continue
            if not path.resolve().is_relative_to(root):
                continue
            body = path.read_text(encoding='utf-8-sig')
            relative = path.relative_to(root).as_posix()
            if query in relative.casefold() or query in body.casefold():
                found.append({'path': str(path), 'kind': 'reference'})
    supplements = root / 'skills/gentv-tv-care/references/device-supplements.json'
    if supplements.is_file():
        data = json.loads(supplements.read_text())
        for identifier, text in data['lists'].items():
            if query in identifier.casefold() or query in text.casefold():
                found.append({'path': str(supplements), 'record': identifier, 'kind': 'reference'})
    return found


def inspect(root):
    return {
        'repository': str(root),
        'device_records': len(list((root / 'phone/framework/data/devices').glob('*/debloat.txt'))),
        'supplement_records': len(json.loads((root / 'skills/gentv-tv-care/references/device-supplements.json').read_text())['lists']) if (root / 'skills/gentv-tv-care/references/device-supplements.json').is_file() else 0,
        'capabilities': {key: {'path': str(root / name), 'present': (root / name).is_file()}
                         for key, name in CAPABILITIES.items()},
        'local_tools': {name: shutil.which(name) for name in ('adb', 'java', 'python3')},
        'execution': 'Run tools/tv_care.py for the local JVM adapter using the shared core.',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True, help='Explicit local GenTV checkout')
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('inspect')
    find = sub.add_parser('search')
    find.add_argument('query')
    args = parser.parse_args()
    root = checkout(args.repo)
    result = inspect(root) if args.command == 'inspect' else search(root, args.query)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        raise SystemExit(f'Local reference lookup stopped: {error}')
