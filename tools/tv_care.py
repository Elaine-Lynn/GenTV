#!/usr/bin/env python3
"""Build/run GenTV's local JVM maintenance adapter; all device writes are opt-in.

Example: python3 tools/tv_care.py match --serial TV_SERIAL
Only an explicitly supplied transport may be connected; no discovery or pairing.
"""
import argparse
import errno
import hashlib
import platform
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys

ROOT = Path(sys.executable if getattr(sys, 'frozen', False) else __file__).resolve().parents[1]


def sdk_roots():
    values = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT')]
    properties = ROOT / 'launcher/local.properties'
    for properties in (properties, ROOT / 'phone/local.properties'):
        if properties.exists():
            values.extend(line.split('=', 1)[1].strip().replace('\\:', ':').replace('\\\\', '\\')
                          for line in properties.read_text().splitlines() if line.startswith('sdk.dir='))
    values += [str(Path.home() / 'Library/Android/sdk'), str(Path.home() / 'Android/Sdk')]
    return [Path(value).expanduser() for value in values if value]


def tool(name, explicit=None):
    if explicit:
        path = shutil.which(explicit) or (explicit if Path(explicit).is_file() else None)
        if not path:
            raise ValueError(f'Cannot locate {name}: {explicit}')
        return str(Path(path).resolve())
    found = shutil.which(name)
    if found:
        return found
    filename = name + ('.exe' if os.name == 'nt' else '')
    for sdk in sdk_roots():
        candidates = [sdk / 'platform-tools' / filename] if name == 'adb' else sorted((sdk / 'build-tools').glob('*/' + filename), reverse=True)
        for path in candidates:
            if path.is_file():
                return str(path)
    raise ValueError(f'{name} unavailable; set its explicit path or configure the local Android SDK')


def java_env():
    env = os.environ.copy()
    if not env.get('JAVA_HOME') and sys.platform == 'darwin':
        output = subprocess.run(['/usr/libexec/java_home', '-v', '21'], capture_output=True, text=True)
        if output.returncode == 0:
            env['JAVA_HOME'] = output.stdout.strip()
    return env


def port_state(host, port, timeout=3):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return 'open'
    except ConnectionRefusedError:
        return 'closed'
    except OSError as error:
        return 'closed' if error.errno == errno.ECONNREFUSED else 'unknown'


def source_stamp():
    digest = hashlib.sha256()
    paths = list((ROOT / 'phone/tv-care/src/main').rglob('*.kt')) + list((ROOT / 'phone/framework/core/src/main').rglob('*.kt'))
    paths += [p for p in (ROOT / 'phone').glob('*.gradle.kts')]
    paths += [ROOT / name for name in ('phone/tv-care/build.gradle.kts', 'phone/framework/core/build.gradle.kts', 'phone/settings.gradle.kts', 'phone/framework/gradle/libs.versions.toml')]
    for path in sorted(paths):
        digest.update(path.relative_to(ROOT).as_posix().encode()); digest.update(path.read_bytes())
    return digest.hexdigest()


def checked_manifest(path, base):
    data = json.loads(path.read_text())
    for name, expected in data['sha256'].items():
        member = (base / name).resolve()
        if not member.is_relative_to(base.resolve()) or not member.is_file() or hashlib.sha256(member.read_bytes()).hexdigest() != expected:
            raise ValueError('Runtime integrity mismatch: ' + name)
    return data


def ensure_runtime(force=False):
    bundle = ROOT / 'maintenance-bundle.json'
    if bundle.is_file():
        data = checked_manifest(bundle, ROOT)
        if data['platform'] != platform.system() or data['architecture'] != platform.machine():
            raise ValueError('Use the maintenance bundle for this OS and architecture')
        env = os.environ.copy(); env['JAVA_HOME'] = str(ROOT / 'runtime')
        return ROOT / data['launcher'], env
    distribution = ROOT / 'phone/tv-care/build/install/tv-care'
    receipt = distribution / 'gentv-runtime.json'
    stamp = source_stamp()
    env = java_env()
    if receipt.is_file() and not force:
        try:
            data = checked_manifest(receipt, distribution)
            if data['source_stamp'] == stamp:
                return distribution / data['launcher'], env
        except (ValueError, OSError, KeyError):
            pass  # A stale or changed distribution is rebuilt, never silently used.
    gradle = ROOT / 'phone' / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    subprocess.run([str(gradle), '-p', str(ROOT / 'phone'), '--no-daemon', '--console=plain', ':tv-care:installDist'], env=env, check=True, stdout=sys.stderr)
    launcher = 'bin/' + ('tv-care.bat' if os.name == 'nt' else 'tv-care')
    hashes = {p.relative_to(distribution).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in distribution.rglob('*') if p.is_file() and p != receipt}
    receipt.write_text(json.dumps({'source_stamp': stamp, 'launcher': launcher, 'sha256': hashes}, indent=2) + '\n')
    return distribution / launcher, env


