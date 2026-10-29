# Contributing to GenTV

Thanks for helping. Bug reports, device reports and pull requests are welcome.

Please follow the [code of conduct](CODE_OF_CONDUCT.md).

## Report a device

Real-device results are the most useful contribution and need no code. Open a [device report](https://github.com/Elaine-Lynn/GenTV/issues/new?template=device_report.yml) with what worked on your TV and what didn't.

## Before you start

- For a large change, open an issue first so the approach can be agreed.
- Report security problems privately as described in [SECURITY.md](SECURITY.md), not in public issues.
- Never include TV addresses, ADB keys, backups, account details or signing material in issues, logs or commits.

## Development setup

Start with the [environment and installation guide](docs/getting-started.md), including the instructions for AI assistants.

No signing keys or server access are needed. See [AGENTS.md](AGENTS.md) if you work with a coding assistant. Run these from the repository root.

TV (JDK 17, Android SDK 34, build-tools 34.0.0):

```sh
./launcher/gradlew -p launcher --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Phone (JDK 21 for Gradle, Android SDK 37, JDK 25 for the ADB unit tests). Set `GENTV_HOME_JAVA_HOME` to a JDK 17 installation, because the phone build compiles and embeds the TV app. Without the private release key, the embedded TV app is debug-signed and only for local testing. See the [phone instructions](phone/README.md).

```sh
./phone/gradlew -p phone --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Outputs: `launcher/app/build/outputs/apk/debug/app-debug.apk` and `phone/app/build/outputs/apk/debug/app-debug.apk`.

## Online services

Updates, online wallpapers, feedback and anonymous statistics stay off unless a GenTV API base is configured at build time. Builds from a fresh clone, including forks, never contact the official service. To point a build at a server you run:

```sh
./launcher/gradlew -p launcher -PgentvApiBaseUrl=https://api.example.com/tv/ assembleDebug
```

`GENTV_API_BASE_URL` works the same way. The server side is the [wallpaper service](server/wallpaper/README.md).

## Official releases

Official releases are signed with a private key that is never in Git, and release builds never fall back to a debug key. See [release and signing](docs/release.md); users can check a download as described in [verify a download](docs/getting-started.md#verify-a-download). Standalone TV redistribution uses `launcher/app/build/outputs/distribution/GenTV-TV-release.zip`, which contains the APK and its licences; keep them together.

## Documentation

- [Releases and changelog](https://github.com/Elaine-Lynn/GenTV/releases) · [Environment and installation guide](docs/getting-started.md) ([简体中文](docs/getting-started.zh-CN.md)) · [Privacy](PRIVACY.md) ([简体中文](PRIVACY.zh-CN.md))
- [Implementation and validation](docs/implementation.md) · [Pre-release validation](docs/pre-release-validation.md)
- [Desktop protocol](docs/desktop-protocol.md) · [Module-card contract](docs/reference/modules.md)
- [Performance workflow](docs/reference/perf-ab.md) · [Emulator measurements](docs/emulator-performance.md) · [Development plan](docs/desktop-optimization-plan.md)
- [Device references](docs/devices/README.md)

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
