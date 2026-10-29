#!/usr/bin/env python3
"""Emulator-only smoke check. Never changes Home, system settings or package enable state."""
import argparse
import base64
import copy
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True)
p.add_argument('--adb',default='adb')
p.add_argument('--out',default='shots')
a=p.parse_args()
def adb(*args):
    return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True)
def shell(*args): return adb('shell',*args)
if not a.serial.startswith('emulator-') or shell('getprop','ro.kernel.qemu').strip()!='1':
    raise SystemExit('Refusing a non-emulator target')
def exchange(operation,profile=None,revision=-1):
    args=['am','broadcast','--user','0','-n','com.gentv.tv/.DesktopReceiver','--es','operation',operation,'--ei','revision',str(revision)]
    if profile is not None: args += ['--es','payload',base64.b64encode(json.dumps(profile,ensure_ascii=False).encode()).decode()]
    output=shell(*args)
    match=re.search(r'GENTV1:([A-Za-z0-9+/=]+)',output)
    if not match: raise RuntimeError('Desktop rejected request; read again before retrying')
    return json.loads(base64.b64decode(match[1]))
def ui():
    shell('uiautomator','dump','/sdcard/gentv-smoke-ui.xml')
    return ET.fromstring(shell('cat','/sdcard/gentv-smoke-ui.xml'))
def center(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    return str((x1+x2)//2),str((y1+y2)//2)
def screenshot(name):
    with (Path(a.out)/name).open('wb') as f:
        subprocess.run([a.adb,'-s',a.serial,'exec-out','screencap','-p'],stdout=f,check=True)
Path(a.out).mkdir(parents=True,exist_ok=True)
original=exchange('read')
assert original['config']['schema']==2
changed=False
try:
    profile=copy.deepcopy(original['config'])
    first=next(item['id'] for item in original['apps'] if item.get('package')!='com.gentv.tv')
    profile['names'][first]='Smoke check'
    saved=exchange('save',profile,original['revision']);changed=True
    assert saved['config']==profile
    try: exchange('save',profile,original['revision'])
    except RuntimeError: pass
    else: raise AssertionError('stale draft accepted')
    shell('am','force-stop','com.gentv.tv')
    assert exchange('read')['config']==profile, 'state lost on process restart'
    restored=exchange('undo',revision=saved['revision']);changed=False
    assert restored['config']==original['config'] and not restored['canUndo']
    shell('am','start','-W','-n','com.gentv.tv/.MainActivity')
    time.sleep(1)
    shell('input','keyevent','19','19','19','19')
    tree=ui()
    settings=next(n for n in tree.iter('node') if n.attrib.get('resource-id')=='com.gentv.tv:id/btnSettings')
    x,y=center(settings)
    shell('input','swipe',x,y,x,y,'1100')
    tree=ui()
    control=next(n for n in tree.iter('node') if n.attrib.get('text')=='Control center')
    shell('input','tap',*center(control))
    time.sleep(.5)
    tree=ui()
    titles={n.attrib.get('text') for n in tree.iter('node')}
    assert 'Inputs' in titles and 'Network' in titles, 'control center missing core entries'
    shell('input','keyevent','20')
    screenshot('gentv-control-center.png')
    shell('input','keyevent','4')
    shell('am','start','-W','-n','com.gentv.tv/.MainActivity')
    time.sleep(.5)
    shell('input','keyevent','20','20')
    screenshot('gentv-home.png')
    shell('input','keyevent','--longpress','23')
    tree=ui()
    assert any(n.attrib.get('text')=='Rename on Home' for n in tree.iter('node')), 'tile rename menu missing'
    screenshot('gentv-tile-menu.png')
    shell('input','keyevent','4')
    print('PASS: protocol read/save/readback, stale refusal, process persistence, exact single undo, TV control-center navigation')
finally:
    if changed:
        current=exchange('read')
        if current['canUndo']: exchange('undo',revision=current['revision'])
        else: raise RuntimeError('Restore needs attention; original profile was retained in this process')
