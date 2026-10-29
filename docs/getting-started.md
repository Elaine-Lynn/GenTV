# Build and install GenTV

English · [简体中文](getting-started.zh-CN.md)

This guide is the entry point for people and AI assistants working from this repository. GenTV consists of Android applications; it is not a desktop Python/Node application or an AI assistant plugin. There is no standalone GenTV CLI package. Command-line installation uses Android SDK Platform Tools (`adb`); the phone app also provides TV installation after an authorised ADB connection.

## Choose a route

- To use an official build, download it from [GitHub Releases](https://github.com/Elaine-Lynn/GenTV/releases/latest) or [the GenTV website](https://www.gentv.me/). Each GitHub release lists SHA-256 checksums in `SHA256SUMS.txt`; the repository itself stores no APKs.
- To build the TV application only, use the TV steps below. The phone application is optional.
- To build the phone companion, install both Android SDK platforms and both build JDKs below. Its build generates and embeds the TV APK from source.
- To contribute or let an AI assistant build locally, follow the same source steps. No server account or signing key is needed for Debug builds.

Both apps require Android 8/API 26 or later. The TV must support Android APK installation and an authorised ADB connection for phone management or CLI installation. Samsung Tizen and LG webOS do not run these Android apps. Manufacturer restrictions may prevent installation, pairing, input switching or Home replacement even on an Android TV.

## Verify a download

Official TV and phone releases are signed with the same certificate. Its SHA-256 fingerprint is:

```text
8D:5B:53:78:F9:05:68:80:F3:03:6D:44:BC:2F:4A:48:32:21:41:FB:B8:D8:BC:91:95:1D:61:A4:65:2D:99:7F
```

In the download folder, the first command checks the file contents and the second the signing certificate (`apksigner` is in the Android SDK build-tools):

```sh
shasum -a 256 -c SHA256SUMS.txt --ignore-missing
apksigner verify --print-certs gentv-tv-3.7.0.apk
```

The `certificate SHA-256 digest` that `apksigner` prints must match the fingerprint above, ignoring colons and case. If it doesn't, don't install the file.

## Local environment

Install Git, Python 3.10 or later, Android SDK Command-line Tools, and the JDKs listed below. Python is needed by the phone's TV asset build and repository checks. No Node.js/npm setup is required.

| Task | Requirements |
|---|---|
| TV build and tests | JDK 17, SDK platform 34, build-tools 34.0.0 |
| Phone build and app/core tests | JDK 21, SDK platform 37.0, build-tools 36.0.0, plus the TV requirements |
| Phone ADB unit tests | JDK 25; Gradle's configured toolchain resolver can provision it over the network |
| CLI installation | SDK Platform Tools (`adb`) |
| Optional screenshot smoke tool | `python3 -m pip install -r tools/requirements-dev.txt` |

Install SDK packages using Android Studio's SDK Manager, or with `sdkmanager` from Android SDK Command-line Tools:

```sh
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "platforms;android-37.0" "build-tools;36.0.0"
sdkmanager --licenses
```

Review and accept the SDK licences interactively. Set the following variables to your own installed paths (POSIX shell examples):

```sh
export ANDROID_HOME="/absolute/path/to/android-sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export GENTV_HOME_JAVA_HOME="/absolute/path/to/jdk-17"
export JAVA_HOME="/absolute/path/to/jdk-21"
java -version
"$GENTV_HOME_JAVA_HOME/bin/java" -version
python3 --version
adb version
```

For a TV-only build, set `JAVA_HOME` to the JDK 17 path. The Gradle wrappers are included; do not install a separate global Gradle. On Windows use PowerShell environment variables and `gradlew.bat` instead of the shell wrapper. Both wrappers are included, but this audit validated macOS builds; Windows remains unverified.

First builds require network access to the Gradle distribution service, Google Maven, Maven Central, the Gradle plugin portal, JitPack and (if needed) the Java toolchain resolver. `--offline` only works after all required artifacts are cached. SDK licences and proxy/firewall configuration must be resolved before building.

## Build from source

From a terminal:

```sh
git clone https://github.com/Elaine-Lynn/GenTV.git
cd GenTV
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
```

TV, from the repository root:

```sh
JAVA_HOME="$GENTV_HOME_JAVA_HOME" ./launcher/gradlew -p launcher --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Output: `launcher/app/build/outputs/apk/debug/app-debug.apk`, package `com.gentv.tv`.

Phone, with `JAVA_HOME` set to JDK 21 and `GENTV_HOME_JAVA_HOME` set to JDK 17:

```sh
./phone/gradlew -p phone --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Output: `phone/app/build/outputs/apk/debug/app-debug.apk`, package `com.gentv.mobile.debug`. Without private signing configuration, the embedded TV APK is debug-signed. Android Debug certificates may differ across development machines. A Debug TV build cannot replace an official differently signed TV installation.

Source builds have online updates, online wallpapers, feedback and statistics disabled unless an API base is explicitly configured. Local desktop operation and phone-to-TV ADB operations do not require the GenTV service. Self-hosting requires a trusted HTTPS endpoint, release manifests, APK hosting and any desired wallpaper media; see [server setup](../server/wallpaper/README.md). Release signing is a separate [release workflow](release.md).

## Install the TV app from a USB drive

This route installs the TV home screen without a phone or ADB. It requires a TV that can read the USB drive and install Android APKs through a file manager.

1. Obtain the **TV APK** (`com.gentv.tv`) from the official download entry, or build it using the TV source steps above. Do not use the phone APK. If you downloaded the TV distribution ZIP, extract it first and keep its licence files with any redistribution.
2. Copy the APK to a USB drive in a filesystem format your TV supports, then connect it to the TV.
3. Open the drive in the TV's file manager and select the APK. If prompted, allow that file manager to install unknown applications, then confirm Android's installation dialog.
4. Open GenTV from the TV's app list and check that navigation works. Choosing it as the default Home is a separate action in system settings.

A manufacturer may block USB APK installation or omit a suitable file manager. In that case use the phone/ADB route if supported. A signature conflict or downgrade error is not permission to uninstall the existing app; preserve data and follow [release migration](release.md). Installing the TV app from USB alone does not provide phone-based cleanup or DNS configuration; those require the authorised phone connection. USB installation was documented but not exercised on a real TV in this audit.

## Install with an AI assistant

Use an assistant that can access your local terminal and Android SDK tools. A chat-only assistant can explain the steps but cannot install the app itself. No GenTV-specific AI plugin is required.

You can give it this request:

> Help me install GenTV from https://github.com/Elaine-Lynn/GenTV. Read docs/getting-started.md first. Check my local environment and ask me to identify the intended TV if necessary. Use a compatible TV APK or build a Debug APK from source. Install and open it over authorised ADB, verify the result, and preserve the current default Home and existing applications. I will approve pairing and installation prompts on the device.

The assistant should follow the [CLI installation steps](#connect-and-install-with-the-cli) and the [assistant instructions](#instructions-for-an-ai-assistant). The user must identify the intended device and approve device prompts; do not invent signing credentials, silently uninstall an incompatible app or disable the original launcher. This is an assisted installation workflow, not an unattended one-click installer.

## Connect and install with the CLI

Enable debugging on the intended device and approve the authorisation prompt on that device. A USB-connected phone can be used directly. For a TV with legacy network ADB enabled, use `adb connect TV_IP:ADB_PORT`. For Android wireless debugging, use the pairing and connection addresses displayed by the device:

```sh
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

Enter the pairing code interactively. The pairing port and connection port are usually different; neither is necessarily 5555. Do not enable network debugging by guessing ports or operating an unrelated device. Each device must appear with state `device`; resolve `unauthorized` or `offline` before proceeding.

Always specify the intended serial from `adb devices -l`, especially with multiple devices. These commands assume a build from the previous section and run from the repository root:

```sh
# TV: replace TV_SERIAL with the exact authorised TV serial/address.
adb -s TV_SERIAL shell getprop ro.build.version.sdk
adb -s TV_SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s TV_SERIAL install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb -s TV_SERIAL shell pm path com.gentv.tv
adb -s TV_SERIAL shell am start -W -n com.gentv.tv/.MainActivity
```

Check `Success` from installation and `Status: ok` from launch, then confirm the GenTV screen on the TV. Re-run the Home resolution command to verify that the previous default has been preserved. An APK appearing in `pm path` alone does not prove a working UI or remote.

```sh
# Phone: replace PHONE_SERIAL with the exact authorised phone serial.
adb -s PHONE_SERIAL install -r phone/app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE_SERIAL shell am start -W -n com.gentv.mobile.debug/com.gentv.mobile.MainActivity
```

For an official phone APK use package `com.gentv.mobile`, not the `.debug` package. After opening the phone app, keep phone and TV on the same reachable local network, connect/pair and approve the TV prompt, then select the GenTV installation action. The current installer installs and opens GenTV and checks the desktop bridge; it preserves the default Home and does not grant optional permissions automatically.

Installation is separate from choosing a default Home. Use the TV's system Home settings only if the owner wants GenTV as default. Some firmware does not expose this choice. Accessibility helpers and device-specific HDMI/input features need their own setup and acceptance; do not disable the stock launcher to work around a blocked setting.

## Errors and recovery

| Symptom | Next step |
|---|---|
| SDK location/platform not found | Check `ANDROID_HOME`, installed package IDs and SDK licences |
| TV build rejects Java version | Set `GENTV_HOME_JAVA_HOME` to JDK 17 and TV `JAVA_HOME` to the same path |
| ADB tests cannot find Java 25 | Allow toolchain provisioning or install JDK 25 and configure Gradle toolchain discovery |
| Dependency resolution fails | Check repository connectivity, proxy and first-build network access; omit `--offline` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Certificate conflict: stop; preserve user data and follow the deliberate migration in release.md |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Use a compatible newer version; do not automatically uninstall or force a downgrade |
| App installs but Home stays unchanged | Expected for normal installation; default selection is a separate user action |
| Online features unavailable | Expected without API configuration; a running server also needs valid manifests/media |
| ADB works but a feature fails | Check model/firmware support and the individual feature; connection success is not compatibility certification |

Before removing GenTV, choose the original Home in system settings and confirm it opens. Export desktop configuration and keep recovery records before any uninstall: Android uninstallation removes application data. Revoke ADB authorisation and turn debugging off when maintenance is complete.

## Community device references

Device lists under `phone/framework/data/devices/` are inherited community records. Some comments refer to upstream device documents or tools that are not shipped here; these are historical references, not executable GenTV instructions or a promise that every model is supported. Do not run a referenced cleanup command or disable a package without checking the current GenTV protection rules and the actual device/firmware.

## Instructions for an AI assistant

Read this guide, README.md, CONTRIBUTING.md and the relevant component documentation first. Inspect local tool versions and Git status, then build and run checks before attempting installation. Treat placeholders as placeholders; never guess a device serial, private key, server host or pairing code. Do not print signing material or commit keys, backups, screenshots or device records.

For a build-only request, stop after validating the artifacts. For a device installation request, identify the user's intended authorised device, record the current Home, install the selected compatible APK and verify package, launch result and the device UI. Device prompts require the user's interaction. A rejected signature/version is a blocking result, not permission to uninstall. Do not change default Home, disable packages or run cleanup merely to install. Report build/test evidence separately from emulator or real-device acceptance.
