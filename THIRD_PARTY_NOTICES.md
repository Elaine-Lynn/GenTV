# GenTV third-party notices

GenTV is built on modified open-source projects. GenTV's own work is copyright (c) 2026 Elaine-Lynn and the GenTV contributors under the MIT License in [LICENSE](LICENSE). The projects below keep their original copyrights and licences; GenTV branding and application identifiers do not replace them, and none of these projects endorses GenTV.

## Origins

| Project | Licence and copyright | What GenTV uses | Baseline |
|---|---|---|---|
| [cleanyourtv](https://github.com/Gathin23/cleanyourtv) | MIT, copyright (c) 2026 Gathin — [LICENSE](LICENSE) | The native TV home screen in `launcher/` (focus, scrolling, module cards, theme provider); the per-model device lists in `phone/framework/data/devices/`; the `default`, `aurora`, `glass` and `scenes` themes in `phone/framework/data/themes/`, including their wallpaper and preview images | `57733360177173c880a023380a864114a03feb25`, with local changes |
| [TidyTV](https://github.com/jolan94/tidytv) | MIT, copyright (c) 2026 Winkle Studios and the tidytv contributors — [mobile-framework-MIT.txt](licenses/mobile-framework-MIT.txt), [phone/framework/LICENSE](phone/framework/LICENSE) | The phone framework in `phone/framework/` (ADB connection, list parsing, package protection, backup and restore) and parts of the phone app | `309515d209106715fa86aa3f07468bd59da3e3aa` (0.9.0) |
| [tv-detox](https://github.com/Ansub/tv-detox) | MIT, copyright (c) 2026 Ansub Khan — [legacy-integration-MIT.txt](licenses/legacy-integration-MIT.txt) | Install, undo and check patterns carried into the phone framework through TidyTV. Its home-screen application and artwork are not included. | — |

GenTV has since changed package namespaces, integration, UI, delivery, updates and the server. The current code is not an unchanged checkout of any of these projects.

## Libraries, fonts and icons

| Component | Licence | Notes |
|---|---|---|
| [Kadb](https://github.com/Flyfish233/Kadb) | Apache License 2.0 — [Apache-2.0.txt](licenses/Apache-2.0.txt) | ADB client used by the phone |
| AndroidX, Kotlin and the Gradle wrapper | Apache License 2.0 | Other build and test dependencies keep their own licences; all are declared in the Gradle build files with their real coordinates |
| [Outfit](https://github.com/Outfitio/Outfit-Fonts) | SIL Open Font License 1.1, copyright 2021 The Outfit Project Authors — [Outfit-OFL.txt](licenses/Outfit-OFL.txt) | TV typeface |
| [Nunito](https://github.com/googlefonts/nunito) | SIL Open Font License 1.1, copyright 2014 The Nunito Project Authors — [Nunito-OFL.txt](licenses/Nunito-OFL.txt) | Phone typeface |
| [Simple Icons](https://simpleicons.org) | CC0 1.0 | App marks in `phone/framework/assets/icons.json`, used only to identify third-party apps |

## Third-party names

Television manufacturers, system services, streaming apps and their package identifiers appear where GenTV must recognise, protect or display them. These names and trademarks belong to their owners. The device lists are inherited community records, not GenTV compatibility certification.

Online wallpapers served by the GenTV wallpaper service are not part of this repository.
