# Desktop configuration protocol

## Identity and transport

Explicit ordered broadcast to `com.gentv.tv/com.gentv.tv.DesktopReceiver`, protected by `android.permission.WRITE_SECURE_SETTINGS`. The existing authorized ADB shell sends requests; the phone and launcher receive no new grants. No HTTP server or remote accessibility setup is added.

Operations: `read`; `save` with integer `revision` and base64 UTF-8 JSON `payload`; `undo` with integer `revision`. Encoded payload limit: 60,000 characters. Success: `GENTV1:<base64 JSON>` in ordered broadcast result data. Failure: `GENTV_ERROR:...`; clients must read before retrying and must not replay an uncertain write automatically.

Response includes protocol 1, backend gentv, revision, canUndo, config, apps, sources, capabilities and smooth. Config schema **2** uses flattened short Android component keys. The phone and the TV accept only schema 2; any other schema is refused rather than reinterpreted. Missing or ambiguous applications and foreign input mappings are not guessed.

## Fields

| Field | Meaning |
|---|---|
| schema | Integer 2 |
| order | Stable component keys, including temporarily missing apps; maximum 500 |
| hidden | Component keys hidden only on Home; maximum 500 |
| names | Component-to-name map; maximum 60 characters per name |
| effects | auto / smooth / full; maps onto original reduced-motion behavior |
| pinnedSources | Optional list of local framework input IDs, maximum 100 |
| sourceNames | Input-to-display-name map |
| sourceDevices | playstation / switch / xbox / appletv / computer; generic existing vector icons |
| sourceTargets | Input-to-local-input mapping; never updates system input databases |
| folders | Optional object keyed by `com.gentv.tv/Folder.<32 lowercase hex>`; each value has only `name` and `apps` |
| images | Optional component-to-base64-JPEG map; maximum 12 KiB per JPEG, 24 KiB total, dimensions at most 320 × 180 |

Unknown fields, invalid types, duplicate keys, invalid components, unsupported values and control characters in names are rejected. New input pins and targets must resolve in the current device input list. Existing unchanged entries are retained if temporarily unavailable.

A pinned input has a stable synthetic component key derived from its input ID. It is routed by the adapter to the original input-switching code, never launched as a real synthetic activity. If a target disappears, activation opens the original input menu. Component-level custom names override the input name; resetting the component name restores the input name. Framework-unlisted vendor sources remain accessible through the original source menu and are not offered as new pins in this version.

## One authority

`desktop-state.json` in app-private storage is an AtomicFile transaction containing revision, the original Order/LibraryApps text encodings, names, hidden components, effects and source settings. Old order and library files are imported when the state does not yet exist and are left untouched. Unreadable state is not overwritten on the next edit.

Order.save, LibraryApps.save, local rename and phone saves share this authority. Remote reorder invalidates phone drafts. During an active TV reorder, phone writes and undo are rejected. Theme and local Launcher settings revisions are observed before phone read/write, invalidating stale drafts without duplicating the original settings storage.

Each accepted change keeps one prior complete desktop state. Undo advances the revision and consumes that prior state. Theme/settings changes outside this layout domain invalidate the undo point. Revisions never roll back. Theme defaults continue to belong to original Config; effective hidden entries also reflect theme and app-library visibility.

## Verification boundaries

The existing phone report recognizes exact new/old Home components and keeps physical remote, standby, reboot, HDMI and closed-debugging checks NOT TESTED unless explicitly owner-confirmed. Pinned source configuration can be validated on an emulator, but correct picture, sound and vendor routing require a real TV.

## Source-built preview delivery

The standalone phone project packages APKs generated from pinned source, with an asset SHA-256 manifest. DesktopPreviewInstaller validates integrity and minSdk, uses a unique temporary path, installs once, opens the explicit Activity and reads the schema2 bridge. Refusal never triggers an uninstall or verification bypass. Temporary files are cleaned up. It does not change Home or enable helpers.

An opt-in emulator integration test exercises this actual installer implementation using the existing authorized ADB shell contract, asserting that default Home and the prior desktop config are unchanged. It verifies the phone data/transport contract, not physical remote behavior.

## Single-level folders and app images (3.1 preview)

Capabilities `folders` and `images` advertise these optional schema2 extensions. Existing state is upgraded with empty objects; no existing app is automatically grouped or restyled. Older clients omitting either nonempty field are refused instead of silently destroying the layout. Imports made before these extensions can still be used while the corresponding TV fields are empty; otherwise merge/re-export with the current phone client first.

Folders are stable synthetic tiles, never Android activities. At most 30 folders, 100 apps each; no nested folders, source/module synthetic entries or duplicate membership across folders. Module cards keep their existing launch behavior when their actual app component is grouped. Empty folders remain visible and editable. Missing members remain configured but are not launched or displayed as available; hidden members remain hidden. Moving an app into a folder makes it visible, removes it from other folders, and preserves its global order. Dissolving removes only the folder metadata/tile, leaving apps and their names/images/order intact. Both phone and remote support creating, renaming, arranging membership, moving out and dissolving; phone drafts write only on Save.

The phone decodes bounded local input (8 MiB source limit), downsamples and JPEG-compresses it. No filename, URI, external storage path or network resource is accepted by the TV. Native validation checks bytes, format, dimensions and total budget before committing. Image data lives inside the atomic configuration and its one-step undo, so JSON exports include the pictures. Removing the custom picture restores the original app/banner selection. The TV caches only decoded custom pictures in a bounded 512 KiB memory cache; no new disk cache or background worker is introduced. The entire encoded profile must still fit the 60,000-character transport budget, so a large app/name configuration may leave less image capacity.


## Online wallpaper scope (3.2)

