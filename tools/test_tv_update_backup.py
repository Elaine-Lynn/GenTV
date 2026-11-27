import hashlib
from pathlib import Path
import tempfile
import unittest
from tv_update_backup import backup_manifest, prepare, CERT


class TvUpdateBackupTest(unittest.TestCase):
    def metadata(self, data=b'apk'):
        return dict(versionCode=40, versionName='3.7.18', packageName='com.gentv.tv',
                    sha256=hashlib.sha256(data).hexdigest(), sizeBytes=len(data), minSdk=17,
                    signingCertificateSha256=CERT, forceUpdate=False, minSupportedVersionCode=0)

    def test_backup_has_independent_download_and_same_identity(self):
        source = self.metadata()
        source['url'] = 'https://primary.example/tv/releases/gentv-tv-3.7.18.apk'
        result = backup_manifest(source)
        self.assertEqual(result['url'], 'https://github.com/Elaine-Lynn/GenTV/releases/download/v3.7.18/gentv-tv-v3.7.18-code40.apk')
        for key in ('versionCode', 'sha256', 'signingCertificateSha256', 'minSdk', 'sizeBytes'):
            self.assertEqual(result[key], source[key])
        self.assertNotEqual(result['url'], source['url'])

    def test_prepare_refuses_mismatched_bytes_and_different_versioned_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            apk = root / 'app.apk'
            apk.write_bytes(b'apk')
            result, files = prepare(apk, self.metadata(), root / 'out')
            self.assertTrue(files[1].is_file())
            self.assertIn('config.json', files[2].read_text())
            files[0].write_bytes(b'changed')
            with self.assertRaises(ValueError): prepare(apk, self.metadata(), root / 'out')
            with self.assertRaises(ValueError): prepare(apk, self.metadata(b'other'), root / 'elsewhere')

    def test_rejects_nonofficial_identity(self):
        for key, value in [('packageName', 'other.app'), ('signingCertificateSha256', '0' * 64),
                           ('versionName', '../other'), ('versionCode', 0), ('sizeBytes', 0)]:
            metadata = self.metadata()
            metadata[key] = value
            with self.assertRaises(ValueError): backup_manifest(metadata)
