#!/usr/bin/env python3
"""Build Android vector layers from the approved mobile icon SVG (stdlib only)."""
from pathlib import Path
import re
import math
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/artwork/ic_gentv.svg'
OUT = ROOT / 'app/src/main/res/drawable'
A = 'http://schemas.android.com/apk/res/android'
AA = 'http://schemas.android.com/aapt'
ET.register_namespace('android', A)
ET.register_namespace('aapt', AA)
SVG = ET.parse(SOURCE).getroot()
GRADIENTS = {e.get('id'): e for e in SVG.iter() if e.tag.endswith('linearGradient')}

def attr(node, name, value):
    node.set(f'{{{A}}}{name}', str(value))

def vector():
    return ET.Element('vector', {f'{{{A}}}{key}': value for key, value in {
        'width': '108dp', 'height': '108dp', 'viewportWidth': '128', 'viewportHeight': '128',
    }.items()})

def rect(x, y, w, h, r):
    return (f'M{x+r},{y} H{x+w-r} A{r},{r} 0,0 1,{x+w},{y+r} V{y+h-r} '
            f'A{r},{r} 0,0 1,{x+w-r},{y+h} H{x+r} A{r},{r} 0,0 1,{x},{y+h-r} '
            f'V{y+r} A{r},{r} 0,0 1,{x+r},{y} Z') if r else f'M{x},{y} H{x+w} V{y+h} H{x} Z'

def path_data(node):
    kind = node.tag.split('}')[-1]
    if kind == 'path': return node.get('d')
    if kind == 'rect':
        return rect(*(float(node.get(k, '0')) for k in ['x', 'y', 'width', 'height', 'rx']))
    if kind == 'circle':
        x, y, r = (float(node.get(k)) for k in ['cx', 'cy', 'r'])
        return f'M{x-r},{y} A{r},{r} 0,1 0,{x+r},{y} A{r},{r} 0,1 0,{x-r},{y} Z'
    raise ValueError(kind)

def color(value):
    return {'white': '#FFFFFF', 'black': '#000000', 'none': '#00000000'}.get(value, value)

def gradient(path, name, identifier, bounds):
    src = GRADIENTS[identifier]
    x, y, w, h = bounds
    fill = ET.SubElement(path, f'{{{AA}}}attr', {'name': f'android:{name}'})
    g = ET.SubElement(fill, 'gradient')
    for key, value in {'startX': x + w*float(src.get('x1', '0')), 'startY': y + h*float(src.get('y1', '0')),
                       'endX': x + w*float(src.get('x2', '1')), 'endY': y + h*float(src.get('y2', '0')),
                       'type': 'linear'}.items(): attr(g, key, value)
    for stop in src:
        item = ET.SubElement(g, 'item')
        attr(item, 'offset', stop.get('offset', '0')); attr(item, 'color', color(stop.get('stop-color')))

def bounds(node):
    # Approved TV paths: SVG gradients use objectBoundingBox coordinates.
    identifier = node.get('fill', '')
    return {'url(#bg)': (0, 0, 128, 128), 'url(#rim)': (26, 34, 56, 40),
            'url(#glass)': (29, 37, 50, 34), 'url(#wave)': (29, 49, 50, 22)}.get(identifier, (0, 0, 128, 128))

def append(parent, node, inherited=None, mono=False):
    inherited = dict(inherited or {})
    inherited.update({k: v for k, v in node.attrib.items() if k in ['fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin']})
    kind = node.tag.split('}')[-1]
    if kind == 'defs': return
    if kind == 'g':
        dest = parent
        for operation, args in re.findall(r'(translate|scale|rotate)\(([^)]+)\)', node.get('transform', '')):
            values = [float(v) for v in re.split(r'[ ,]+', args)]
            dest = ET.SubElement(dest, 'group')
            if operation == 'translate':
                attr(dest, 'translateX', values[0]); attr(dest, 'translateY', values[1] if len(values)>1 else 0)
            elif operation == 'scale':
                attr(dest, 'scaleX', values[0]); attr(dest, 'scaleY', values[1] if len(values)>1 else values[0])
            else:
                attr(dest, 'rotation', values[0])
                if len(values)>1: attr(dest, 'pivotX', values[1]); attr(dest, 'pivotY', values[2])
        for child in node: append(dest, child, inherited, mono)
        return
    if kind not in ['path', 'rect', 'circle']: raise ValueError(kind)
    path = ET.SubElement(parent, 'path'); attr(path, 'pathData', path_data(node))
    for key, attribute, default in [('fill', 'fillColor', 'black'), ('stroke', 'strokeColor', 'none')]:
        value = inherited.get(key, default)
        if mono:
            # A line-art themed icon: outlines and controls stay legible at launcher size.
            if key == 'fill': value = '#FFFFFF' if kind == 'circle' and node.get('r') == '3' else 'none'
            elif value != 'none': value = '#FFFFFF'
        if value.startswith('url('): gradient(path, attribute, value[5:-1], bounds(node))
        else: attr(path, attribute, color(value))
    for key, dest in [('stroke-width', 'strokeWidth'), ('stroke-linecap', 'strokeLineCap'), ('stroke-linejoin', 'strokeLineJoin')]:
        if key in inherited: attr(path, dest, inherited[key])
    if node.get('opacity') and not mono:
        attr(path, 'fillAlpha', node.get('opacity')); attr(path, 'strokeAlpha', node.get('opacity'))
    if mono and kind == 'rect' and node.get('x') == '70':
        attr(path, 'strokeColor', '#FFFFFF'); attr(path, 'strokeWidth', '2.3')


