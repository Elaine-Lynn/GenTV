#!/usr/bin/env python3
"""Freeze host Python entrypoints into a matching runtime bundle; build machines only."""
import os
from pathlib import Path
import subprocess
import sys
import importlib.metadata

def freeze(root, output):
    work = root / 'phone/tv-care/build/frozen'
    work.mkdir(parents=True, exist_ok=True)
    licenses = output / 'licenses/frozen-python'
    licenses.mkdir(parents=True, exist_ok=True)
    import shutil
    import urllib.request
    version = '.'.join(map(str, sys.version_info[:3]))
    with urllib.request.urlopen('https://raw.githubusercontent.com/python/cpython/v' + version + '/LICENSE', timeout=30) as response:
        (licenses / 'Python-LICENSE.txt').write_bytes(response.read())
    if os.environ.get('OPENSSL_STATIC') == '1':
        openssl = Path(os.environ['OPENSSL_DIR'])
        for pattern in ('LICENSE*', 'share/doc/**/LICENSE*'):
            for source in openssl.glob(pattern):
                if source.is_file(): shutil.copy2(source, licenses / ('OpenSSL-' + source.name))
    for dist in importlib.metadata.distributions():
        for entry in dist.files or []:
            if any(word in entry.name.lower() for word in ('license', 'copying', 'copyright')) and '..' not in entry.parts:
                source = dist.locate_file(entry)
                if source.is_file():
                    target = licenses / dist.metadata['Name'] / str(entry)
                    target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(source, target)
    for name, source in [('gentv-care', 'tv_care.py'), ('gentv-transport', 'tv_transport.py'), ('gentv-relay', 'tv_relay.py')]:
        args = [sys.executable, '-m', 'PyInstaller', '--noconfirm', '--clean', '--onefile', '--name', name,
                '--distpath', str(output / 'bin'), '--workpath', str(work / name), '--specpath', str(work),
                '--paths', str(root / 'tools')]
        if name == 'gentv-transport': args += ['--collect-all', 'adb_shell', '--collect-all', 'cryptography', '--hidden-import', '_cffi_backend']
        subprocess.run(args + [str(root / 'tools' / source)], check=True, stdout=sys.stderr)
