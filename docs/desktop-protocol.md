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

The online wallpaper feed is separate from the desktop layout protocol: https://api.gentv.app/tv/wallpaper, metadata `wallpapers: [{id, url}]`, fixed120-second foreground rotation. Runtime feed data and the last downloaded image do not enter schema2 exports or undo. User desktop layout, local theme settings and their revisions remain authoritative as before. Empty feeds restore the underlying theme; network failures retain the last good background. Only the independentGenTV app gains INTERNET; no device identifiers, HTTP server or background service is introduced. See the server README for endpoint and deployment details.
