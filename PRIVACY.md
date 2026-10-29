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
| Anonymous usage statistics | Only after you switch them on. They are off by default. | GenTV version, and the number and foreground length of home-screen sessions |

## GenTV Remote (phone)

| Request | When | What is sent |
|---|---|---|
| Update check | At most once a day when the app starts, and when you select *Check for updates* in Settings | The request for the update list |
| Update download | Only after you choose to download | The request for the release file, verified by size and SHA-256 before installation |
| Feedback | Only when you submit the feedback form | Your message and feedback type, the page you sent it from, an email address if you enter one, phone model, Android version, app version, a random install ID and any log or screenshot you choose to attach |
| Anonymous usage statistics | Only after you switch them on in Settings. They are off by default and never sent from debug builds. | Fixed feature events (for example a screen opened, or whether a connection or file transfer succeeded), app version, Android SDK level and a random ID that changes every session |

Statistics never include IP or MAC addresses, serial numbers, TV names, Wi-Fi names, app lists, files, logs, screenshots or text you type. Turning statistics off clears unsent events. Requests that have already been sent cannot be recalled by the app.

## Data that stays on your devices

TV connection keys, backups, restore records, operation history and desktop drafts stay on the phone unless you choose to export or share them. Shared reports are redacted before export. The phone talks to your TV directly over your local network after you authorise ADB.

## Optional Private DNS filtering

If you enable ad filtering in GenTV Remote, the app configures the TV to use AdGuard DNS (`dns.adguard-dns.com`). The TV then sends DNS lookups to that third-party provider, which can see the queried domain names and the connection source IP. Those lookups are not sent to the GenTV service. Filtering depends on the provider and the app; it does not remove every in-app ad. The previous DNS settings are recorded for restoration.

## Retention on the GenTV service

- Phone usage events are kept for 30 days and then deleted; only daily totals are kept after that.
- Feedback is kept for 30 days and then deleted.
- TV usage statistics are stored only as daily totals per app version.
- The server backup tool keeps the latest 14 database snapshots. A snapshot can still contain records removed from the live database until that snapshot expires; live-database deletion does not immediately remove those backup copies.
- As with any network connection, the service sees the IP address of each request. GenTV does not store it in statistics or feedback records.

The [server source](server/wallpaper/) shows exactly what is stored.

## Contact

For questions or deletion requests, email [support@gentv.me](mailto:support@gentv.me). Email is the only way to reach GenTV about data. Feedback is not linked to an account, so a deletion request should include enough detail (for example the email address you entered) to find it.

The aerial screensaver downloads videos directly from sylvan.apple.com (Apple), which receives normal network request metadata including your IP address. It opens when you press Back on Home or leave Home idle for the selected interval (2 minutes by default). Video requests stop when the screensaver closes; the desktop uses image wallpapers. No video files are bundled. Complete videos and interrupted downloads are stored privately on the TV. Up to five complete videos are cached; downloads stop when disk space is insufficient. Cached videos play locally in shuffled rounds. Later entries resume interrupted downloads and fill unused cache slots. Settings includes Clear screensaver cache; after clearing, downloads restart the next time the screensaver opens.

Online wallpaper images are cached privately up to 64 MiB. Rotation reads local images; new image response bodies have a 16 MiB budget per 24-hour window, including failed transfers. Manifest checks use ETag at six-hour intervals after success. Download history, failure cooldowns and traffic budgets are stored only on the TV and survive app restarts.
