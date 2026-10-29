#!/usr/bin/env python3
"""Convert the approved TV-only GenTV SVGs to native Android vectors. Requires fontTools for text outlines."""
from pathlib import Path
import argparse,re,xml.etree.ElementTree as E
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
root=Path(__file__).resolve().parents[1];res=root/'launcher/app/src/main/res';art=root/'launcher/app/src/main/artwork'
A='http://schemas.android.com/apk/res/android';AA='http://schemas.android.com/aapt'
E.register_namespace('android',A);E.register_namespace('aapt',AA)
p=argparse.ArgumentParser();p.add_argument('--font',default='/System/Library/Fonts/HelveticaNeue.ttc');p.add_argument('--font-index',type=int,default=1);args=p.parse_args()
font=TTFont(args.font,fontNumber=args.font_index);glyphs=font.getGlyphSet();cmap=font.getBestCmap();upem=font['head'].unitsPerEm

def attrs(**values):return {f'{{{A}}}{k}':str(v) for k,v in values.items()}
def vector(w=108,h=108):return E.Element('vector',attrs(width=f'{w}dp',height=f'{h}dp',viewportWidth=w,viewportHeight=h))
def write(path,node):
 E.indent(node,space='    ');path.parent.mkdir(parents=True,exist_ok=True);path.write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from the approved TV-only artwork by tools/generate_tv_branding.py. -->\n'+E.tostring(node,encoding='unicode')+'\n')
def rect(n):
 x,y,w,h,r=[float(n.get(k,'0')) for k in ('x','y','width','height','rx')]
 return f'M{x+r},{y} H{x+w-r} Q{x+w},{y} {x+w},{y+r} V{y+h-r} Q{x+w},{y+h} {x+w-r},{y+h} H{x+r} Q{x},{y+h} {x},{y+h-r} V{y+r} Q{x},{y} {x+r},{y} Z' if r else f'M{x},{y}h{w}v{h}h{-w}Z'
def append(parent,n,gradients):
 kind=n.tag.split('}')[-1]
 if kind=='defs':return
 if kind=='g':
  g=parent
  for op,raw in re.findall(r'(translate|scale)\(([^)]+)\)',n.get('transform','')):
   values=[float(v) for v in re.split(r'[ ,]+',raw)];g=E.SubElement(g,'group',attrs(**({'translateX':values[0],'translateY':values[1] if len(values)>1 else 0} if op=='translate' else {'scaleX':values[0],'scaleY':values[1] if len(values)>1 else values[0]})))
  for child in n:append(g,child,gradients)
  return
 if kind=='text':
  x=float(n.get('x'));y=float(n.get('y'));size=float(n.get('font-size'));spacing=float(n.get('letter-spacing','0'));scale=size/upem
  for text,color,dx in [(n.text or '',n.get('fill'),0)]+[(c.text or '',c.get('fill',n.get('fill')),float(c.get('dx','0'))) for c in n]:
   x+=dx
   for char in text:
    name=cmap[ord(char)];pen=SVGPathPen(glyphs);glyphs[name].draw(TransformPen(pen,(scale,0,0,-scale,x,y)));E.SubElement(parent,'path',attrs(pathData=pen.getCommands(),fillColor=color));x+=glyphs[name].width*scale+spacing
  return
 if kind not in ('path','rect'):raise ValueError(kind)
 path=E.SubElement(parent,'path',attrs(pathData=n.get('d') if kind=='path' else rect(n)))
 for src,dest in [('fill','fillColor'),('stroke','strokeColor')]:
  value=n.get(src,'black' if src=='fill' else 'none')
  if value.startswith('url('):
   ident=value[5:-1];g=gradients[ident]
   bounds={'bg':(3,3,102,102),'rim':(26,34,56,40),'glass':(29,37,50,34),'wave':(29,49,50,22),'backdrop':(0,0,640,360)}[ident]
   if kind=='rect':bounds=tuple(float(n.get(k,'0')) for k in ('x','y','width','height'))
   x,y,w,h=bounds;fill=E.SubElement(path,f'{{{AA}}}attr',{'name':'android:'+dest});gradient=E.SubElement(fill,'gradient',attrs(startX=x+w*float(g.get('x1','0')),startY=y+h*float(g.get('y1','0')),endX=x+w*float(g.get('x2','1')),endY=y+h*float(g.get('y2','0')),type='linear'))
   for stop in g:E.SubElement(gradient,'item',attrs(offset=stop.get('offset','0'),color=stop.get('stop-color')))
  else:path.set(f'{{{A}}}{dest}',{'white':'#FFFFFF','black':'#000000','none':'#00000000'}.get(value,value))
 for src,dest in [('stroke-width','strokeWidth'),('stroke-linecap','strokeLineCap'),('opacity','fillAlpha')]:
  if n.get(src):path.set(f'{{{A}}}{dest}',n.get(src))
svg=E.parse(art/'ic_gentv.svg').getroot();gradients={e.get('id'):e for e in svg.iter() if e.tag.endswith('linearGradient')};children=[n for n in svg if not n.tag.endswith('defs')]
legacy=vector()
for n in children:append(legacy,n,gradients)
write(res/'drawable/ic_gentv.xml',legacy);write(res/'mipmap-anydpi/ic_launcher.xml',legacy)
background=vector();append(background,E.fromstring('<rect width="108" height="108" fill="url(#bg)"/>'),gradients);write(res/'drawable/ic_gentv_background.xml',background)
foreground=vector()
for n in children[1:]:append(foreground,n,gradients)
write(res/'drawable/ic_gentv_foreground.xml',foreground)
mono=vector()
E.SubElement(mono,'path',attrs(fillColor='#FFFFFF',fillType='evenOdd',pathData=children[1].get('d')+' '+children[2].get('d')))
E.SubElement(mono,'path',attrs(fillColor='#FFFFFF',pathData=children[3].get('d')))
E.SubElement(mono,'path',attrs(fillColor='#00000000',strokeColor='#FFFFFF',strokeWidth='1.8',strokeLineCap='round',pathData=children[-1].get('d')))
write(res/'drawable/ic_gentv_monochrome.xml',mono)
for version in (26,33):
 node=E.Element('adaptive-icon');E.SubElement(node,'background',attrs(drawable='@drawable/ic_gentv_background'));E.SubElement(node,'foreground',attrs(drawable='@drawable/ic_gentv_foreground'))
 if version==33:E.SubElement(node,'monochrome',attrs(drawable='@drawable/ic_gentv_monochrome'))
 write(res/f'mipmap-anydpi-v{version}/ic_launcher.xml',node)
banner=E.parse(art/'banner.svg').getroot();grads={e.get('id'):e for e in banner.iter() if e.tag.endswith('linearGradient')};out=vector(640,360);out.set(f'{{{A}}}width','320dp');out.set(f'{{{A}}}height','180dp')
for n in banner:append(out,n,grads)
write(res/'drawable/banner.xml',out)
