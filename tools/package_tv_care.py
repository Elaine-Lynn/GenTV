#!/usr/bin/env python3
"""Build an OS/architecture-specific maintenance bundle with a private Java runtime.

Output stays under phone/tv-care/build. Does not connect to a TV or package APKs,
ADB credentials, signing material, local.properties, user sessions or SDK binaries.
"""
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tv_care


def package():
    root = tv_care.ROOT
    executable, env = tv_care.ensure_runtime()
    java_home = Path(env.get('JAVA_HOME', ''))
    jlink = java_home / 'bin' / ('jlink.exe' if os.name == 'nt' else 'jlink')
    if not jlink.is_file():
        raise ValueError('Packaging needs a local JDK with jlink; end users do not need a JDK')
    build = root / 'phone/tv-care/build/bundles'
    build.mkdir(parents=True, exist_ok=True)
    name = 'gentv-tv-care-' + platform.system().lower() + '-' + platform.machine()
    output = build / name
    if output.exists(): shutil.rmtree(output)
    output.mkdir()
    shutil.copytree(executable.parent.parent, output / 'maintenance', ignore=shutil.ignore_patterns('gentv-runtime.json'))
    subprocess.run([str(jlink), '--add-modules', 'java.base,java.logging,java.management,java.naming,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.charsets', '--strip-debug', '--no-header-files', '--no-man-pages', '--output', str(output / 'runtime')], check=True)
    # Explicit allowlist: never copy the workspace or its ignored/private directories.
    for directory in ('phone/framework/data/devices', 'skills/gentv-tv-care', 'licenses'):
        shutil.copytree(root / directory, output / directory, ignore=shutil.ignore_patterns('__pycache__', '*.pyc', '.DS_Store'))
    for name in ('tools/tv_care.py', 'tools/tv_transport.py', 'tools/requirements-tv-transport.txt', 'tools/tv_relay.py', 'docs/tv-care.md', 'docs/desktop-protocol.md', 'AGENTS.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md'):
        target = output / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(root / name, target)
    # Retain source context for the assistant and the product's local-first workflow.
    for directory in ('phone/framework/core/src/main', 'phone/tv-care/src/main'):
        shutil.copytree(root / directory, output / directory, ignore=shutil.ignore_patterns('.DS_Store'))
    for name in ('launcher/app/src/main/java/com/gentv/tv/Config.java', 'docs/getting-started.md', 'CONTRIBUTING.md', 'README.md'):
        target = output / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(root / name, target)
    launcher = 'maintenance/bin/' + ('tv-care.bat' if os.name == 'nt' else 'tv-care')
    hashes = {p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in output.rglob('*') if p.is_file()}
    (output / 'maintenance-bundle.json').write_text(json.dumps({'version': json.loads((root / 'skills/gentv-tv-care/release.json').read_text())['version'], 'platform': platform.system(), 'architecture': platform.machine(), 'launcher': launcher, 'sha256': hashes}, indent=2) + '\n')
    archive = shutil.make_archive(str(output), 'zip', output.parent, output.name)
    print(json.dumps({'bundle': str(output), 'archive': archive, 'sha256': hashlib.sha256(Path(archive).read_bytes()).hexdigest()}, indent=2))
    return output


if __name__ == '__main__':
    try: package()
    except (OSError, ValueError, subprocess.CalledProcessError) as error: raise SystemExit(str(error))
