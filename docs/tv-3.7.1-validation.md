# TV 3.7.1 pre-release check — 2026-10-29

Scope: current local TV source and wallpaper server tests. No deployment, Git push,
default Home change, real-TV installation or phone release was performed.

## Candidate

- Application: `com.gentv.tv`, version `3.7.1`, code `23`, min SDK 26, target SDK 34.
- Formal Release APK: 834,879 bytes / 1,000,000-byte budget.
- SHA-256: `c517062137e504a59f6ae4b5a585c6c9c4f7619cece37f21d2c36471a062a2bc`.
- Signing certificate verified against the production fingerprint in `release.md`.
- API base verified in generated Release configuration: `https://api.gentv.app/tv/`.
- Distribution ZIP includes APK and applicable license texts.
- Production TV update metadata still advertises 3.7.0 / code 22; candidate is newer.

## Checks

| Check | Result |
| --- | --- |
| Debug and Release unit tests | 308 each; zero failures |
| Debug and Release assembly, R8 and lint | Passed |
| Release lint findings | 144 warnings, 134 informational; not a warning-free build |
| Android desktop integration | PASS 62 checks |
| Offline wallpaper and defaults | PASS 18 checks |
| Screensaver clock/caption lifecycle | PASS 11 checks |
| Apple HTTPS range request from Android | PASS |
| Mandatory/optional update UI | PASS 12 checks |
| Wallpaper server tests | 20 tests; 18 passed, 2 platform-specific skips |
| Branding, repository integrity and whitespace | Passed |

Android instrumentation used the Debug variant on GoogleTV_1080p, Android API 36.
The formal Release was built and signature-checked, not installed over the
debug-signed emulator app. Screen-info and offline checks preceded the version
bump; desktop integration, source HTTPS and update UI were rerun on code 23.

## Corrections during audit

- New folder IDs used `com.gentv.tv`, while validation only accepted the older
  `com.gentv.home` prefix. Accept both; newly generated IDs remain `com.gentv.tv`.
  Full native integration now passes folder creation, persistence and validation.
- Native caption assertion now accepts legitimate multiline place descriptions.
- Privacy descriptions now match the five-video cache and incremental downloads.
- Incremented the TV candidate version/code to avoid conflicting with production.

## Remaining acceptance

This is a signed candidate ready for device acceptance, not approval for all TVs.
Before publication, verify an in-place upgrade from the production-signed 3.7.0
on a real TV, retention of configuration, physical remote navigation, the
one-second screensaver exit, idle/Never behavior, HDMI, standby/wake and low-memory
behavior. Validate playback performance and five-video disk use on the target TV.
No fresh phone build, Linux deployment tests, or complete production-service audit
was performed. The working tree contains uncommitted and untracked candidate files;
review and include required source/assets/licenses in the release commit.

## Publication and production-signed upgrade follow-up

The user subsequently authorized publication. On 2026-10-29, the official site
and TV update endpoint were updated to 3.7.1 / code 23. The Chinese and English
download links, structured software version and `data/release.json` were updated.
The phone artifact remains unchanged. The update remains optional (`forceUpdate=false`).
Public APK SHA-256 and signing certificate match the candidate recorded above.

The site was deployed to a new timestamped release directory and the previous one was kept as the rollback target. Exact server paths are kept in the maintainer's private deployment record.

A separate fresh `GenTV_371_UpgradeQA` Google TV API 36 emulator was used:

1. Downloaded the public production-signed 3.7.0 APK and verified its published
   SHA-256 `57af41d35eee3f1ce4720da1b643f6a5b485264e6d818b7dd64ff0dfde1b3505`.
2. Installed 3.7.0 (`Success`) and launched it (`Status: ok`).
3. Set columns to 4 through Launcher settings as a persistence marker.
4. Used the old client's Check for updates and Upgrade now actions. It detected
   3.7.1 from the production endpoint, downloaded and verified the APK. Enabled
   the emulator's normal Install unknown apps permission for GenTV.
5. Completed the client/system installation flow. No ADB install command for the
   new APK, uninstall or data clear was used on this emulator.
6. Package readback and update UI confirmed 3.7.1 / code 23. Columns remained 4,
   the new screensaver setting appeared, and the removed wallpaper picker was absent.

Relevant raw package readback:

```text
versionCode=23 minSdk=26 targetSdk=34
versionName=3.7.1
lastUpdateTime=2026-10-29 23:27:02
firstInstallTime=2026-10-29 23:24:16
```

Default Home before and after remained:

