# GenTV for AI assistants

This file is for coding assistants (Claude Code, Codex, Gemini CLI and similar) working in this repository or installing GenTV for a user. People should start at [README.md](README.md).

## What GenTV is

GenTV is a free, open-source, ad-free home screen (launcher) for Android TV and Google TV, plus an Android phone companion that installs it, cleans up preinstalled apps, restores changes and works as a remote. It is two Android apps; the repository also provides a local maintenance adapter for assistants (see docs/tv-care.md), not a standalone desktop app or assistant plugin.

| Component | Application ID | Source | Minimum |
|---|---|---|---|
| TV home screen | `com.gentv.tv` | `launcher/` | Android 8 / API 26 |
| Phone companion | `com.gentv.mobile` (`com.gentv.mobile.debug` for Debug builds) | `phone/` | Android 8 / API 26 |

Samsung Tizen, LG webOS and Hisense VIDAA are not Android and cannot run GenTV. Stop and tell the user if the TV is one of these.

## Installing GenTV for a user

Read [docs/getting-started.md](docs/getting-started.md) first, including its "Instructions for an AI assistant" section. That guide is the source of truth; this file only summarises the rules.

1. Ask for the TV's IP address or serial if it isn't given. Never guess a device, port, pairing code, key or server.
2. If ADB isn't reachable yet, point the user to [turning on ADB](docs/devices/adb-by-brand.md) for their brand. The user turns it on and accepts every prompt on the TV.
3. Check local tools (`adb version`, JDKs, SDK) before building. Use an official TV APK from [GitHub Releases](https://github.com/Elaine-Lynn/GenTV/releases/latest) (check it against `SHA256SUMS.txt`) or [gentv.me](https://www.gentv.me/) if available, otherwise build a Debug APK from source (see below).
4. Record the current Home before installing:
   `adb -s <serial> shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
5. Install and open:
   `adb -s <serial> install -r launcher/app/build/outputs/apk/debug/app-debug.apk`
   `adb -s <serial> shell am start -W -n com.gentv.tv/.MainActivity`
6. Verify `Success`, `Status: ok`, and ask the user to confirm the GenTV screen on the TV. After confirming GenTV works, set it as default Home with `adb -s <serial> shell cmd package set-home-activity com.gentv.tv/.MainActivity`. Re-run the Home command from step 4, press the physical Home button and confirm GenTV opens; reboot and verify again. A command reporting success alone is not proof. If handover fails, restore the recorded Home and report the limitation. If the user explicitly requests preview/install only, preserve the existing Home instead.
7. Report what you ran and its raw output. Report build or emulator results separately from what was seen on the real TV.

Do not:

- treat CVTE, an unfamiliar Home or unclear Wi-Fi ownership as unsupported without investigation. A full GenTV setup authorises the necessary reversible Home handover: install and verify GenTV first, try normal selection, identify the actual Home, prepare recovery and continue the local staged flow in `docs/tv-care.md`. Resolve network ownership or verify actual Ethernet before disabling setupwraith; CVTE uses a separate reboot checkpoint. Never uninstall the stock launcher or disable Android/TV core services;
- run cleanup lists from `phone/framework/data/devices/` on a device; they are community references for the phone app, not scripts;
- uninstall an existing GenTV or clear its data after `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or `INSTALL_FAILED_VERSION_DOWNGRADE`; stop and follow [docs/release.md](docs/release.md);
- expose ADB beyond the local network, or print keys, pairing codes or signing material.

When the work is finished, remind the user to turn ADB debugging off.

## AI TV care skill

For assistant-driven cleanup, ad-service reduction, GenTV installation, default Home handover and recovery, use [GenTV TV Care](skills/gentv-tv-care/SKILL.md). The local computer adapter and its checks are documented in [TV care](docs/tv-care.md). It prioritises the current local implementations, device records and environment; external references are only for comparison or missing device notes. Compatibility labels do not replace the real-device checks above.

## Building from source

No signing keys or server access are needed for Debug builds. Run from the repository root.

```sh
# TV: JDK 17, Android SDK 34, build-tools 34.0.0
./launcher/gradlew -p launcher --no-daemon testDebugUnitTest lintDebug assembleDebug

# Phone: JDK 21 for Gradle, JDK 25 for ADB tests, Android SDK 37; GENTV_HOME_JAVA_HOME = JDK 17
./phone/gradlew -p phone --no-daemon :core:test :adb:test :tv-care:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Outputs: `launcher/app/build/outputs/apk/debug/app-debug.apk` and `phone/app/build/outputs/apk/debug/app-debug.apk`. The phone build compiles and embeds the TV app; without the private key it is debug-signed and for local testing only.

## Changing the code

Follow [CONTRIBUTING.md](CONTRIBUTING.md). Before you finish, run the checks for what you changed:

```sh
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
python3 -m unittest discover -s tools -p 'test_*.py'
python3 -m unittest discover -s skills/gentv-tv-care/scripts -p 'test_*.py'
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