def setup_transport():
    if getattr(sys, 'frozen', False):
        executable = ROOT / 'bin' / ('gentv-transport.exe' if os.name == 'nt' else 'gentv-transport')
        if not executable.is_file(): raise ValueError('Bundled transport missing')
        return str(executable)
    directory = Path.home() / '.cache/gentv-tv-care/transport-python'
    python = directory / ('Scripts/python.exe' if os.name == 'nt' else 'bin/python')
    if not python.is_file():
        subprocess.run([sys.executable, '-m', 'venv', str(directory)], check=True)
    ready = subprocess.run([str(python), '-c', 'import adb_shell'], capture_output=True)
    if ready.returncode:
        subprocess.run([str(python), '-m', 'pip', 'install', '--require-hashes', '-r', str(ROOT / 'tools/requirements-tv-transport.txt')], check=True, stdout=sys.stderr)
    return str(python)


def select_transport(mode, serial, explicit=None):
    native = None
    if mode != 'python':
        try:
            native = tool('adb', explicit)
        except ValueError:
            if mode == 'native' or explicit:
                raise
    if native and mode == 'auto' and serial:
        state = subprocess.run([native, '-s', serial, 'get-state'], capture_output=True, text=True, timeout=15)
        message = (state.stdout + state.stderr).lower()
        if 'unauthorized' in message or 'authentication' in message:
            raise ValueError('Accept debugging authorisation on the TV; fallback cannot bypass it')
        # Connect only the user-supplied TCP endpoint, before any device mutation.
        if state.returncode and ("not found" in message or "offline" in message):
            from tv_transport import endpoint
            endpoint(serial)
            connected = subprocess.run([native, 'connect', serial], capture_output=True, text=True, timeout=20)
            state = subprocess.run([native, '-s', serial, 'get-state'], capture_output=True, text=True, timeout=15)
            message = (connected.stdout + connected.stderr + state.stdout + state.stderr).lower()
            if any(value in message for value in ('unauthorized', 'authentication', 'failed to authenticate', 'tls')):
                raise ValueError('Complete native debugging authorisation/pairing on the TV; no fallback or TLS downgrade')
        # Never replay a mutation; unresolved offline states require device-side action.
        if state.returncode and any(value in message for value in ('no route to host', 'timed out', 'cannot connect', 'failed to connect')) and 'offline' not in message:
            native = None
    if native:
        return [native], []
    if not serial:
        raise ValueError('Specify the authorised TV transport with --serial')
    from tv_transport import endpoint
    endpoint(serial)
    executable = ROOT / 'bin' / ('gentv-transport.exe' if os.name == 'nt' else 'gentv-transport')
    if executable.is_file():
        return [str(executable)], []
    python = setup_transport()
    return [str(ROOT / 'tools/tv_transport.py')], ['--adb-python', python]


def main():
    if sys.argv[1:2] == ['ports']:
        parser = argparse.ArgumentParser(description='Check explicit known TV ports; unreachable is not closed')
        parser.add_argument('command'); parser.add_argument('--host', required=True); parser.add_argument('--ports', required=True)
        args = parser.parse_args()
        ports = [int(value) for value in args.ports.split(',')]
        if not ports or any(port not in range(1, 65536) for port in ports): parser.error('Invalid port')
        report = {port: port_state(args.host, port) for port in ports}
        print(json.dumps({'ports': report}))
        return 0 if all(state == 'closed' for state in report.values()) else 2
    parser = argparse.ArgumentParser(description=__doc__, add_help=False)
    parser.add_argument('--adb'); parser.add_argument('--aapt')
    parser.add_argument('--transport', choices=('auto', 'native', 'python'), default='auto')
    parser.add_argument('--setup-transport', action='store_true')
    parser.add_argument('--prepare-runtime', action='store_true')
    parser.add_argument('--rebuild', action='store_true')
    options, remaining = parser.parse_known_args()
    os.umask(0o077)
    if options.setup_transport:
        setup_transport(); print('Independent ADB transport ready'); return 0
    if options.prepare_runtime:
        ensure_runtime(options.rebuild); print('GenTV maintenance runtime ready'); return 0
    if not remaining or remaining == ['--help']: remaining = ['help']
    command = remaining[0]
    if command != 'help':
        serial = remaining[remaining.index('--serial') + 1] if '--serial' in remaining else None
        selected, extra = select_transport(options.transport, serial, options.adb)
        remaining += ['--adb', selected[0], '--repo', str(ROOT), *extra]
    executable, env = ensure_runtime(options.rebuild)
    if (ROOT / 'maintenance-bundle.json').is_file():
        java = ROOT / 'runtime/bin' / ('java.exe' if os.name == 'nt' else 'java')
        return subprocess.call([str(java), '-cp', str(ROOT / 'maintenance/lib/*'), 'com.gentv.care.MainKt', *remaining], env=env)
    return subprocess.call([str(executable), *remaining], env=env)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyError, subprocess.SubprocessError) as error:
        raise SystemExit(f'GenTV maintenance stopped: {error}')
