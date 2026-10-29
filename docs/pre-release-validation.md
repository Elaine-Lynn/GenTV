# Pre-release validation — 2026-10-29

This is a local source audit, not certification of every supported device or a fresh check of production services.

## Reproducible source build

A temporary directory was populated with Git-tracked files, including the pending wrapper/checksum changes, without keys, local.properties, existing build output or private screenshots. The build used installed Android SDKs and shared Gradle dependency/toolchain caches. This verifies a clean source tree, not a completely new operating-system installation or a cold network download of every dependency.

| Check | Result |
|---|---|
| TV unit tests | 280 tests, no failures or skips |
| Phone/app/core/ADB unit tests | 469 discovered, 468 passed, 1 emulator-dependent test skipped |
| TV Debug lint and APK | Passed; 137 warnings and 126 informational findings |
| Phone Debug lint and APK | Passed; 26 warnings |
| Embedded TV delivery | Source-built, debug-signed single GenTV APK plus SHA-256 manifest generated without release credentials |
| Repository branding and framework integrity checks | Passed |
| Server unit tests | 18 discovered, 16 passed, 2 Linux/GNU deployment tests skipped on macOS |
| Diff whitespace check | Passed |

Both Android build commands are in [the installation guide](getting-started.md). The full build results above describe the initial clean-source audit. A later change corrected the phone English cleanup label from an internal code name to “Clean up”; HomeScreenTest and LanguageTest were rerun (6 tests passed), and the homepage preview was regenerated. No new official signed phone APK was produced by this audit.

## Functional coverage and limits

| Area | Evidence | Remaining acceptance |
|---|---|---|
| TV home screen, navigation, themes and configuration | Implemented source, JVM tests, Debug build | Actual remote, input/HDMI, sleep/wake and firmware-specific Home behaviour |
| Phone discovery, ADB transport and pairing | Framework/ADB tests; previous device checks recorded separately | Fresh installation and pairing on each target device/network |
| Inspection, cleanup, backup and restore | Framework tests for protection, logging and recovery outcomes | Device-specific package choices and real recovery; never infer safety from a community list alone |
| Remote controls and file transfer | Unit/UI tests; historical real-device results in phone release acceptance | Full current-release real-device regression |
| GenTV TV installation and desktop management | Installer, package/asset integrity and desktop protocol tests | Emulator installation test skipped in this run; no real device was installed or changed |
| CLI installation | Uses standard adb commands; complete build/install/verification instructions added | Commands were not executed against a real device in this audit; no standalone GenTV CLI product exists |
| AI-assisted setup | A shared guide specifies environment, serial selection, build, installation and verification | Assistant follows the documented CLI workflow; no installable AI skill/plugin is included |
| Online updates, wallpapers, feedback and optional analytics | Client implementation and local server tests | Production TLS, manifests, APK/media hosting and live end-to-end operation were not checked |
| Official signed release | Existing signing gates and historical release acceptance | This run built Debug artifacts without private keys; no new official release was signed/published |
| CI and portability | CI definitions are included; macOS source build passed | Fresh GitHub Actions run, Windows build/install and Linux deployment rollback tests |

Historical real-device evidence is in [phone release acceptance](../phone/docs/release-acceptance.md). Those results should not be described as a fresh test of the current release or of other television models.

## Follow-up screenshot capture

A dedicated read-only TV emulator was subsequently used to install and launch the source-built Debug APK and capture eight wallpaper variants. Configuration readback confirmed five columns, five pinned apps and `dock_opacity=30`. This verifies that screenshot setup and emulator launch; it does not replace the skipped desktop integration test or real-device acceptance. The emulator was closed afterward.

## Remaining review items

- Lint succeeds but is not clean. Most TV findings concern resources, layout, localisation or deprecated APIs. Phone dependency lint includes three `TrustAllX509TrustManager` warnings originating in Kadb/Bouncy Castle artifacts. They require a separate review of the ADB pairing/transport trust model; this audit did not establish an exploitable HTTPS bypass or resolve those dependency warnings. Do not disable app HTTPS certificate checks to address pairing failures.
- Some inherited device-list comments reference upstream documents and tools absent from this repository. Treat those as historical references, not working local instructions or complete device certification.
- First-time dependency download, SDK licence acceptance, device authorisation and Android installer prompts still need the local environment/user interaction described in the setup guide.
- Online features intentionally remain unconfigured in default source builds. A successful local build alone does not provide a running backend, wallpaper collection or update release manifest.

The repository is buildable and its tested code paths pass. A claim that all features work on all devices, or that unattended one-command installation is available, is not supported by this audit.