def write(name, node):
    ET.indent(node, space='    ')
    content = ET.tostring(node, encoding='unicode')
    (OUT / name).write_text('<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated from app/src/main/artwork/ic_gentv.svg by tools/generate_launcher_icon.py. -->\n' + content + '\n')

children = [n for n in SVG if not n.tag.endswith('defs')]
legacy = vector()
for child in children: append(legacy, child)
write('ic_gentv.xml', legacy)
background = vector()
full_bg = ET.fromstring('<rect x="0" y="0" width="128" height="128" fill="url(#bg)"/>')
append(background, full_bg)
write('ic_gentv_background.xml', background)
foreground = vector()
safe = ET.SubElement(foreground, 'group')
for key, value in {'pivotX': 64, 'pivotY': 64, 'scaleX': .72, 'scaleY': .72}.items(): attr(safe, key, value)
for child in children[1:]: append(safe, child)
write('ic_gentv_foreground.xml', foreground)
# TV rim is an even-odd ring, wave and stand form the back; the phone uses the approved scaled layout.
mono = vector()
safe_m = ET.SubElement(mono, 'group', dict(safe.attrib))
# Cut the phone silhouette out of the TV before drawing the themed phone outline.
# The points use the same 16-degree rotation and 75% scale as the approved SVG.
def phone_clip():
    x, y, w, h, r = 68.85, 44.85, 38.3, 66.3, 9.15
    k = .5522847498307936
    segments = [('M', [x+r,y]), ('L', [x+w-r,y]),
        ('C', [x+w-r+k*r,y, x+w,y+r-k*r, x+w,y+r]), ('L', [x+w,y+h-r]),
        ('C', [x+w,y+h-r+k*r, x+w-r+k*r,y+h, x+w-r,y+h]), ('L', [x+r,y+h]),
        ('C', [x+r-k*r,y+h, x,y+h-r+k*r, x,y+h-r]), ('L', [x,y+r]),
        ('C', [x,y+r-k*r, x+r-k*r,y, x+r,y])]
    theta = math.radians(16)
    def point(px, py):
        return (90 + .75 * (math.cos(theta)*(px-90) - math.sin(theta)*(py-80)),
                80 + .75 * (math.sin(theta)*(px-90) + math.cos(theta)*(py-80)))
    # Opposite winding also works on renderers that ignore clip-path fillType.
    cursor = segments[0][1]
    edges = []
    for command, numbers in segments[1:]:
        edges.append((command, cursor, numbers))
        cursor = numbers[-2:]
    reversed_segments = [segments[0]]
    for command, start, numbers in reversed(edges):
        reversed_segments.append((command, numbers[2:4] + numbers[0:2] + start if command == 'C' else start))
    result = 'M0,0 H128 V128 H0 Z '
    for command, numbers in reversed_segments:
        result += command + ' '.join(','.join(f'{v:.5f}' for v in point(*numbers[i:i+2])) for i in range(0, len(numbers), 2)) + ' '
    return result + 'Z'
masked_tv = ET.SubElement(safe_m, 'group')
clip = ET.SubElement(masked_tv, 'clip-path'); attr(clip, 'fillType', 'nonZero'); attr(clip, 'pathData', phone_clip())
tv = ET.SubElement(masked_tv, 'group')
for key, value in {'translateX': -20, 'translateY': -25, 'scaleX': 1.45, 'scaleY': 1.45}.items(): attr(tv, key, value)
outer, inner = list(children[1])[:2]
ring = ET.SubElement(tv, 'path'); attr(ring, 'fillColor', '#FFFFFF'); attr(ring, 'fillType', 'evenOdd')
attr(ring, 'pathData', path_data(outer) + ' ' + path_data(inner))
wave = ET.SubElement(tv, 'path'); attr(wave, 'fillColor', '#FFFFFF'); attr(wave, 'pathData', path_data(list(children[1])[2]))
append(tv, list(children[1])[-1], mono=True)
append(safe_m, children[2], mono=True)
write('ic_gentv_monochrome.xml', mono)
