# GHCP 1 reference app

中文接入入口：[Gen 插件 App 开发规范](../../docs/reference/home-content-development.zh-CN.md)。

A standalone, offline Android TV app demonstrating the [GenTV desktop content protocol](../../docs/reference/home-content.md). Package: `com.gentv.example.homecontent`. It does not use GenTV's signing key, Module Card spec 1 or ADB desktop configuration permissions. Its only data are fictional metrics, original geometric JPEG artwork and mock media progress. It requests no permissions, reads no personal data, and starts no background service. Its bound service runs only while a host connects.

## Build

JDK 17 and Android SDK 34:

```sh
./launcher/gradlew -p launcher --no-daemon :home-content-example:assembleDebug :home-content-example:lintDebug
```

Output: `launcher/home-content-example/build/outputs/apk/debug/home-content-example-debug.apk`. This is a local debug reference APK, not an official signed weather/media product and not embedded in the Launcher or phone companion.

Install onto an explicit authorized emulator serial:

```sh
adb -s <emulator-serial> install -r launcher/home-content-example/build/outputs/apk/debug/home-content-example-debug.apk
adb -s <emulator-serial> shell am start -n com.gentv.example.homecontent/com.gentv.example.ExampleActivity
```

Choose Summary, Media row or Feature. Back in GenTV, open Launcher settings → Desktop content, explicitly allow the example, and place its icon in the first row. Up enters content, Down/Back returns; OK on the normal tile still opens the app. Empty, malformed, nonresponse and missing-background modes demonstrate safe fallback. The sample Activity also accepts `example.mode` for emulator automation; this only switches fictional fixture data.

## Source map

- AndroidManifest.xml: launch activity metadata and exported bound service.
- ContentService.java: Messenger request/reply, public-data caller allow-list and snapshots.
- ExampleActivity.java: ordinary entry point, template selection and content action destination.
- res/raw/landscape.jpg: original local geometric test artwork, supplied as an android.resource URI.

The repository MIT licence applies. There are no third-party data feeds or bundled copyrighted media.

## Important authorization boundary

The service checks Android's assigned sending UID against `com.gentv.tv` before returning its **public fictional data**. This is not a sufficient policy for private data or authenticating a specific production signing identity. A real private provider must add a user-approved signer-aware host authorization flow, enforce it on images/actions, and invalidate sessions on logout/revocation. Never use the sample to expose private media/calendar data simply because a caller supplies GenTV's package name.

No host SDK dependency is necessary: implement the documented framework Messenger wire contract. Providers may use Java or Kotlin and their own licences and signing keys.
