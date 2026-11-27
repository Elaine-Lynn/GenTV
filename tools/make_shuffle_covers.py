#!/usr/bin/env python3
"""Render the wallpaper gallery's Shuffle covers ahead of time.

Each cover is a 2x2 mosaic of wallpapers from its scope (two side by side when a scope has only two),
a soft centre vignette, a pair of circular shuffle arrows and, for scopes with motion wallpapers, a small
play badge in the bottom-left corner. The covers ship in the APK so the Shuffle shelf shows at once,
offline, without composing bitmaps on the TV.

    python3 tools/make_shuffle_covers.py PREVIEW_DIR

PREVIEW_DIR holds the gallery previews as the API serves them (for example
catWallpaperLoop1080p-preview.jpg, alpine-lake.jpg). Re-run after the catalogue's wallpapers change.
Requires Pillow (tools/requirements-dev.txt).
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / 'launcher/app/src/main/res/drawable-nodpi'
W, H = 768, 432          # 16:9, sharp on a 1080p card (about 524 x 295 px) with room for 1.5x screens
SS = 3                   # supersampling for the vector parts

# scope -> (output name, previews in reading order, play badge)
COVERS = {
    'all': ('wallpaper_shuffle_all', ['catWallpaperLoop1080p-preview.jpg', 'nature-alpine-lake-preview.jpg',
                                       'dreamy-sakura-girl-preview.jpg', 'milky-way.jpg'], True),
    'pets': ('wallpaper_shuffle_pets', ['catWallpaperLoop1080p-preview.jpg', 'pet-sunlit-cat-preview.jpg',
                                         'pet-tea-bear-preview.jpg', 'pet-rainbow-ducks-preview.jpg'], True),
    'nature': ('wallpaper_shuffle_nature', ['nature-sunlit-leaves-preview.jpg',
                                             'nature-alpine-lake-preview.jpg'], True),
    'dreamy': ('wallpaper_shuffle_dreamy', ['dreamy-sakura-girl-preview.jpg', 'dreamy-afternoon-girl-preview.jpg',
                                             'dreamy-purple-girl-preview.jpg', 'dreamy-underwater-girl-preview.jpg'],
               True),
    'images': ('wallpaper_shuffle_images', ['alpine-lake.jpg', 'autumn.jpg', 'milky-way.jpg', 'winter.jpg'], False),
}


def fill(image, w, h):
    """Centre-crop to w:h, then scale to w x h."""
    src_w, src_h = image.size
    scale = max(w / src_w, h / src_h)
    crop_w, crop_h = w / scale, h / scale
    left, top = (src_w - crop_w) / 2, (src_h - crop_h) / 2
    return image.resize((w, h), Image.LANCZOS, box=(left, top, left + crop_w, top + crop_h))


def mosaic(paths):
    canvas = Image.new('RGB', (W, H))
    if len(paths) == 2:
        cells = [(0, 0, W // 2, H), (W // 2, 0, W, H)]
    else:
        cells = [(0, 0, W // 2, H // 2), (W // 2, 0, W, H // 2), (0, H // 2, W // 2, H), (W // 2, H // 2, W, H)]
    for path, (x0, y0, x1, y1) in zip(paths, cells):
        with Image.open(path) as source:
            canvas.paste(fill(source.convert('RGB'), x1 - x0, y1 - y0), (x0, y0))
    return canvas


def vignette():
    """A soft dark pool behind the emblem and a gentle floor shade, so white reads on any mosaic."""
    pool = Image.new('L', (W, H), 0)
    r = H * 0.42
    ImageDraw.Draw(pool).ellipse((W / 2 - r, H / 2 - r, W / 2 + r, H / 2 + r), fill=150)
    pool = pool.filter(ImageFilter.GaussianBlur(H * 0.16))
    floor = Image.linear_gradient('L').resize((W, H)).point(lambda v: int(max(0, v - 150) * 0.55))
    return pool, floor


def arrows(size):
    """Two heavy circular arrows chasing each other, drawn at SS x and returned as an L mask."""
    s = size * SS
    mask = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(mask)
    cx = cy = s / 2
    radius = s * 0.30                             # centre line of the stroke
    stroke = s * 0.125
    for start in (196, 16):                       # upper-left arc and lower-right arc
        sweep = 124
        outer = radius + stroke / 2                # PIL grows an arc's width inward from its box
        box = (cx - outer, cy - outer, cx + outer, cy + outer)
        d.arc(box, start, start + sweep, fill=255, width=round(stroke))
        # Round cap at the tail.
        tail = math.radians(start)
        tx, ty = cx + radius * math.cos(tail), cy + radius * math.sin(tail)
        d.ellipse((tx - stroke / 2, ty - stroke / 2, tx + stroke / 2, ty + stroke / 2), fill=255)
        # Arrowhead at the head, pointing along the clockwise tangent.
        head = math.radians(start + sweep)
        hx, hy = cx + radius * math.cos(head), cy + radius * math.sin(head)
        tangent = head + math.pi / 2
        normal = head
        length, half = stroke * 1.45, stroke * 1.2
        tip = (hx + length * math.cos(tangent), hy + length * math.sin(tangent))
        base_out = (hx + half * math.cos(normal), hy + half * math.sin(normal))
        base_in = (hx - half * math.cos(normal), hy - half * math.sin(normal))
        d.polygon([tip, base_out, base_in], fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def play_badge():
    """A frosted light tile with a dark play triangle, like a media thumbnail marker."""
    bw, bh = 46, 34
    s = SS
    tile = Image.new('RGBA', (bw * s, bh * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(tile)
    d.rounded_rectangle((0, 0, bw * s - 1, bh * s - 1), radius=8 * s, fill=(236, 238, 242, 205))
    cx, cy, t = bw * s / 2 + 1.5 * s, bh * s / 2, 8.5 * s
    d.polygon([(cx - t * 0.8, cy - t), (cx - t * 0.8, cy + t), (cx + t * 0.95, cy)], fill=(58, 62, 70, 235))
    return tile.resize((bw, bh), Image.LANCZOS)


def cover(paths, badge):
    base = mosaic(paths).convert('RGBA')
    pool, floor = vignette()
    black = Image.new('RGBA', (W, H), (4, 6, 12, 255))
    base = Image.composite(black, base, Image.eval(pool, lambda v: int(v * 0.85)))
    base = Image.composite(black, base, floor)

    size = round(H * 0.40)
    mask = arrows(size)
    x, y = (W - size) // 2, (H - size) // 2
    shadow = Image.new('L', (W, H), 0)
    shadow.paste(mask, (x, y + 4))
    shadow = shadow.filter(ImageFilter.GaussianBlur(9)).point(lambda v: int(v * 0.55))
    base = Image.composite(Image.new('RGBA', (W, H), (0, 0, 0, 255)), base, shadow)
    white = Image.new('RGBA', (size, size), (255, 255, 255, 255))
    base.paste(white, (x, y), mask)

    if badge:
        tile = play_badge()
        base.alpha_composite(tile, (22, H - tile.size[1] - 20))
    return base.convert('RGB')


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    previews = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    for scope, (name, files, badge) in COVERS.items():
        paths = [previews / f for f in files]
        missing = [str(p) for p in paths if not p.is_file()]
        if missing:
            sys.exit('Missing previews for %s: %s' % (scope, ', '.join(missing)))
        target = OUT / (name + '.webp')
        cover(paths, badge).save(target, 'WEBP', quality=84, method=6)
        print('%s  %d bytes' % (target.relative_to(OUT.parents[5]), target.stat().st_size))


if __name__ == '__main__':
    main()
