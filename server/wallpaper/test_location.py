import io
import json
import os
import unittest
from unittest.mock import patch
import app
import location


class LocationTest(unittest.TestCase):
    def setUp(self):
        location._CACHE.clear()
        self.valid = {'status': '1', 'infocode': '10000', 'province': '上海市', 'city': '上海市', 'adcode': '310000'}

    def request(self, **extra):
        result = {}
        env = {'PATH_INFO': '/tv/location', 'REQUEST_METHOD': 'GET', 'REMOTE_ADDR': '8.8.4.4', **extra}
        body = b''.join(app.app(env, lambda status, headers: result.update(status=status, headers=dict(headers))))
        return result, json.loads(body) if body else {}

    def test_missing_key_and_method(self):
        with patch.dict(os.environ, {'AMAP_WEB_SERVICE_KEY': ''}):
            self.assertEqual(self.request()[0]['status'], '503 Service Unavailable')
        self.assertEqual(self.request(REQUEST_METHOD='POST')[0]['status'], '405 Method Not Allowed')

    def test_only_trusted_proxy_can_supply_public_ipv4(self):
        self.assertEqual(location.source_ip({'REMOTE_ADDR': '8.8.8.8', 'HTTP_X_GENTV_PEER': '1.1.1.1'}), '8.8.8.8')
        self.assertEqual(location.source_ip({'REMOTE_ADDR': '127.0.0.1', 'HTTP_X_GENTV_PEER': '1.1.1.1'}), '1.1.1.1')
        for value in ('10.0.0.1', '127.0.0.1', '::1', '2001:4860:4860::8888', 'bad'):
            with self.assertRaises(ValueError):
                location.source_ip({'REMOTE_ADDR': value})

    def test_success_cache_and_no_ip_or_key_in_response(self):
        with patch.dict(os.environ, {'AMAP_WEB_SERVICE_KEY': 'test-key'}), \
                patch.object(location, 'urlopen', side_effect=lambda *a, **kw: io.BytesIO(json.dumps(self.valid).encode())) as upstream:
            status, body = self.request()
            self.assertEqual(status['status'], '200 OK')
            self.assertEqual(body['city'], '上海市')
            self.assertEqual(status['headers']['Cache-Control'], 'no-store')
            self.request()
            self.assertEqual(upstream.call_count, 1)
            url = upstream.call_args[0][0].full_url
            self.assertIn('ip=8.8.4.4', url)
            self.assertIn('key=test-key', url)
            self.assertEqual(set(body), {'province', 'city', 'adcode', 'source'})

    def test_upstream_errors_are_sanitized_and_not_cached(self):
        with patch.dict(os.environ, {'AMAP_WEB_SERVICE_KEY': 'test-key'}), \
                patch.object(location, 'urlopen', side_effect=OSError('secret provider url')):
            status, body = self.request()
            self.assertEqual(status['status'], '503 Service Unavailable')
            self.assertEqual(body, {'error': 'location_unavailable'})
            self.assertFalse(location._CACHE)

    def test_empty_city_and_provider_failure_are_rejected(self):
        for payload in ({'status': '0', 'info': 'INVALID_USER_KEY'}, {**self.valid, 'city': []},
                        {**self.valid, 'adcode': 'bad'}):
            with patch.object(location, 'urlopen', return_value=io.BytesIO(json.dumps(payload).encode())):
                with self.assertRaises(ValueError):
                    location.lookup('1.1.1.1', 'test-key')