The online wallpaper feed is separate from the desktop layout protocol: https://api.gentv.app/tv/wallpaper, metadata `wallpapers: [{id, url}]`, fixed120-second foreground rotation. Runtime feed data and the last downloaded image do not enter schema2 exports or undo. User desktop layout, local theme settings and their revisions remain authoritative as before. The APK bundles the original 1672×941 blue abstract JPEG (`wallpaper_default.jpg`), without recompression as the first-frame/offline fallback. Empty feeds restore a valid local theme wallpaper or the bundled abstract image; network failures retain the last good background. After a hidden-UI memory trim, returning Home restores the local fallback before any network work. Online images still crossfade normally. Only the independentGenTV app gains INTERNET; no device identifiers, HTTP server or background service is introduced. See the server README for endpoint and deployment details.


### Aerial screensaver

Home uses the existing local/online image wallpaper rotation. It never creates a
video player. Pressing Back on Home first returns focus to the first app tile if
another control or tile is focused, scrolling to the beginning as needed. Back
while the first tile is focused opens `AerialScreensaverActivity`. The saver
also opens after Home remains idle for the interval selected in Launcher settings:
2 minutes (default), 10 minutes, 60 minutes, or Never. The TV preference
`launcher_settings.screensaver_minutes` stores 2, 10, 60 or 0 (Never). Invalid values, including the retired 30-minute setting, fall
back to 2 minutes; restoring theme defaults restores 2 minutes. The older
`background_mode` video/image preference no longer affects Home. The legacy
`home_idle_clock_minutes` theme property no longer controls Home idle behavior;
the separate system clock dream remains available.

Never disables automatic entry but still permits Back to open the saver manually.
Input resets the deadline. Losing Home focus or leaving Home cancels it; returning
starts a full new interval. Menus, settings, other apps and reorder mode do not
launch the saver over their content. A waking key is consumed through key-up so
it neither launches the selected app nor immediately reopens the saver. Touch and
pointer input also exit. Home/power keys reserved by Android retain system behavior.

The fullscreen saver downloads H.264 SDR films directly from Apple's
`sylvan.apple.com`, independently of the configured GenTV API. The catalogue is
bundled; there is no separate screensaver catalogue endpoint. Complete files and
partial transfers live in `files/aerial-cache`, with at most five completed videos.
Only completed files are passed to the `TextureView`/`MediaPlayer`. They play in
shuffled rounds, without immediate repeats across rounds when multiple films exist.

Each entry resumes a serial fill pass while fewer than five films are cached. The first
completed film starts local playback while subsequent films download. A film that
does not fit is refused before reading its body; playable films are never evicted
to fetch more. On later entries, existing films play locally while unfinished
downloads resume and unused slots are filled. The previous entry's film is avoided
when another complete film is available. Clear screensaver cache in Settings
removes complete files, partials and validators on a worker thread. The next saver
entry starts a fresh fill pass, rather than downloading from the settings page.

Exit cancels the transfer and retains the partial file. Resume uses Range and a
strong ETag in If-Range, checks Content-Range, and appends only matching bytes.
A full 200 response replaces the partial; without a strong validator the transfer
must restart to avoid mixing versions. Unknown lengths and files larger than the
available budget are not downloaded. Low disk space also stops filling. Download
failures retry at most twice per entry, one minute apart. Undecodable local files
are skipped for the session without automatic redownload.

The desktop frame is captured locally on entry and held until the first local
video frame fades in over 900 ms. The first download can take time; offline errors
retain the entry frame. Playback pauses and network requests stop on exit; the
decoder is retained until activity destruction after the window alpha falls linearly
from 1 to 0 over 1000 ms. Home remains visible underneath the translucent window;
the activity finishes only at zero alpha, with no additional system transition.
Stopping or losing the video surface during exit does not restore the entry image.
System-disabled animations
are respected. Video files are not bundled.

Catalogue provenance: `pengchujin/aerial-tv` commit
`269e928057b492ce698c0d36df6bde9ef8b2a9b5`,
`app/src/main/assets/aerials.json`, derived from Apple's
`https://sylvan.apple.com/Aerials/resources-16.tar`. The reduced catalogue retains
114 IDs, labels, H.264 URLs and timestamped Chinese/English place captions. See `licenses/aerial-tv-MIT.txt`. Video rights
remain with their respective owners. Real-TV decoder compatibility remains to be
verified; emulator playback is a separate check.

Apple's CDN uses Apple Root CA, absent from Android's system trust store.
`res/raw/apple_root_ca.cer` is the public DER certificate downloaded from
`https://www.apple.com/appleca/AppleIncRootCertificate.cer`, SHA-256
`B0B1730ECBC7FF4505142C49F1295E6EDA6BCAED7E2C68C5BE91B5A11001F024`,
valid through 2035-02-09. Network Security Config adds this anchor only for
`sylvan.apple.com`; hostname and certificate validation remain enabled and HTTP
is refused for this domain. Other domains keep platform trust.

The screensaver shows a lower-left 24-hour clock and the current film's localized
place description on each entry. At the reference 16:9 layout, left margin is
5.5% of width; clock and caption baselines are at 83.5% and 91% of height, with
font sizes 12.5% and 3.2% of height. Descriptions follow playback timestamps and
use Chinese for Chinese launcher locales, English otherwise. The overlay appears
with the first video frame, starts a 600 ms fade-out eight seconds later, and
stays hidden until the next screensaver entry. Loading time does not consume
those eight seconds. No additional network requests are needed for captions.

Fresh TV settings default to 5 apps per row, 0 rows below the dock and 30% dock
band opacity. Saved user choices and explicit theme settings retain precedence.

Launcher settings no longer expose the legacy local wallpaper picker. Its stored
theme override remains compatible, while desktop online rotation and the bundled
offline wallpaper keep their existing behavior.
