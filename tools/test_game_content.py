import tempfile
from pathlib import Path
import unittest
import zipfile
from package_game_content import validate_contents


class GameContentPackageTest(unittest.TestCase):
    def test_rejects_engine_and_unprefixed_resources(self):
        for extra in ['lib/armeabi-v7a/libxul.so', 'assets/omni.ja', 'assets/remote.html']:
            with tempfile.TemporaryDirectory() as folder:
                apk = Path(folder) / 'game.apk'
                with zipfile.ZipFile(apk, 'w') as archive:
                    for name in ['classes.dex', 'assets/game_kart_remote.html', extra]: archive.writestr(name, b'example')
                with self.assertRaises(ValueError): validate_contents(apk, 'kart')

    def test_accepts_independent_small_game(self):
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / 'game.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('classes.dex', b'example')
                archive.writestr('assets/game_kart_remote.html', b'example')
            validate_contents(apk, 'kart')
