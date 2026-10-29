# Contributing to GenTV

Thanks for helping. Bug reports, device reports and pull requests are welcome.

## Before you start

- For a large change, open an issue first so the approach can be agreed.
- Report security problems privately as described in [SECURITY.md](SECURITY.md), not in public issues.
- Never include TV addresses, ADB keys, backups, account details or signing material in issues, logs or commits.

## Development setup

Start with the [environment and installation guide](docs/getting-started.md), including the instructions for AI assistants.

You need JDK 17 for the TV app, JDK 21 for the phone build (with `GENTV_HOME_JAVA_HOME` pointing to JDK 17), JDK 25 for the ADB unit tests, and Android SDKs 34 and 37. No signing keys or server access are needed. See the [README](README.md#build) for the build commands.

Development builds do not contact the GenTV service. To test online features against your own server, pass `-PgentvApiBaseUrl=https://your-server/tv/` (see [server/wallpaper](server/wallpaper/README.md)).

## Before opening a pull request

Run the checks for the parts you changed:

```sh
(cd launcher && ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug)
(cd phone && ./gradlew --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
git diff --check
```

If you change files under `phone/framework/`, regenerate `phone/framework-lock.json` in the same commit and explain why.

Say in the pull request what you tested and on what: emulator, unit tests, or a real TV (model and firmware). Emulator results do not prove real-TV Home, HDMI, remote-key or recovery behaviour.

## Rules for changes

- Do not commit APKs, keystores, `keys/` contents or generated build output.
- Do not hard-code the GenTV API address; it is build configuration.
- Keep the original copyright and licence notices in `LICENSE`, `licenses/`, `phone/framework/LICENSE` and `THIRD_PARTY_NOTICES.md`.
- Device lists are community references. When adding or changing one, say which model and firmware you checked, and never mark a package safe to disable without evidence.
- Keep commit subjects short and prefixed with the area, for example `Launcher: …`, `phone: …`, `server: …`, `docs: …`.

## Licence

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
