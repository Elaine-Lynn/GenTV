#!/usr/bin/env python3
"""Emulator-only folder/image UI and persistence regression; restores the prior profile."""
import argparse,base64,copy,io,json,re,subprocess,time,uuid,xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image,ImageDraw
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');p.add_argument('--out',default='shots');a=p.parse_args()
def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True)
def sh(*args):return adb('shell',*args)
if not a.serial.startswith('emulator-') or sh('getprop','ro.kernel.qemu').strip()!='1':raise SystemExit('Emulator only')
def exchange(op,profile=None,revision=-1):
 args=['am','broadcast','--user','0','-n','com.gentv.tv/.DesktopReceiver','--es','operation',op,'--ei','revision',str(revision)]
 if profile is not None:args+=['--es','payload',base64.b64encode(json.dumps(profile,ensure_ascii=False,separators=(',',':')).encode()).decode()]
 output=sh(*args);m=re.search(r'GENTV1:([A-Za-z0-9+/=]+)',output)
 if not m:raise RuntimeError('TV refused request')
 return json.loads(base64.b64decode(m[1]))
def ui():
 sh('uiautomator','dump','/sdcard/gentv-extras-ui.xml');return ET.fromstring(sh('cat','/sdcard/gentv-extras-ui.xml'))
def node(text):return next(n for n in ui().iter('node') if n.get('text')==text)
def center(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));return str((x1+x2)//2),str((y1+y2)//2)
def shot(name):
 with (out/name).open('wb') as f:subprocess.run([a.adb,'-s',a.serial,'exec-out','screencap','-p'],stdout=f,check=True)
def launch():sh('am','start','-W','-n','com.gentv.tv/.MainActivity');time.sleep(.6)
out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
original=exchange('read');changed=False
try:
 profile=copy.deepcopy(original['config']);apps={x['id']:x for x in original['apps'] if x['package']!='com.gentv.tv'}
 settings=next(x for x in apps if x.startswith('com.android.tv.settings/'))
 others=[x for x in profile['order'] if x in apps and x!=settings]
 assert len(others)>=2
 key='com.gentv.tv/Folder.'+uuid.uuid4().hex
 # Move apps from any prior folders only in this temporary draft.
 for f in profile['folders'].values():f['apps']=[x for x in f['apps'] if x not in [settings,others[0]]]
 profile['folders'][key]={'name':'Video test','apps':[settings,others[0],'com.missing.test/.Main']}
 profile['order']=[key]+[x for x in profile['order'] if x!=key]
 profile['hidden']=[x for x in profile['hidden'] if x not in [settings,others[0],others[1]]]
 image=Image.new('RGB',(320,180),'#153a60');draw=ImageDraw.Draw(image);draw.rounded_rectangle((20,20,300,160),radius=24,fill='#367bb1');draw.polygon([(135,55),(135,125),(195,90)],fill='white')
 buf=io.BytesIO();image.save(buf,'JPEG',quality=80);profile['images'][others[1]]=base64.b64encode(buf.getvalue()).decode()
 saved=exchange('save',profile,original['revision']);changed=True
 assert saved['config']==profile
 sh('am','force-stop','com.gentv.tv');assert exchange('read')['config']==profile
 launch();shot('gentv-folders-and-image.png')
 folder=node('Video test');sh('input','tap',*center(folder));time.sleep(.3)
 tree=ui();labels={n.get('text') for n in tree.iter('node')}
 assert apps[settings]['label'] in labels and apps[others[0]]['label'] in labels
 shot('gentv-folder-open.png')
 sh('input','tap',*center(node(apps[settings]['label'])));time.sleep(.3)
 assert 'com.android.tv.settings' in sh('dumpsys','activity','activities').split('mResumedActivity:')[-1]
 launch();folder=node('Video test');x,y=center(folder);sh('input','swipe',x,y,x,y,'1100')
 labels={n.get('text') for n in ui().iter('node')}
 assert {'Rename folder','Choose apps','Dissolve folder'}<=labels
 shot('gentv-folder-menu.png');sh('input','keyevent','4')
 sh('am','start','-W','-a','android.settings.SETTINGS');time.sleep(.6)
 sh('am','send-trim-memory','com.gentv.tv','RUNNING_CRITICAL');launch()
 sh('input','tap',*center(node('Video test')));time.sleep(.3)
 assert apps[settings]['label'] in {n.get('text') for n in ui().iter('node')}, 'folder lost after memory trim'
 sh('input','keyevent','4')
 dissolved=copy.deepcopy(profile);dissolved['folders'].pop(key);dissolved['order'].remove(key)
 latest=exchange('read');result=exchange('save',dissolved,latest['revision'])
 assert key not in result['config']['order'] and key not in result['config']['folders']
 assert settings in result['config']['order'] and result['config']['images']==profile['images']
 undone=exchange('undo',revision=result['revision']);assert undone['config']==profile
 print('PASS: folder/image save, restart persistence, folder contents, app launch, remote menu, memory-trim recovery, dissolve and complete undo')
finally:
 if changed:
  current=exchange('read');restored=exchange('save',original['config'],current['revision'])
  # The store deliberately preserves newly known missing app keys. Test synthetic folders are removed.
  assert restored['config']==original['config'],'Original desktop profile restore differs'
 sh('input','keyevent','4');launch()
