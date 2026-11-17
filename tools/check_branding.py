#!/usr/bin/env python3
"""Guard GenTV branding while preserving licences and real device identities."""
from pathlib import Path
import os
import re

root = Path(__file__).resolve().parents[1]
skip = {'.claude', '.git', '.gradle', '.kotlin', 'build', 'out', 'work', 'shots', 'keys', '.idea', '__pycache__'}
legal = {'LICENSE', 'THIRD_PARTY_NOTICES.md', 'phone/framework/LICENSE'}
# These skill sources explicitly identify pinned third-party compatibility references.
# The exemption does not apply to Android app branding or any retired identity checks.
compatibility_references = {
    'skills/gentv-tv-care/references/upstream.json',
    'skills/gentv-tv-care/references/device-supplements.json',
}
errors = []
checked = 0
for directory, names, files in os.walk(root):
    names[:] = [name for name in names if name not in skip]
    for name in files:
        path = Path(directory) / name
        relative = path.relative_to(root).as_posix()
        if relative == 'tools/check_branding.py':
            continue
        try:
            source = path.read_text()
        except (UnicodeError, OSError):
            continue
        checked += 1
        if re.search(r'AS' + r'Launcher|dev\.tidytv|net\.neatv\.app|me\.gentv\.(?:remote|home)|WinkleFeedback', source, re.I):
            errors.append(relative + ': retired identity')
        if re.search(r'\bcom\.gentv\.me\b|Theme\.TvHome|\bTvHome(?:Cards|\.)', source):
            errors.append(relative + ': retired desktop identity')
        # A few inherited device lists keep upstream commands next to safety notes; nothing else may.
        if relative not in compatibility_references and not relative.startswith('phone/framework/data/devices/') and re.search(
                r'tools/(?:disable|restore|theme)\.(?:sh|ps1)|tools/(?:tv|race_calendar)\.py|build-and-install\.sh|[A-Z]+_KEY_SERVICES?=1|PROMPT\.md|test_examples\.py|reference desktop', source):
            errors.append(relative + ': upstream repository path')
        exempt = relative in legal or relative.startswith('licenses/')
        compatibility = relative in compatibility_references
        if not exempt and not compatibility and re.search(r'tidytv|neatv|cleanyourtv|com\.(?:ansub|gathin)\.tvhome|tv-detox', source, re.I):
            errors.append(relative + ': old project branding')
        if re.search(r'mascot_(?:stand|search|happy|cheer|worried)', name):
            errors.append(relative + ': inherited artwork')
assert not errors, '\n'.join(errors)
print(f'PASS: {checked} text files; retired identities absent; licences scoped; compatibility references scoped')
