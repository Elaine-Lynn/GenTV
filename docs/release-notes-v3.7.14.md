# GenTV TV v3.7.14 temporary update bridge

This branch starts from `main` at `b03b1f0` and advances the TV app to version
3.7.14 / code 36. It keeps the Android 4.2 / API 17 minimum and the existing
production signing certificate.

The v3.7.12 TV updater rejects APKs larger than 8 MiB. The v3.7.13 package is
18,655,742 bytes, so v3.7.12 cannot download it. This build retains both
bundled motion wallpapers while retaining H.264 Baseline 1080p/30 fps,
compresses the three bundled glass wallpapers as WebP, and recompresses the
pond background. The finished APK is 8,057,421 bytes, below the 8 MiB
(8,388,608-byte) limit. A release build task
fails if the APK grows past the legacy 8 MiB limit.

The v3.7.14 updater itself retains the larger 128 MiB limit, allowing later
full-size releases with version codes above 36. The update service must publish
a v3.7.14 / code 36 manifest and signed APK before v3.7.12 clients can see it.
The service currently advertises v3.7.13 / code 35; no server change is part
of this branch.

On a tested Android 9 TV, the router-provided DNS server does not resolve
`api.gentv.app`, although public DNS has valid A records and
the API responds through the computer's configured HTTP proxy. DNS resolution
on the TV network must be repaired separately before its in-app update check
can reach the service. This build has not been installed on that TV.