```text
com.google.android.apps.tv.launcherx/.home.HomeActivity
```

This confirms the production-signed in-app upgrade on this emulator. Real-TV
firmware, physical remote, standby/wake and performance acceptance remain separate.


## Production upgrade focus regression — 2026-10-29

A second fresh Google TV API 36 / 1920×1080 emulator,
`GenTV_371_FocusQA`, was used for this regression. Installed the verified
production-signed 3.7.0 APK (SHA-256 recorded above), selected four columns,
then used Check for updates → Upgrade now → the Android package installer.
The live production endpoint returned version 3.7.1 / code 23 and the same
candidate SHA-256 recorded above. Only the initial 3.7.0 installation used
`adb install`; the new version was downloaded and verified by the old client.
No uninstall, data clear, downgrade, default-Home change or source fix was needed.

Raw installation / launch / package results:

```text
Performing Streamed Install
Success
Status: ok
Activity: com.gentv.tv/.MainActivity

Android installer: App installed.
versionCode=23 minSdk=26 targetSdk=34
versionName=3.7.1
lastUpdateTime=2026-10-29 23:40:42
firstInstallTime=2026-10-29 23:38:54

Home before and after:
com.google.android.apps.tv.launcherx/.home.HomeActivity
```

Post-upgrade tests used Android DPAD key events (19/20/21/22), Center (23),
long Center and Back (4). UI hierarchy focus attributes and screenshots were
collected in the ignored `shots/focus-upgrade-371/` directory.

| Focus path | Observed result |
| --- | --- |
| Cold start | First app card focused |
| Four-column grid | Down: card 0 → 4; Right: 4 → 5; Left: 5 → 4; Up: 4 → 0 |
| Bottom-row boundary | Down remains on the existing last-row card |
| App long-press menu | Opens focused; Down moves to next action; Back restores app-card focus |
| Header | Network ↔ Control center; visible focus follows direction keys |
| Control center | First entry focused; Down/Up moves between entries; Back restores header-button focus |
| Launcher settings | Four-column marker retained and focused on entry; Down moves to Rows; Up returns to Columns; Back restores app-card focus |
| Update page | Check for updates focused on entry; Left/Right switches Later/check; Down/Up switches statistics/check |
| Home Back | From another card, returns to first card |
| Screensaver | Back on first card opens AerialScreensaverActivity; Right exits to MainActivity and first card regains focus |
| Crash buffer | Empty after these checks |

Final screensaver activity and focus readback:

```text
topResumedActivity=ActivityRecord{29834737 u0 com.gentv.tv/.AerialScreensaverActivity t7}
topResumedActivity=ActivityRecord{157059923 u0 com.gentv.tv/.MainActivity t7}
FOCUS [('com.gentv.tv:id/card', '[144,785][534,1004]')]
```

Result: no stuck or lost focus observed in these tested post-upgrade paths.
This is emulator DPAD validation, not physical-TV remote or firmware acceptance.
Screensaver exit was checked after a 1.5-second wait, not measured as an exact
latency benchmark. No production deployment or code modification was performed.
Repository branding/integrity checks, wallpaper tests (20; two skipped), and
`git diff --check` passed. Turn ADB debugging off after real-device maintenance.


## Emulator remote input — 2026-10-30

On `GenTV_371_FocusQA`, the emulator window's remote and keyboard moved no focus.
The AVD had `hw.keyboard=no`, so Android had no input device that declared the
D-pad, Enter, Back or Home keys (`getevent -pl`). Apps received no keys from the
emulator UI at all, so the problem was not specific to GenTV. `adb shell input keyevent`
still worked, because it injects through InputManager.

After setting `hw.keyboard=yes` and a cold boot, the `qwerty2` device appeared. Keys
sent through the emulator's gRPC `sendKey` (the same path as the window's remote)
were tested on the installed production 3.7.1 APK, unchanged
(SHA-256 `c517062137e504a59f6ae4b5a585c6c9c4f7619cece37f21d2c36471a062a2bc`):
Right moved across the four cards, Up/Down moved between the cards and the header,
Enter opened the focused app or settings page, Back returned to GenTV, and Home
went to the stock Home. Default Home stayed
`com.google.android.apps.tv.launcherx/.home.HomeActivity`.

Minor, non-blocking: after a mouse click (touch mode) focus rests on the app grid
without a visible ring, and the first Right goes to the header Wi-Fi button rather
than the first card. Use `hw.keyboard=yes` on TV AVDs used for remote testing.
