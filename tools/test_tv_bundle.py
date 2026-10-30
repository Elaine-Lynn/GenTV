#!/usr/bin/env python3
"""Real host smoke check; no television access."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


def verify(release):
    with tempfile.TemporaryDirectory(prefix='gentv-smoke-') as directory:
        temp = Path(directory)
        with zipfile.ZipFile(release/'gentv-tv-care-1.0.0.zip') as z: z.extractall(temp/'skill')
        skill = temp/'skill/gentv-tv-care'
        spec=importlib.util.spec_from_file_location('gentv_setup',skill/'scripts/setup.py'); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        lock=module.select_runtime(json.loads((skill/'release-lock.json').read_text()))
        workspace=module.unpack(release/lock['runtime']['file'],temp/'runtime',lock)
        suffix = '.exe' if os.name == 'nt' else ''
        env=os.environ.copy();env['JAVA_HOME']='/not-installed';env['PATH']=str(temp)
        subprocess.run([str(workspace/'bin'/('gentv-care'+suffix)), '--help'],env=env,check=True,timeout=120)
        subprocess.run([str(workspace/'bin'/('gentv-care'+suffix)), '--setup-transport'],env=env,check=True,timeout=120)
        subprocess.run([str(workspace/'bin'/('gentv-transport'+suffix)), '--self-test'],env=env,check=True,timeout=120)
        # Invalid endpoint is rejected before network or key generation; proves frozen client starts.
        proxy=subprocess.run([str(workspace/'bin'/('gentv-transport'+suffix)), '-s','8.8.8.8:5555','get-state'],env=env,capture_output=True,text=True,timeout=120)
        if proxy.returncode == 0 or 'Traceback' in proxy.stderr or 'Failed to execute' in proxy.stderr: raise ValueError('Frozen transport failed startup validation')
        print('PASS: extracted frozen launcher and transport start without Python/JDK/ADB on PATH')

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--release',type=Path,required=True);a=p.parse_args();verify(a.release.resolve())
