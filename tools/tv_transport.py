#!/usr/bin/env python3
"""SDK-independent ADB transport for an explicit legacy TCP endpoint.

Implements only the local maintenance adapter's get-state/shell/exec-out/push/pull
operations. Encrypted Wireless Debugging remains on the native ADB path; this
client never changes a TV's debugging mode or guesses a port.
"""
import argparse
import ipaddress
import os
from pathlib import Path
import shlex
import sys


def endpoint(serial):
    if serial.startswith('['):
        host, separator, tail = serial[1:].partition(']:')
    else:
        host, separator, tail = serial.rpartition(':')
    if not separator or not tail.isdecimal() or not 1 <= int(tail) <= 65535:
        raise ValueError('Python transport needs the explicitly known HOST:PORT (IPv6: [HOST]:PORT)')
    address = ipaddress.ip_address(host)
    if address.is_global or address.is_unspecified or address.is_multicast:
        raise ValueError('Use an authorised local/private VPN endpoint, never public ADB')
    return host, int(tail)


def connect(serial):
    from adb_shell.adb_device import AdbDeviceTcp
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner
    host, port = endpoint(serial)
    key = Path(os.environ.get('GENTV_ADB_KEY', str(Path.home() / '.android/adbkey')))
    os.umask(0o077)
    if not key.exists():
        key.parent.mkdir(parents=True, exist_ok=True)
        keygen(str(key))
    device = AdbDeviceTcp(host, port, default_transport_timeout_s=15)
    def approval(_):
        print('Approve the debugging authorisation on the specified TV. No code or key is logged.', file=sys.stderr)
    device.connect(rsa_keys=[PythonRSASigner.FromRSAKeyPath(str(key))], auth_timeout_s=30, auth_callback=approval)
    return device


def dispatch(device, operation):
    command, *args = operation
    if command == 'get-state' and not args:
        return b'device\n'
    if command == 'shell' and len(args) == 1:
        return device.shell(args[0], decode=False)
    if command == 'exec-out' and args == ['screencap', '-p']:
        return device.shell('screencap -p', decode=False)
    if command in ('push', 'pull') and len(args) == 2:
        if command == 'push':
            device.push(args[0], args[1])
        else:
            device.pull(args[0], args[1])
        return b''
    raise ValueError('Unsupported maintenance transport operation')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('-s', '--serial', required=True)
    parser.add_argument('operation', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    endpoint(args.serial)  # Validate before loading a dependency or generating any key.
    if not args.operation:
        parser.error('Missing operation')
    device = connect(args.serial)
    try:
        output = dispatch(device, args.operation)
        sys.stdout.buffer.write(output if isinstance(output, bytes) else output.encode())
    finally:
        device.close()


if __name__ == '__main__':
    try:
        main()
    except ImportError:
        raise SystemExit('Python ADB dependency unavailable; run tools/tv_care.py --setup-transport')
    except Exception as error:
        # Third-party exception strings may include endpoint/authentication details.
        raise SystemExit('Python ADB stopped (' + type(error).__name__ + '); verify endpoint, authorisation and legacy TCP support. TLS pairing requires native ADB.')
