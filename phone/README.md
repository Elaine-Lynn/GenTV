# GenTV Remote

Android companion for GenTV TV. Application ID and namespace: `com.gentv.mobile`; debug builds use `com.gentv.mobile.debug`. TV application ID: `com.gentv.tv`.

[GitHub](https://github.com/Elaine-Lynn/GenTV) · [Website](https://www.gentv.me/) · [Capabilities](CAPABILITIES.md) · [Notices](../THIRD_PARTY_NOTICES.md)

For first-time environment setup and CLI/device installation, use [the installation guide](../docs/getting-started.md).

## Build

Use JDK 21 for Gradle, SDK 37, JDK 17 for the TV build and JDK 25 for ADB unit tests. Set the SDK path locally and keep release credentials in the ignored `keys/` directory.

```sh
./gradlew --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Set `GENTV_HOME_JAVA_HOME` to a local JDK 17 installation. Release builds require explicit signing properties and never fall back to debug signing; without them, debug builds embed a debug-signed TV preview for local testing. No credential examples in this repository contain usable secrets.

## Structure and delivery

- `app/`: GenTV UI and integration, namespace `com.gentv.mobile`.
- `framework/core`: package protection, list parsing, logging and recovery, namespace `com.gentv.core`.
- `framework/adb`: authorised ADB connection and transport, namespace `com.gentv.adb`.
- `framework/data`: licensed device lists and theme configuration, without a second launcher implementation.
- `framework-lock.json`: integrity manifest for the current modified framework snapshot; original authors remain in the licence files.

`app:buildHomeAssets` builds this repository's GenTV TV source, checks package `com.gentv.tv`, the configured release certificate (when signing is configured) and SHA-256, then includes only `gentv-home.apk`. Incremental builds remove obsolete generated desktop APKs. Installation opens GenTV and preserves the current default Home; real-TV acceptance remains separate.

Older installed applications and recovery records keep their real identities. The parsers continue to recognise them; new builds do not ship or advertise alternative launcher products. Existing saved data is not deleted, relabelled or silently migrated by this branding update.

## Services and boundaries

Website and download entry: `https://www.gentv.me`. The API base is build configuration (`-PgentvApiBaseUrl`, `GENTV_API_BASE_URL`, or `apiBaseUrl` in the private `keys/signing.properties`); without it, updates, feedback and statistics are off. Official releases use `https://api.gentv.app/tv/`; mobile update path: `update?platform=mobile`, feedback: `feedback`, optional usage events: `events`. Source configuration is not a fresh online service verification.

Android TV / Google TV device support depends on model and firmware. Device lists are inherited references, not GenTV certification. Backups and logs contain sensitive local information; preserve the original recovery records. Emulator and unit-test success do not prove real-TV pairing, Home, HDMI, remote keys or recovery.

Desktop controls: [management](docs/desktop-management.md), [appearance](docs/cinema-appearance.md). Other contracts: [updates](docs/mobile-self-update.md), [optional analytics](docs/mobile-analytics.md). Sensitive reports: see [SECURITY.md](../SECURITY.md). Network use: [PRIVACY.md](../PRIVACY.md).
