"""Prepare/publish the TV APK and independent config.json on GitHub. No signing secrets uploaded."""
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.request import Request, urlopen
from urllib.parse import urlparse

REPO = 'Elaine-Lynn/GenTV'
BRANCH = 'update-manifest'
CONFIG_URL = f'https://raw.githubusercontent.com/{REPO}/{BRANCH}/config.json'
CERT = '8d5b5378f9056880f3036d44bc2f4a48322141fbb8d8bc91951d61a4652d997f'


def digest(file):
    value = hashlib.sha256()
    with Path(file).open('rb') as source:
        for block in iter(lambda: source.read(65536), b''):
            value.update(block)
    return value.hexdigest()


def backup_manifest(metadata):
    data = dict(metadata)
    version = data['versionName']
    code = data['versionCode']
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or type(code) is not int or code <= 0:
        raise ValueError('Invalid release version')
    if data['packageName'] != 'com.gentv.tv' or data['signingCertificateSha256'].lower() != CERT:
        raise ValueError('Unexpected release identity or signer')
    if not re.fullmatch(r'[a-fA-F0-9]{64}', data['sha256']) or not 0 < data['sizeBytes'] <= 128 * 1024 * 1024:
        raise ValueError('Invalid release content metadata')
    name = f'gentv-tv-v{version}-code{code}.apk'
    data['url'] = f'https://github.com/{REPO}/releases/download/v{version}/{name}'
    return data


def prepare(apk, metadata, output, license_bundle=None):
    data = backup_manifest(metadata)
    if digest(apk) != data['sha256'] or Path(apk).stat().st_size != data['sizeBytes']:
        raise ValueError('APK differs from verified release metadata')
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    target = output / data['url'].rsplit('/', 1)[1]
    if target.exists() and digest(target) != data['sha256']:
        raise ValueError('Refusing to overwrite a different versioned APK')
    if not target.exists():
        shutil.copyfile(apk, target)
    config = output / 'config.json'
    config.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    assets = [target, config]
    if license_bundle is not None:
        source = Path(license_bundle)
        if not source.is_file():
            raise ValueError('Build the release distribution with its licence notices first')
        licensed = output / f"gentv-tv-v{data['versionName']}-code{data['versionCode']}-licenses.zip"
        if licensed.exists() and digest(licensed) != digest(source):
            raise ValueError('Refusing to overwrite different release distribution bytes')
        if not licensed.exists(): shutil.copyfile(source, licensed)
        assets.append(licensed)
    sums = output / 'SHA256SUMS.txt'
    sums.write_text(''.join(f'{digest(f)}  {f.name}\n' for f in assets), encoding='ascii')
    return data, (target, config, sums, *assets[2:])


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True, encoding='utf-8')


def api_optional(path):
    result = subprocess.run(['gh', 'api', path], capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        if '404' in result.stderr:
            return None
        raise RuntimeError('GitHub API failed; check gh authentication and repository permissions')
    return json.loads(result.stdout)


def verify_public_apk(metadata):
    request = Request(metadata['url'], headers={'User-Agent': 'GenTV-Release/1'})
    with urlopen(request, timeout=30) as response:
        final = urlparse(response.geturl())
        if final.scheme != 'https' or final.hostname not in ('github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com'):
            raise ValueError('Unexpected public APK download host')
        size = 0
        value = hashlib.sha256()
        for block in iter(lambda: response.read(65536), b''):
            size += len(block)
            if size > metadata['sizeBytes']:
                raise ValueError('Public APK is larger than expected')
            value.update(block)
    if size != metadata['sizeBytes'] or value.hexdigest() != metadata['sha256']:
        raise ValueError('Public APK checksum mismatch')


def publish(metadata, files):
    if not shutil.which('gh'):
        raise RuntimeError('Install GitHub CLI and authenticate (or set GH_TOKEN) before publishing')
    path = f'repos/{REPO}/contents/config.json'
    current = api_optional(path + '?ref=' + BRANCH)
    if current:
        old = json.loads(base64.b64decode(current['content']))
        if old['versionCode'] > metadata['versionCode']:
            raise ValueError('Refusing to downgrade the GitHub update channel')
        if old['versionCode'] == metadata['versionCode'] and old.get('sha256') != metadata['sha256']:
            raise ValueError('Same version must keep the same APK bytes')
    tag = 'v' + metadata['versionName']
    release = api_optional(f'repos/{REPO}/releases/tags/{tag}')
    if release is None:
        target = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        gh('release', 'create', tag, '--repo', REPO, '--draft', '--target', target,
           '--title', 'GenTV TV ' + tag, '--notes', 'GenTV TV official signed release. See repository release notes.')
        release = json.loads(gh('api', f'repos/{REPO}/releases/tags/{tag}'))
    assets = {asset['name']: asset for asset in release['assets']}
    for file in files:
        if file.name in assets:
            asset = assets[file.name]
            if asset.get('digest') == 'sha256:' + digest(file) and asset['size'] == file.stat().st_size:
                continue
            # Older GitHub assets may not report a digest; compare actual bytes without clobbering.
            with tempfile.TemporaryDirectory() as temp:
                gh('release', 'download', tag, '--repo', REPO, '--pattern', file.name, '--dir', temp)
                if digest(Path(temp) / file.name) != digest(file):
                    raise ValueError('Refusing to overwrite a different published release asset: ' + file.name)
            continue
        gh('release', 'upload', tag, str(file), '--repo', REPO)
    if release['draft']:
        gh('release', 'edit', tag, '--repo', REPO, '--draft=false', '--latest')
    # Do not advertise a release until an anonymous download verifies successfully.
    verify_public_apk(metadata)
    payload = {'message': 'server: Publish TV update backup ' + tag, 'branch': BRANCH,
               'content': base64.b64encode(files[1].read_bytes()).decode('ascii')}
    if current:
        payload['sha'] = current['sha']
    with tempfile.TemporaryDirectory() as temp:
        body = Path(temp) / 'request.json'
        body.write_text(json.dumps(payload), encoding='utf-8')
        gh('api', '--method', 'PUT', path, '--input', str(body))
    print('GitHub TV backup published: ' + CONFIG_URL)
