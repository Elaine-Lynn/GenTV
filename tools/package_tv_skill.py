#!/usr/bin/env python3
"""Create a local skill release candidate and its exact runtime dependency. Never publish."""
import hashlib
import json
from pathlib import Path
import shutil
import package_tv_care


def package():
    runtime = package_tv_care.package()
    root = package_tv_care.tv_care.ROOT
    source = root / 'skills/gentv-tv-care'
    release = json.loads((source / 'release.json').read_text())
    version = release['version']
    output = root / 'phone/tv-care/build/skill-release' / version
    output.mkdir(parents=True, exist_ok=True)
    stage = output / 'gentv-tv-care'
    if stage.exists(): shutil.rmtree(stage)
    shutil.copytree(source, stage, ignore=shutil.ignore_patterns('__pycache__', '*.pyc', '.DS_Store', 'release-lock.json'))
    shutil.copy2(root / 'LICENSE', stage / 'LICENSE')
    shutil.copy2(root / 'THIRD_PARTY_NOTICES.md', stage / 'THIRD_PARTY_NOTICES.md')
    shutil.copytree(root / 'licenses', stage / 'licenses', ignore=shutil.ignore_patterns('.DS_Store'))
    archive = Path(str(runtime) + '.zip')
    runtime_copy = output / archive.name
    shutil.copy2(archive, runtime_copy)
    manifest = json.loads((runtime / 'maintenance-bundle.json').read_text())
    workspace = {name: digest for name, digest in manifest['sha256'].items()
                 if not name.startswith(('runtime/', 'maintenance/', 'skills/'))}
    lock = {'version': version, 'published': False,
            'runtime': {'file': archive.name, 'directory': runtime.name,
                        'platform': manifest['platform'], 'architecture': manifest['architecture'],
                        'sha256': hashlib.sha256(archive.read_bytes()).hexdigest()},
            'workspace_sha256': workspace}
    (stage / 'release-lock.json').write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n')
    skill_archive = Path(shutil.make_archive(str(output / ('gentv-tv-care-' + version)), 'zip', output, stage.name))
    # Store submission notes are private publisher material in the ignored work/ directory.
    submission = root / 'work/cocoloop-submission.md'
    if submission.is_file(): shutil.copy2(submission, output / 'CocoLoop提交文案.md')
    assets = [skill_archive, runtime_copy]
    (output / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in assets))
    receipt = {'version': version, 'published': False, 'files': [p.name for p in assets],
               'skill_sha256': hashlib.sha256(skill_archive.read_bytes()).hexdigest(),
               'runtime_sha256': lock['runtime']['sha256']}
    (output / 'release-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({'release_directory': str(output), **receipt}, ensure_ascii=False, indent=2))
    return output


if __name__ == '__main__': package()
