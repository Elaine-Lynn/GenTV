# GenTV Home Content Protocol — GHCP 1

Developer guide: [中文 App 开发规范](home-content-development.zh-CN.md) (setup, code, field constraints and acceptance checklist).

Implementation status: local source supports discovery, explicit user permission, asynchronous Messenger snapshots, three base native templates and optional image backgrounds. This is a developer preview, not a claim of a public release or real-TV certification. The source-built reference app is [launcher/home-content-example](../../launcher/home-content-example/README.md).

GHCP is separate from [ADB desktop configuration](../desktop-protocol.md) and [Module Card spec 1](modules.md). Extensions are independent Android apps, with their own package/signing keys. There is no downloaded executable code, account requirement, official server requirement or catalogue approval requirement.

## 1. Declare a provider

On a real exported, enabled `MAIN` + `LEANBACK_LAUNCHER` or `LAUNCHER` activity:

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
    <meta-data android:name="com.gentv.tv.home.PROTOCOL_MAJOR" android:value="1" />
    <meta-data android:name="com.gentv.tv.home.CONTENT_SERVICE" android:value="com.example.ContentService" />
</activity>
<service android:name=".ContentService" android:exported="true">
    <intent-filter>
        <action android:name="com.gentv.tv.home.BIND_CONTENT" />
    </intent-filter>
</service>
```

`CONTENT_SERVICE` is a fully-qualified class name, relative `.Class`, or flattened component. The activity and service must be in the same package and UID. The host rejects unavailable/foreign/disabled components and multiple current signers. Bind permissions, if any, must already be granted to the host. No system permission is needed for ordinary integration. Do not remove your normal launch activity: OK on its icon still opens it.

The owner opens **Launcher settings → Desktop content**, enables this app, then places its icon in the first row. Permission is bound to activity, service and current signing certificate. Signing identity or service changes require another explicit permission. Catalogue installation does not imply permission to display content. Module Card permission does not imply GHCP permission. A global switch disables all desktop content.

No auto-reorder occurs. Content is currently disabled in pond mode and while child protection is enabled. A layout with insufficient space hides content instead of covering controls.

## 2. Messenger transport

Return an `android.os.Messenger` Binder from `Service.onBind`. Requests and replies use only Android framework `Message`/`Bundle`, primitive fields and a JSON string. No custom Parcelable, RemoteViews, PendingIntent, bitmap, executable UI or serialised Intent is accepted.

Host request:

| Field | Value |
|---|---|
| Message.what | `1` (REQUEST) |
| Message.arg1 | Opaque request token; echo unchanged |
| Message.replyTo | Host callback Messenger |
| Bundle.protocolMajor | integer `1` |
| Bundle.component | The declared launch component, flattened short string |
| Bundle.locale | BCP-47 language tag |
| Bundle.widthDp / heightDp | Available content area; hints only |

Provider reply:

| Field | Value |
|---|---|
| Message.what | `2` (SNAPSHOT) |
| Message.arg1 | Request token unchanged |
| Bundle.snapshot | UTF-8 JSON object encoded as a Java String, at most 65,536 bytes |

The host accepts only the first response per request, and verifies Android's `Message.sendingUid` equals the discovered provider UID. Tokens are session-specific; late replies are discarded. Request preparation/discovery is asynchronous; the bind + response/validation deadline is one second. A timeout keeps ordinary Home visible. If no snapshot has ever arrived for this focus, the host makes at most one cold-start retry after 500 ms; it never polls indefinitely. Each attempt has a new token. A new focus/session can retry. The host retains at most one bound provider, and unbinds on leaving the relevant Home session.

While bound, the provider can send `what=3` (INVALIDATE), with the same token, to immediately clear a snapshot/background, e.g. on logout or access revocation. This immediately clears the old session and returns content focus to its source icon. If that source remains active, the host requests fresh authorized data; private providers must deny reads after logout/revocation. Invalidation refetches are limited to one per second; a faster repeat clears without auto-refetch until the owner refocuses. Repeated SNAPSHOT messages are not a push stream. The host refreshes foreground snapshots shortly before expiry when their TTL is greater than two seconds; each refresh has a new request token. Expiry is measured from receipt using elapsed time, not the provider's wall clock.

A provider must verify the host using `sendingUid` and PackageManager, never a self-reported package name. The reference service contains public, fictional data and a package-name allow-list only. **That public-data sample is not sufficient authorization for private data.** A private provider must implement its own user-approved, signer-aware host trust policy, ensure reads/actions/images use it, and invalidate when permission or account state changes. Host display consent does not confer account access.

## 3. Snapshot schema

```json
{
  "protocolMajor": 1,
  "snapshotId": "weather-42",
  "template": "summary",
  "title": "今天，也有好心情",
  "subtitle": "天气摘要",
  "privacy": "public",
  "ttlSeconds": 30,
  "background": null,
  "items": [
    {
      "id": "temperature",
      "title": "18°",
      "subtitle": "多云",
      "activity": "com.example/.MainActivity",
      "actionId": "weather"
    }
  ]
}
```

| Field | Contract |
|---|---|
| protocolMajor | Required integer 1; incompatible versions are not reinterpreted |
| snapshotId | Required nonempty string, at most 80 characters |
| template | `media_row`, `summary`, or `feature` |
| title | Required nonempty string, at most 80 characters |
| subtitle | Optional string, at most 160 characters |
| privacy | Required `public` or `personal`; both stay in memory only |
| ttlSeconds | Required integer 1–300; expired content/images disappear |
| background | Optional image URI string or null; absent/null/empty means no app background |
| items | Required array; empty means no content, not a failure |
| item.id | Required nonempty unique string, at most 80 characters |
| item.title | Required nonempty string, at most 80 characters |
| item.subtitle | Optional string, at most 160 characters |
| item.image | Optional image URI or null |
| item.progress | Optional finite number 0–1 |
| item.activity | Optional flattened exported activity component in the provider's package |
| item.actionId | Optional opaque string, at most 80 characters; requires activity |

Maximum items: `media_row` 6, `summary` 3, `feature` 1. Media cards scroll horizontally and all accepted actions are reachable. Missing actions make an item informational; they do not cause the host to guess a deep link. Source attribution always comes from installed app identity. Text controls and bidi override/isolate characters are refused. Duplicate keys/IDs, excessive nesting (over 12), oversized strings, invalid types and trailing payload are rejected. Unknown optional keys are ignored. Future required capabilities must use a compatible extension/major-version contract; GHCP 1 does not accept arbitrary new templates.

Each response is a complete snapshot, not a patch. Omitted background never inherits a prior snapshot's image. Different `snapshotId` does not grant an application more privileges. Revisions should use stable item IDs to help preserve focus.

## 4. Images and actions

Images support only `android.resource://<your package>/<resource id>` and `content://<your own provider>/...`. Maximum URI length is 1,024 characters; query, fragment and user-info are rejected. The provider must belong to the extension's own package/UID. No arbitrary filesystem path or HTTP(S) download is performed by GenTV. For mutable image versions use distinct owned resource paths; the same background URI within a session is treated as unchanged.

