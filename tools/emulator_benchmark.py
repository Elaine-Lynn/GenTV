#!/usr/bin/env python3
"""Controlled emulator A/B. Installs/clears only the reserved com.gentv.perf test package."""
import argparse,base64,json,os,re,shutil,statistics,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');p.add_argument('--runs',type=int,default=7)
p.add_argument('--rounds',type=int,default=2);p.add_argument('--offline',action='store_true')
p.add_argument('--baseline-source',type=Path,help='Optional repository-local source snapshot for attribution')
p.add_argument('--out',type=Path,help='Output directory beneath this repository work/')
a=p.parse_args()
if a.runs<5 or a.rounds<2: raise SystemExit('Use at least five samples and two alternating rounds')
root=Path(__file__).resolve().parents[1]
out=(a.out or root/'work/perf').resolve()
baseline=(a.baseline_source or root/'work/performance-baseline').resolve()
if not out.is_relative_to(root/'work') or not baseline.is_relative_to(root) or baseline.is_relative_to(out):raise SystemExit('Use a repository-local baseline and separate work/ output')
out.mkdir(parents=True,exist_ok=True)
pkg='com.gentv.perf';component=pkg+'/com.gentv.tv.MainActivity'
def ad(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True)
def sh(*args):return ad('shell',*args)
if not a.serial.startswith('emulator-') or sh('getprop','ro.kernel.qemu').strip()!='1':raise SystemExit('Emulator-only')
def clone(src,dest):
 source_gradle=(src/'app/build.gradle').read_text()
 source_namespace=re.search(r'''namespace\s+['"]([^'"]+)['"]''',source_gradle)[1]
 if dest.exists():shutil.rmtree(dest)
 shutil.copytree(src,dest,ignore=shutil.ignore_patterns('build','.gradle','local.properties','*.apk','*.jks'))
 for f in dest.rglob('*'):
  if f.is_file() and f.suffix in ('.java','.xml','.gradle','.pro'):
   text=f.read_text().replace(source_namespace,'com.gentv.tv')
   f.write_text(text)
 gradle=dest/'app/build.gradle';text=gradle.read_text()
 text=re.sub(r'applicationId .*',"applicationId 'com.gentv.perf'",text)
 # Both variants share a benchmark version so the alternating install is never a downgrade.
 text=re.sub(r'versionCode \d+', 'versionCode 100', text)
 text=re.sub(r"versionName '[^']*'", "versionName 'benchmark'", text)
 gradle.write_text(text)
 # Match resource options, including library/menu switches, exactly between the variants.
 shutil.copyfile(root/'launcher/app/src/main/res/values/config.xml',dest/'app/src/main/res/values/config.xml')
 subprocess.run([str(dest/'gradlew'),'-p',str(dest),'--no-daemon',*(['--offline'] if a.offline else []),':app:assembleRelease'],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 return dest/'app/build/outputs/apk/release/app-release.apk'
print('Building controlled variants',flush=True)
apks={name:clone(source,out/name) for name,source in [('baseline',baseline),('candidate',root/'launcher')]}
def start():
 text=sh('am','start','-W','-n',component)
 timing={key:int(value) for key,value in re.findall(r'(TotalTime|WaitTime): (\d+)',text)}
 if 'WaitTime' not in timing or 'Status: ok' not in text:raise RuntimeError('Launcher did not start: '+text)
 return timing
def configure_candidate():
 def request(op,profile=None,revision=-1):
  args=['am','broadcast','--user','0','-n',pkg+'/com.gentv.tv.DesktopReceiver','--es','operation',op,'--ei','revision',str(revision)]
  if profile is not None:args+=['--es','payload',base64.b64encode(json.dumps(profile).encode()).decode()]
  reply=sh(*args);m=re.search(r'GENTV1:([A-Za-z0-9+/=]+)',reply)
  if not m:raise RuntimeError('Cannot match candidate effects')
  return json.loads(base64.b64decode(m[1]))
 state=request('read');profile=state['config'];profile['effects']='full';request('save',profile,state['revision'])
 time.sleep(1)
def median(values):return round(statistics.median(values),3)
installed=False
try:
 results=[]
 for turn in range(a.rounds):
  for name in (['baseline','candidate'] if turn%2==0 else ['candidate','baseline']):
   print(f'Round {turn+1}: {name}',flush=True)
   subprocess.run([a.adb,'-s',a.serial,'install','-r',str(apks[name])],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
   installed=True
   sh('pm','enable',pkg)
   # This identity belongs only to these source-built benchmark variants; no production app data is cleared.
   assert 'Success' in sh('pm','clear',pkg)
   start();time.sleep(1)
   if name=='candidate':configure_candidate()
   cold=[];warm=[]
   for _ in range(a.runs):
    sh('am','force-stop',pkg);cold.append(start()['TotalTime']);time.sleep(.3)
   for _ in range(a.runs):
    settings=sh('am','start','-W','-a','android.settings.SETTINGS')
    if 'Status: ok' not in settings:raise RuntimeError('Settings could not open for warm-return scenario')
    time.sleep(.2);warm.append(start()['WaitTime']);time.sleep(.3)
   sh('dumpsys','gfxinfo',pkg,'reset')
   for key in [22,22,22,21,21,21,20,19]*10:
    sh('input','keyevent',str(key));time.sleep(.08)
   frames=sh('dumpsys','gfxinfo',pkg,'framestats')
   (out/f'{turn}-{name}-frames.txt').write_text(frames)
   durations=[];header=None
   for line in frames.splitlines():
    if line.startswith('Flags,'):header=line.split(',');continue
    if header and re.match(r'^\d+,',line):
     row=line.split(',')
     try:
      flags=int(row[header.index('Flags')]);begin=int(row[header.index('IntendedVsync')]);end=int(row[header.index('FrameCompleted')])
      if flags==0 and begin>0 and end>begin and (end-begin)<10_000_000_000:durations.append((end-begin)/1e6)
     except (ValueError,IndexError):pass
   jank=re.search(r'Janky frames: \d+ \(([\d.]+)%\)',frames)
   memory=sh('dumpsys','meminfo',pkg)
   total=re.search(r'TOTAL PSS:\s*(\d+)',memory)
   if total is None:total=re.search(r'^\s*TOTAL\s+(\d+)',memory,re.M)
   if not total or not durations or not jank:raise RuntimeError('Required memory/frame metrics unavailable')
   result={'round':turn+1,'variant':name,'cold_ms':cold,'warm_launch_dispatch_wait_ms':warm,'pss_mb':round(int(total[1])/1024,3),
     'frame_ms_p50':median(durations),'frame_ms_p95':round(sorted(durations)[int(.95*(len(durations)-1))],3),'jank_pct':float(jank[1]),'frames':len(durations)}
   results.append(result);(out/'results.json').write_text(json.dumps(results,indent=2))
 summary={}
 for name in apks:
  rows=[r for r in results if r['variant']==name]
  summary[name]={'cold_ms_p50':median([v for r in rows for v in r['cold_ms']]),'warm_launch_dispatch_wait_ms_p50':median([v for r in rows for v in r['warm_launch_dispatch_wait_ms']]),
   **{k:median([r[k] for r in rows]) for k in ['pss_mb','frame_ms_p50','frame_ms_p95','jank_pct']}}
 (out/'summary.json').write_text(json.dumps(summary,indent=2))
 print(json.dumps(summary),flush=True)
 print('Only emulator Activity launch/dispatch, frame and PSS samples. This does not certify physical Home response or real-TV performance.',flush=True)
finally:
 if installed:
  sh('am','force-stop',pkg)
  home=sh('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
  if pkg+'/' not in home:
   sh('pm','disable-user','--user','0',pkg)
   print('Disabled the owned benchmark package; pm enable com.gentv.perf restores it.',flush=True)
  else:
   print('Benchmark package currently resolves as Home; left enabled.',flush=True)
