# Privacy

English · [简体中文](PRIVACY.zh-CN.md)

This page describes the network use of official GenTV builds. Builds made from this source without a configured GenTV API base, including forks, never contact the GenTV service.

GenTV has no accounts, advertising or third-party analytics SDKs.

## GenTV TV

| Request | When | What is sent |
|---|---|---|
| Update check | At most once a day from the home screen, or when you select *Check for updates* | The request and an app version in the `User-Agent` header |
| Update download | Only after you choose to download | The request for the release file. The APK's SHA-256, package name and signing certificate are verified before Android asks you to confirm installation. |
| Online wallpapers | While the home screen shows online wallpapers | Requests for the wallpaper list (refreshed about every six hours) and the images. Downloaded images are cached on the TV. |
| Usage statistics | Off by default in schema 3; enable in Settings after reviewing the revised disclosure. | When enabled: a local factory MAC if the OS permits access, a random installation ID, session ID, fixed page/action events, event time and order, app version, Android SDK and foreground duration. The ID stays local across upgrades and is reset on opt-out, clearing app data or reinstallation. Debug/emulator or manually marked reports are test traffic. |

## GenTV Remote (phone)

| Request | When | What is sent |
|---|---|---|
| Update check | At most once a day when the app starts, and when you select *Check for updates* in Settings | The request for the update list |
| Update download | Only after you choose to download | The request for the release file, verified by size and SHA-256 before installation |
| Feedback | Only when you submit the feedback form | Your message and feedback type, the page you sent it from, an email address if you enter one, phone model, Android version, app version, a random install ID and any log or screenshot you choose to attach |
| Usage statistics | Off by default in schema 3; enable in Settings after reviewing the revised disclosure. Debug builds never send statistics. | When enabled: a local factory MAC if the OS permits access, a random installation ID, a changing session ID, fixed page/feature events and their order/time, app version, Android SDK and foreground duration. No typed content is sent. Emulator and manually marked reports are test traffic. |

The server immediately converts valid MAC addresses to an app-scoped HMAC using a random server secret and does not store or display raw MACs. MAC hashes take priority over installation IDs for deduplication and may link reinstallations. Empty, placeholder, randomized or inaccessible MACs fall back to installation IDs. MAC-based matching is not a guarantee of physical device counts: network interfaces can differ. Updating from the old collection requires enabling statistics again; old MAC-less records cannot be linked across reinstallations. Disabling does not remove historical links; contact GenTV to request remote-data deletion. Statistics never include IP addresses, serial numbers, TV names, Wi-Fi names, app lists, files, logs, screenshots or text you type. Turning statistics off clears unsent events and resets the statistics installation ID. Upgrades preserve this app-local ID; clearing data or reinstalling resets it. Upgrades preserve an explicit choice to disable statistics. Requests that have already been sent cannot be recalled by the app.

## Data that stays on your devices

TV connection keys, backups, restore records, operation history and desktop drafts stay on the phone unless you choose to export or share them. Shared reports are redacted before export. The phone talks to your TV directly over your local network after you authorise ADB.

The TV app’s local ADB activation connects only to the TV’s loopback address after your confirmation, to enable the child timer accessibility service. Its separate ADB key stays in the TV app’s private, backup-excluded storage. The previous service settings are recorded locally for recovery and are not sent to GenTV servers. This feature does not start network debugging or grant the app general permission to write secure settings. Network debugging can be turned off afterwards.

## Optional Private DNS filtering

If you enable ad filtering in GenTV Remote, the app configures the TV to use AdGuard DNS (`dns.adguard-dns.com`). The TV then sends DNS lookups to that third-party provider, which can see the queried domain names and the connection source IP. Those lookups are not sent to the GenTV service. Filtering depends on the provider and the app; it does not remove every in-app ad. The previous DNS settings are recorded for restoration.

## Retention on the GenTV service

- Phone usage events are kept for 30 days and then deleted; only daily totals are kept after that.
- Feedback is kept for 30 days and then deleted.
- Legacy TV usage statistics remain daily totals per app version, without an installation ID.
- Schema 2/3 event paths and session records are retained for 30 days. Daily per-installation activity is retained for 400 days. The server stores installation ID hashes, keyed MAC hashes and their mappings, not raw MACs or installation IDs; first/last observation metadata remains until 400 days of inactivity. TV and phone installation IDs are separate and not linked.
- The authenticated report shows aggregate new-installation, active-installation, retention, foreground-duration and path statistics. A new installation means first observed reporting, not an independently verified physical device or install timestamp. Test traffic is excluded by default.
- The server backup tool keeps the latest 14 database snapshots. A snapshot can still contain records removed from the live database until that snapshot expires; live-database deletion does not immediately remove those backup copies.
- As with any network connection, the service sees the IP address of each request. GenTV does not store it in statistics or feedback records.

The [server source](server/wallpaper/) shows exactly what is stored.

## Contact

For questions or deletion requests, email [support@gentv.me](mailto:support@gentv.me). Email is the only way to reach GenTV about data. Feedback is not linked to an account, so a deletion request should include enough detail (for example the email address you entered) to find it.

The aerial screensaver downloads videos directly from sylvan.apple.com (Apple), which receives normal network request metadata including your IP address. It opens when you press Back on Home or leave Home idle for the selected interval (2 minutes by default). Video requests stop when the screensaver closes; the desktop uses image wallpapers. No video files are bundled. Complete videos and interrupted downloads are stored privately on the TV. Up to five complete videos are cached; downloads stop when disk space is insufficient. Cached videos play locally in shuffled rounds. Later entries resume interrupted downloads and fill unused cache slots. Settings includes Clear screensaver cache; after clearing, downloads restart the next time the screensaver opens.

Online wallpaper images are cached privately up to 64 MiB. Rotation reads local images; new image response bodies have a 16 MiB budget per 24-hour window, including failed transfers. Manifest checks use ETag at six-hour intervals after success. Download history, failure cooldowns and traffic budgets are stored only on the TV and survive app restarts.
