#!/usr/bin/env python3
"""Merge per-platform CI candidates into one skill; never publish from a developer machine."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import zipfile


def assemble(artifacts, output):
    archives = list(artifacts.rglob('gentv-tv-care-1.0.0.zip'))
    if not archives: raise ValueError('No platform skill artifacts')
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        stage = Path(directory)
        locks = []
        baseline = None
        for index, archive in enumerate(archives):
            with zipfile.ZipFile(archive) as z:
                members = {i.filename: z.read(i) for i in z.infolist() if not i.is_dir() and not i.filename.endswith(('/release-lock.json', '/scripts/prepare.sh', '/scripts/prepare.ps1'))}
                if baseline is None:
                    baseline = members
                    for name, data in members.items():
                        path = Path(name)
                        if path.is_absolute() or '..' in path.parts: raise ValueError('Unsafe skill member')
                        target = stage / path; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
                elif baseline != members: raise ValueError('Skill sources differ across platforms')
                lock = json.loads(z.read('gentv-tv-care/release-lock.json')); locks.append(lock)
                runtime = archive.parent / lock['runtime']['file']
                if hashlib.sha256(runtime.read_bytes()).hexdigest() != lock['runtime']['sha256']: raise ValueError('Runtime hash mismatch')
                shutil.copy2(runtime, output / runtime.name)
        first = locks[0]
        for lock in locks:
            if any(lock[k] != first[k] for k in ('version', 'release_url', 'workspace_sha256')): raise ValueError('Inconsistent versions or sources')
        runtimes = [lock['runtime'] for lock in locks]
        if len({(r['platform'],r['architecture']) for r in runtimes}) != len(runtimes): raise ValueError('Duplicate platform')
        merged = {k:v for k,v in first.items() if k != 'runtime'}; merged['runtimes'] = runtimes
        (stage/'gentv-tv-care/release-lock.json').write_text(json.dumps(merged, indent=2)+'\n')
        from tv_bootstrap import write_bootstraps
        write_bootstraps(stage/'gentv-tv-care', merged)
        shutil.make_archive(str(output / ('gentv-tv-care-' + first['version'])), 'zip', stage, 'gentv-tv-care')
        (output/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(output.glob('*.zip'))))
        print(json.dumps({'platforms':len(runtimes),'output':str(output)}))

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--artifacts',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();assemble(a.artifacts,a.output)
