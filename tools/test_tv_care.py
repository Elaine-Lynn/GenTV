"""Offline transport outcomes plus loopback relay integration."""
import asyncio
import socket
import unittest
from unittest.mock import patch
import tv_care
import tv_relay


class PortChecks(unittest.TestCase):
    def test_refused_is_closed(self):
        with patch('tv_care.socket.create_connection', side_effect=ConnectionRefusedError()):
            self.assertEqual(tv_care.port_state('192.0.2.1', 5555), 'closed')

    def test_timeout_and_unreachable_are_unknown(self):
        for error in (socket.timeout(), OSError(65, 'No route to host')):
            with patch('tv_care.socket.create_connection', side_effect=error):
                self.assertEqual(tv_care.port_state('192.0.2.1', 5555), 'unknown')

    def test_open_socket_is_not_closed(self):
        with patch('tv_care.socket.create_connection'):
            self.assertEqual(tv_care.port_state('192.0.2.1', 5555), 'open')


class RelayTests(unittest.IsolatedAsyncioTestCase):
    async def test_bidirectional_binary_and_half_close(self):
        async def responder(reader, writer):
            content = await reader.read()
            writer.write(content[::-1]); await writer.drain()
            writer.close(); await writer.wait_closed()
        target = await asyncio.start_server(responder, '127.0.0.1', 0)
        port = target.sockets[0].getsockname()[1]
        proxy = await asyncio.start_server(lambda r, w: tv_relay.relay(r, w, '127.0.0.1', port), '127.0.0.1', 0)
        try:
            reader, writer = await asyncio.open_connection('127.0.0.1', proxy.sockets[0].getsockname()[1])
            payload = bytes(range(256)) * 1000
            writer.write(payload); await writer.drain(); writer.write_eof()
            answer = await asyncio.wait_for(reader.read(), 3)
            self.assertEqual(answer, payload[::-1])
            writer.close(); await writer.wait_closed()
        finally:
            proxy.close(); target.close()
            await proxy.wait_closed(); await target.wait_closed()


if __name__ == '__main__':
    unittest.main()
