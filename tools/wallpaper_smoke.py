#!/usr/bin/env python3
"""Emulator-only debug fixture: rotation, pause, empty feed and failed-network fallback."""
import argparse,importlib.util,io,json,re,subprocess,tempfile,threading,time,xml.etree.ElementTree as ET
from pathlib import Path
from socketserver import ThreadingMixIn
from wsgiref.simple_server import WSGIServer,WSGIRequestHandler,make_server
from PIL import Image
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
def adb(*args,check=True,binary=False):
 return subprocess.run([a.adb,'-s',a.serial,*args],check=check,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=not binary)
def sh(*args,check=True):return adb('shell',*args,check=check).stdout
assert a.serial.startswith('emulator-') and sh('getprop','ro.kernel.qemu').strip()=='1','Emulator only'
assert adb('shell','run-as','com.gentv.tv','pwd',check=False).returncode==0,'Install the source-built debug APK first'
spec=importlib.util.spec_from_file_location('wallpaper_fixture',root/'server/wallpaper/app.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
requests=[]
class Server(ThreadingMixIn,WSGIServer):daemon_threads=True
class Logs(WSGIRequestHandler):
 def log_message(self,fmt,*args):requests.append(fmt % args)
def wait_for(test,timeout=14):
 until=time.monotonic()+timeout
 while time.monotonic()<until:
  if test():return
  time.sleep(.2)
 raise AssertionError('Timed out waiting for wallpaper state')
def cached():return '.img' in sh('run-as','com.gentv.tv','ls','files/wallpapers-test')
def crop():
 b=adb('exec-out','screencap','-p',binary=True).stdout
 return Image.open(io.BytesIO(b)).crop((800,300,1100,500)).tobytes()
with tempfile.TemporaryDirectory(prefix='gentv-wallpaper-') as tmp:
 directory=Path(tmp);module.MEDIA=directory
 (directory/'0-bad.jpg').write_bytes(b'invalid image fixture')
 for name,color in [('a.jpg',(30,60,140)),('b.jpg',(140,65,30))]:Image.new('RGB',(640,360),color).save(directory/name,'JPEG')
 http=make_server('127.0.0.1',0,module.app,server_class=Server,handler_class=Logs);port=http.server_port;module.BASE=f'http://127.0.0.1:{port}'
 thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start();closed=False
 adb('reverse',f'tcp:{port}',f'tcp:{port}')
 try:
  sh('am','force-stop','com.gentv.tv')
  sh('am','start','-W','-n','com.gentv.tv/.MainActivity','--es','gentv.wallpaper.test_endpoint',f'{module.BASE}/wallpaper')
  wait_for(lambda:any('GET /wallpapers/a.jpg' in r for r in requests))
  time.sleep(.5);first=crop()
  wait_for(lambda:any('GET /wallpapers/b.jpg' in r for r in requests))
  time.sleep(.5);assert crop()!=first,'Second image did not render'
  # A full repeat must use both persistent images, without downloading either again.
  time.sleep(11)
  assert sum('GET /wallpapers/a.jpg' in r for r in requests)==1,'First image downloaded again'
  assert sum('GET /wallpapers/b.jpg' in r for r in requests)==1,'Second image downloaded again'
  # A process restart must restore disk images before refreshing the list.
  sh('am','force-stop','com.gentv.tv')
  sh('am','start','-W','-n','com.gentv.tv/.MainActivity','--es','gentv.wallpaper.test_endpoint',f'{module.BASE}/wallpaper')
  time.sleep(1)
  assert sum('GET /wallpapers/a.jpg' in r for r in requests)==1
  assert sum('GET /wallpapers/b.jpg' in r for r in requests)==1
  sh('uiautomator','dump','/sdcard/gentv-wallpaper-test.xml')
  ui=ET.fromstring(sh('cat','/sdcard/gentv-wallpaper-test.xml'))
  control=next(n for n in ui.iter('node') if n.get('resource-id')=='com.gentv.tv:id/btnSettings')
  assert control.get('text')=='' and control.get('content-desc')=='Control center'
  brand=next(n for n in ui.iter('node') if n.get('resource-id')=='com.gentv.tv:id/greeting')
  assert brand.get('text')=='GenTV','Brand text missing'
  bx1,by1,bx2,by2=map(int,re.findall(r'\d+',brand.get('bounds')))
  cx1,cy1,cx2,cy2=map(int,re.findall(r'\d+',control.get('bounds')))
  assert bx1>cx2 and abs((by1+by2)-(cy1+cy2))<35,'Brand is not right of the controls on the same row'
  x1,y1,x2,y2=map(int,re.findall(r'\d+',control.get('bounds')))
  sh('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)
  count=len(requests);time.sleep(6);assert len(requests)==count,'Background polling continued'
  sh('input','keyevent','4')
  for f in directory.glob('*.jpg'):f.rename(f.with_suffix('.hold'))
  wait_for(lambda:not cached())
  for f in directory.glob('*.hold'):f.rename(f.with_suffix('.jpg'))
  wait_for(cached);time.sleep(.5)
  wait_for(lambda:sum('GET /wallpapers/b.jpg' in r for r in requests)>=2)
  time.sleep(.5)
  http.shutdown();http.server_close();closed=True
  time.sleep(1);retained=crop();time.sleep(6);assert crop()!=retained,'Offline rotation did not use saved playlist'
  assert cached(),'Failed request deleted last good cache'
  print('PASS: invalid image skipped, both images rendered, rotation, no paused polling, empty-feed reset, zero repeat image downloads, process-restart disk restore, offline rotation')
 finally:
  if not closed:http.shutdown();http.server_close()
  adb('reverse','--remove',f'tcp:{port}',check=False)
  sh('am','force-stop','com.gentv.tv')
  sh('am','start','-W','-n','com.gentv.tv/.MainActivity')
