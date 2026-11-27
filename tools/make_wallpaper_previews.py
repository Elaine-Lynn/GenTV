#!/usr/bin/env python3
"""Render gallery previews for still wallpapers ahead of time.

For every jpg/jpeg/png/webp wallpaper in MEDIA_DIR this writes ``<name>-preview.jpg``: 640x360, centre-cropped
to 16:9, progressive JPEG. The wallpaper API (server/wallpaper/app.py) then lists that file as the item's
``preview``, so the TV gallery downloads a few dozen kilobytes per card instead of the full wallpaper, and never
lists a preview as a wallpaper of its own.

    python3 tools/make_wallpaper_previews.py MEDIA_DIR [--out DIR] [--force]

Previews go next to the wallpapers unless --out names another folder (for example a staging folder to upload
to /srv/gentv/wallpaper/media). An existing preview newer than its wallpaper is kept unless --force is given.
Requires Pillow (tools/requirements-dev.txt).
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageOps

SIZE = (640, 360)            # the gallery card is about 524 x 295 px on a 1080p TV
QUALITY = 82
SUFFIX = '-preview'
ALLOWED = {'.jpg', '.jpeg', '.png', '.webp'}


def wallpapers(media):
    for path in sorted(media.iterdir()):
        if (path.is_file() and not path.is_symlink() and not path.name.startswith('.')
                and path.suffix.lower() in ALLOWED and not path.stem.endswith(SUFFIX)):
            yield path


def render(source, target):
    with Image.open(source) as image:
        image = ImageOps.exif_transpose(image).convert('RGB')
        preview = ImageOps.fit(image, SIZE, Image.LANCZOS, centering=(0.5, 0.5))
    temporary = target.with_name('.' + target.name + '.tmp')
    preview.save(temporary, 'JPEG', quality=QUALITY, optimize=True, progressive=True)
    temporary.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('media', type=Path)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--force', action='store_true')
    args = parser.parse_args()
    if not args.media.is_dir():
        sys.exit('Not a folder: %s' % args.media)
    out = args.out or args.media
    out.mkdir(parents=True, exist_ok=True)
    made = 0
    for source in wallpapers(args.media):
        target = out / (source.stem + SUFFIX + '.jpg')
        if not args.force and target.is_file() and target.stat().st_mtime >= source.stat().st_mtime:
            continue
        render(source, target)
        made += 1
        print('%-40s %7d -> %6d bytes' % (target.name, source.stat().st_size, target.stat().st_size))
    print('%d preview(s) written to %s' % (made, out))


if __name__ == '__main__':
    main()