If a content provider needs grants, explicitly grant read access to the verified host and revoke it when appropriate; putting a URI in a Messenger Bundle does not automatically grant access. Private image providers must enforce the same host authorization as snapshots.

Image reads and decoding are asynchronous, best effort and bounded: 2 MiB encoded bytes per image, input edges at most 8,192 pixels, sampled to at most approximately 480×300 for item art / 1280×720 for backgrounds. Background preparation deadline is two seconds; a late image does not suddenly appear. Control/JSON, image reads, and cancellation use separate process-wide bounded executors (2 / 2 / 1 workers). An uncooperative image provider cannot grow threads across Activity recreation or starve other providers’ text/control work; exhausted image capacity falls back to text. Decode ownership has a 12 MiB session budget, with additional transient decode/render overhead to be measured on real devices. Images are never saved into a desktop export, disk snapshot cache, telemetry or logs. Optional unreadable/foreign/invalid image resources fall back to text/default wallpaper; malformed JSON field types or forbidden URI schemes reject the malformed snapshot.

For an actionable item, GenTV revalidates that `activity` is enabled/exported, accessible, and in the same current package/UID before launching it. The launch is an explicit `ACTION_VIEW` with **one** string extra:

```java
getIntent().getStringExtra("com.gentv.tv.home.ACTION_ID");
```

No caller-provided Intent flags, extras, grant flags or cross-package destinations are accepted. Activities must perform their own authorization and confirmation before any side effect. The host's child restriction still applies. GHCP 1 actions navigate to the app; the desktop is not a player/device-control surface.

## 5. Default presentation and keys

The normative [presentation contract](home-content-presentation.zh-CN.md) applies to all templates:

- No app background by default. Content floats above the owner's existing wallpaper; covers are not automatically used as wallpaper.
- About 200 ms focus dwell, then native content moves a short distance and fades in (initial 280 ms; exit 180 ms; about 20 dp).
- Optional background loads independently and softly fades over approximately 700 ms; content never waits for it. Background failure does not remove valid text.
- Rapid navigation cancels old work/animations; late images cannot replace the current source. At most two temporary background layers during fading.
- Reduced motion removes movement and uses a short fade; disabled system animations snap to final state.
- Up from a first-row icon enters actionable content; Left/Right navigate; Down/Back returns to source; Up from content reaches the top bar. Back's key-up is consumed so it cannot also trigger the screensaver.
- Leaving Home, losing window focus, opening menus/screen saver, revoking consent or changing packages clears the active session. Personal content is hidden immediately at expiry/revocation.

The current native preview renderer does not implement every exploratory screenshot layout. Summary, media row and feature are the base templates. A host advertising the optional Boolean `channelPreview` in REQUEST also accepts the [channel preview extension](home-content-development.zh-CN.md#55-可选频道预览). Runtime images are memory-only; return-to-Home fetches a fresh snapshot.

## 6. Build and verify

```sh
# JDK 17, Android SDK 34
./launcher/gradlew -p launcher --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :home-content-example:assembleDebug :home-content-example:lintDebug
```

On an isolated authorized emulator/device, install both debug APKs, open the reference app to choose a template, then allow it under Desktop content and add its icon to the first row. Do not overwrite a differently signed installed GenTV or uninstall it to make this test work. The sample does not change default Home.

The emulator-only integration fixture is run through the existing test runner with `-e home_content_only true`; it writes an isolated emulator's GenTV order/consent for the sample. It must never be used on an owner TV. See [validation](../home-content-validation-2026-11-24.zh-CN.md) for exact checks and remaining real-device boundaries.

Official Android references: [Messenger Message and sendingUid](https://developer.android.com/reference/android/os/Message), [ContentResolver image access/cancellation](https://developer.android.com/reference/android/content/ContentResolver).
