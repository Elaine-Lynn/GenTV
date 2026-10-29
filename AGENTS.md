# GenTV for AI assistants

This file is for coding assistants (Claude Code, Codex, Gemini CLI and similar) working in this repository or installing GenTV for a user. People should start at [README.md](README.md).

## What GenTV is

GenTV is a free, open-source, ad-free home screen (launcher) for Android TV and Google TV, plus an Android phone companion that installs it, cleans up preinstalled apps, restores changes and works as a remote. It is two Android apps; there is no GenTV CLI, desktop app or assistant plugin.

| Component | Application ID | Source | Minimum |
|---|---|---|---|
| TV home screen | `com.gentv.tv` | `launcher/` | Android 8 / API 26 |
| Phone companion | `com.gentv.mobile` (`com.gentv.mobile.debug` for Debug builds) | `phone/` | Android 8 / API 26 |

Samsung Tizen, LG webOS and Hisense VIDAA are not Android and cannot run GenTV. Stop and tell the user if the TV is one of these.

## Installing GenTV for a user

Read [docs/getting-started.md](docs/getting-started.md) first, including its "Instructions for an AI assistant" section. That guide is the source of truth; this file only summarises the rules.

1. Ask for the TV's IP address or serial if it isn't given. Never guess a device, port, pairing code, key or server.
2. If ADB isn't reachable yet, point the user to [turning on ADB](docs/devices/adb-by-brand.md) for their brand. The user turns it on and accepts every prompt on the TV.
3. Check local tools (`adb version`, JDKs, SDK) before building. Use an official TV APK from [gentv.me](https://www.gentv.me/) if the user has one, otherwise build a Debug APK from source (see below).
4. Record the current Home before installing:
   `adb -s <serial> shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
5. Install and open:
   `adb -s <serial> install -r launcher/app/build/outputs/apk/debug/app-debug.apk`
   `adb -s <serial> shell am start -W -n com.gentv.tv/.MainActivity`
6. Verify `Success`, `Status: ok`, and ask the user to confirm the GenTV screen on the TV. Re-run the Home command from step 4 and confirm it is unchanged.
7. Report what you ran and its raw output. Report build or emulator results separately from what was seen on the real TV.

Do not:

- change the default Home, disable the stock launcher or disable any package just to install GenTV;
- run cleanup lists from `phone/framework/data/devices/` on a device; they are community references for the phone app, not scripts;
- uninstall an existing GenTV or clear its data after `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or `INSTALL_FAILED_VERSION_DOWNGRADE`; stop and follow [docs/release.md](docs/release.md);
- expose ADB beyond the local network, or print keys, pairing codes or signing material.

When the work is finished, remind the user to turn ADB debugging off.

## Building from source

No signing keys or server access are needed for Debug builds. Run from the repository root.

```sh
# TV: JDK 17, Android SDK 34, build-tools 34.0.0
./launcher/gradlew -p launcher --no-daemon testDebugUnitTest lintDebug assembleDebug

# Phone: JDK 21 for Gradle, JDK 25 for ADB tests, Android SDK 37; GENTV_HOME_JAVA_HOME = JDK 17
./phone/gradlew -p phone --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Outputs: `launcher/app/build/outputs/apk/debug/app-debug.apk` and `phone/app/build/outputs/apk/debug/app-debug.apk`. The phone build compiles and embeds the TV app; without the private key it is debug-signed and for local testing only.

## Changing the code

Follow [CONTRIBUTING.md](CONTRIBUTING.md). Before you finish, run the checks for what you changed:

```sh
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
git diff --check
```

- Don't hard-code the GenTV API address; it is build configuration (`-PgentvApiBaseUrl` or `GENTV_API_BASE_URL`).
- Don't commit APKs, keystores, `keys/`, `work/`, `shots/` or build output.
- If you change `phone/framework/`, regenerate `phone/framework-lock.json` in the same commit.
- Keep licence and copyright notices in `LICENSE`, `licenses/`, `phone/framework/LICENSE` and `THIRD_PARTY_NOTICES.md`.
- Commit subjects are short and prefixed by area: `Launcher: …`, `phone: …`, `server: …`, `docs: …`.

## Where things are

| Path | Contents |
|---|---|
| `launcher/` | TV home screen (Java) |
| `phone/` | Phone companion (Kotlin); `phone/framework/` holds ADB, cleanup and restore logic |
| `phone/framework/data/devices/` | 252 community device references; index in [docs/devices/README.md](docs/devices/README.md) |
| `server/wallpaper/` | Optional self-hosted update and wallpaper service |
| `docs/` | Installation, protocol, release and validation documents |
| `tools/` | Repository checks and helper scripts |
