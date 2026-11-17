import unittest
from unittest.mock import Mock, patch
import tv_transport
import tv_care

class TransportTest(unittest.TestCase):
    def test_explicit_private_endpoint_required(self):
        self.assertEqual(tv_transport.endpoint('192.168.1.2:5556'), ('192.168.1.2', 5556))
        self.assertEqual(tv_transport.endpoint('[fd00::1]:1234'), ('fd00::1', 1234))
        for value in ('192.168.1.2', '192.168.1.2:0', '8.8.8.8:5555', 'emulator-5554'):
            with self.assertRaises(ValueError): tv_transport.endpoint(value)

    def test_binary_output_is_preserved(self):
        device = Mock(); device.shell.return_value = b'\x89PNG\r\n\x00\xff'
        self.assertEqual(tv_transport.dispatch(device, ['exec-out', 'screencap', '-p']), device.shell.return_value)
        device.shell.assert_called_once_with('screencap -p', decode=False)

    def test_unknown_proxy_operations_are_refused(self):
        with self.assertRaises(ValueError): tv_transport.dispatch(Mock(), ['pair', 'secret'])

    def test_denied_authorisation_does_not_fallback(self):
        result = Mock(returncode=1, stdout='', stderr='device unauthorized')
        with patch('tv_care.tool', return_value='/fixture/adb'), patch('tv_care.subprocess.run', return_value=result), patch('tv_care.setup_transport') as setup:
            with self.assertRaises(ValueError): tv_care.select_transport('auto', '192.168.1.2:5555')
            setup.assert_not_called()

    def test_explicit_endpoint_is_connected_before_fallback(self):
        missing = Mock(returncode=1, stdout='', stderr="error: device '192.168.1.2:5555' not found")
        connected = Mock(returncode=0, stdout='connected', stderr='')
        ready = Mock(returncode=0, stdout='device', stderr='')
        with patch('tv_care.tool', return_value='/fixture/adb'), patch('tv_care.subprocess.run', side_effect=[missing, connected, ready]) as run, patch('tv_care.setup_transport') as setup:
            self.assertEqual(tv_care.select_transport('auto', '192.168.1.2:5555'), (['/fixture/adb'], []))
            self.assertEqual(run.call_args_list[1].args[0], ['/fixture/adb', 'connect', '192.168.1.2:5555'])
            setup.assert_not_called()

if __name__ == '__main__': unittest.main()
