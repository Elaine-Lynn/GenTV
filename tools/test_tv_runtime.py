import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import tv_care
class RuntimeTest(unittest.TestCase):
    def test_manifest_rejects_tampered_files_and_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); binary = root/'tool'; binary.write_bytes(b'good')
            manifest = root/'receipt.json'; manifest.write_text(json.dumps({'sha256': {'tool': hashlib.sha256(b'good').hexdigest()}}))
            tv_care.checked_manifest(manifest, root)
            binary.write_bytes(b'bad')
            with self.assertRaises(ValueError): tv_care.checked_manifest(manifest, root)
            manifest.write_text(json.dumps({'sha256': {'../escape': '0'*64}}))
            with self.assertRaises(ValueError): tv_care.checked_manifest(manifest, root)

    def test_bundle_uses_its_runtime_without_gradle_or_java_discovery(self):
        import platform
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); launcher = root/'maintenance/bin/tv-care'; launcher.parent.mkdir(parents=True); launcher.write_bytes(b'fixture')
            data = {'platform': platform.system(), 'architecture': platform.machine(), 'launcher':'maintenance/bin/tv-care','sha256':{'maintenance/bin/tv-care':hashlib.sha256(b'fixture').hexdigest()}}
            (root/'maintenance-bundle.json').write_text(json.dumps(data))
            with patch('tv_care.ROOT', root), patch('tv_care.subprocess.run') as run, patch('tv_care.java_env') as env:
                executable, environment = tv_care.ensure_runtime()
                self.assertEqual(executable, launcher); self.assertEqual(environment['JAVA_HOME'], str(root/'runtime'))
                run.assert_not_called(); env.assert_not_called()
if __name__ == '__main__': unittest.main()
