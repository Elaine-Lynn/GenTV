#!/usr/bin/env python3
"""Loopback-only ADB byte relay to one explicitly supplied TV and port.

Run in an ordinary local terminal if the assistant's ADB process lacks local
network access. Pairing/authorisation still belongs to the owner. Never exposes
ADB to the public network. Not a remedy for an unauthorised or unsupported TV.
"""
import argparse
import asyncio
import ipaddress


async def copy_stream(reader, writer):
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
        if writer.can_write_eof():
            writer.write_eof()
    except (ConnectionError, OSError):
        pass


async def relay(reader, writer, host, port):
    remote = None
    try:
        remote_reader, remote = await asyncio.wait_for(asyncio.open_connection(host, port), 5)
        await asyncio.gather(copy_stream(reader, remote), copy_stream(remote_reader, writer))
    except (OSError, asyncio.TimeoutError):
        pass
    finally:
        for stream in (writer, remote):
            if stream is not None:
                stream.close()
                try:
                    await stream.wait_closed()
                except (OSError, ConnectionError):
                    pass


async def serve(host, port, listen_port):
    server = await asyncio.start_server(lambda reader, writer: relay(reader, writer, host, port), '127.0.0.1', listen_port)
    print(f'Loopback relay ready: 127.0.0.1:{server.sockets[0].getsockname()[1]}. Stop with Ctrl-C after maintenance.', flush=True)
    async with server:
        await server.serve_forever()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', required=True, help='Explicit private TV or private VPN IP; no hostname scanning')
    parser.add_argument('--port', required=True, type=int, help='Known device ADB connection port')
    parser.add_argument('--listen-port', type=int, default=0, help='0 chooses a free local port and prints it')
    args = parser.parse_args()
    address = ipaddress.ip_address(args.host)
    if address.is_unspecified or address.is_multicast or address.is_loopback or address.is_global:
        parser.error('Use the explicitly authorised private TV/VPN IP, never a public address')
    if not 1 <= args.port <= 65535 or not 0 <= args.listen_port <= 65535:
        parser.error('Invalid port')
    try:
        asyncio.run(serve(args.host, args.port, args.listen_port))
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
