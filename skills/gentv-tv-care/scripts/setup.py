#!/usr/bin/env python3
"""Check an exact GenTV workspace or unpack a release-locked runtime. No downloads/device access."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

SKILL = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(root, lock):
    root = root.resolve()
    for name, expected in lock['workspace_sha256'].items():
        member = (root / name).resolve()
        if not member.is_relative_to(root) or not member.is_file() or digest(member) != expected:
            raise ValueError('Version mismatch or missing dependency: ' + name)
    return root


def unpack(archive, destination, lock):
    if destination.exists():
        raise ValueError('Destination must be new; existing workspace is never overwritten')
    if digest(archive) != lock['runtime']['sha256']:
        raise ValueError('Runtime archive SHA-256 does not match this skill release')
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            parts = Path(item.filename).parts
            if not parts or Path(item.filename).is_absolute() or '..' in parts or '\\' in item.filename or stat.S_ISLNK(item.external_attr >> 16):
                raise ValueError('Unsafe archive member')
        destination.mkdir(parents=True)
        for item in z.infolist():
            target = destination / item.filename
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with z.open(item) as source, target.open('wb') as output:
                    import shutil
                    shutil.copyfileobj(source, output)
                target.chmod(0o755 if (item.external_attr >> 16) & 0o111 else 0o644)
    return check(destination / lock['runtime']['directory'], lock)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path)
    p.add_argument('--bundle', type=Path)
    p.add_argument('--destination', type=Path)
    a = p.parse_args()
    lockfile = SKILL / 'release-lock.json'
    if not lockfile.is_file():
        p.error('Use a packaged skill release with release-lock.json; source checkout is for development')
    lock = json.loads(lockfile.read_text())
    if a.bundle:
        if not a.destination or a.repo:
            p.error('--bundle requires --destination and cannot combine with --repo')
        root = unpack(a.bundle, a.destination, lock)
    elif a.repo and not a.destination:
        root = check(a.repo, lock)
    else:
        p.error('Specify --repo, or --bundle with --destination')
    print(json.dumps({'status': 'ready', 'version': lock['version'], 'workspace': str(root)}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        raise SystemExit('GenTV setup stopped: ' + str(error))
