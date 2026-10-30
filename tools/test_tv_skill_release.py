import hashlib
import importlib.util
import tempfile
from pathlib import Path
import unittest
import zipfile
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('gentv_setup', Path(__file__).resolve().parents[1] / 'skills/gentv-tv-care/scripts/setup.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class ReleaseTest(unittest.TestCase):
    def test_workspace_mismatch_and_traversal_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); (root / 'file').write_bytes(b'ok')
            lock = {'workspace_sha256': {'file': hashlib.sha256(b'ok').hexdigest()}}
            self.assertEqual(setup.check(root, lock), root.resolve())
            (root / 'file').write_bytes(b'changed')
            with self.assertRaises(ValueError): setup.check(root, lock)
            with self.assertRaises(ValueError): setup.check(root, {'workspace_sha256': {'../file': 'x'}})

    def test_unpack_checks_archive_and_preserves_executable(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); archive = root / 'runtime.zip'
            with zipfile.ZipFile(archive, 'w') as z:
                member = zipfile.ZipInfo('runtime/tool'); member.external_attr = 0o100755 << 16
                z.writestr(member, b'ok')
            lock = {'runtime': {'directory': 'runtime', 'sha256': setup.digest(archive)}, 'workspace_sha256': {'tool': hashlib.sha256(b'ok').hexdigest()}}
            with self.assertRaises(ValueError): setup.unpack(archive, root / 'bad', {**lock, 'runtime': {**lock['runtime'], 'sha256': 'x'}})
            self.assertFalse((root / 'bad').exists())
            installed = setup.unpack(archive, root / 'new', lock)
            self.assertTrue((installed / 'tool').stat().st_mode & 0o111)
            with self.assertRaises(ValueError): setup.unpack(archive, root / 'new', lock)

    def test_unsafe_zip_refused_before_creating_destination(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); archive = root / 'runtime.zip'
            with zipfile.ZipFile(archive, 'w') as z: z.writestr('../escape', b'bad')
            lock = {'runtime': {'directory': 'runtime', 'sha256': setup.digest(archive)}, 'workspace_sha256': {}}
            with self.assertRaises(ValueError): setup.unpack(archive, root / 'new', lock)
            self.assertFalse((root / 'new').exists())

    def test_platform_selection_is_exact(self):
        first = {'platform':'Darwin','architecture':'arm64','file':'a'}
        second = {'platform':'Windows','architecture':'AMD64','file':'b'}
        with patch.object(setup.platform, 'system', return_value='Darwin'), patch.object(setup.platform, 'machine', return_value='arm64'):
            self.assertEqual(setup.select_runtime({'runtimes':[first,second]})['runtime'], first)
            with self.assertRaises(ValueError): setup.select_runtime({'runtimes':[second]})

    def test_download_rejects_non_release_url_before_network(self):
        with tempfile.TemporaryDirectory() as d, patch.object(setup.urllib.request, 'build_opener') as network:
            with self.assertRaises(ValueError): setup.download({'release_url':'https://example.com','runtime':{'file':'x'}}, Path(d)/'new')
            network.assert_not_called()
