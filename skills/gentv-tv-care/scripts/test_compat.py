"""Local lookup tests; no network, TV or installed SDK required."""
from pathlib import Path
import tempfile
import unittest
import compat


class LocalLookupTest(unittest.TestCase):
    def make_repo(self, directory):
        root = Path(directory).resolve()
        for name in ('AGENTS.md', 'docs/getting-started.md', compat.CAPABILITIES['cleanup']):
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('GenTV')
        return root

    def test_incomplete_checkout_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                compat.checkout(directory)

    def test_local_search_and_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.make_repo(directory)
            record = root / 'phone/framework/data/devices/example/debloat.txt'
            record.parent.mkdir(parents=True)
            record.write_text('# TCL model G05\ncom.example.ads\n')
            self.assertEqual(compat.checkout(directory), root.resolve())
            self.assertEqual(compat.inspect(root)['device_records'], 1)
            self.assertEqual(compat.search(root, 'g05'), [{'path': str(record), 'kind': 'reference'}])
            self.assertEqual(compat.search(root, 'absent-model'), [])
            with self.assertRaises(ValueError):
                compat.search(root, ' ')

    def test_external_symlink_not_searched(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = self.make_repo(directory)
            target = Path(outside) / 'secret.txt'
            target.write_text('needle')
            link = root / 'docs/devices/link.txt'
            link.parent.mkdir(parents=True)
            link.symlink_to(target)
            self.assertEqual(compat.search(root, 'needle'), [])


if __name__ == '__main__':
    unittest.main()
