# GenTV Remote capabilities

| Capability | Implementation |
|---|---|
| Discovery, manual connection and pairing | Authorised ADB transport |
| Inspection, backup, cleanup and restore | Shared framework; protected-package and recovery rules retained |
| Remote, touchpad, keyboard and file transfer | Existing connection and user-selected operations |
| GenTV installation | One source-built, identity-checked TV package; default Home handed over in stages only after the user confirms GenTV on the TV, with the original Home recorded; preview keeps Home |
| Desktop configuration | Read, local draft, save, undo, profile import and export |
| Updates, feedback and optional analytics | GenTV endpoints; signature checks and opt-in boundaries retained |
| Logs and share cards | GenTV presentation; no inherited product mascot |

Framework packages have moved to `com.gentv.core` and `com.gentv.adb`; application IDs and signing configuration remain stable. Historical installed-package identities are retained only where used to recognise existing records and applications. The device lists are fixed community references, not certification of all televisions.

The source contains no prebuilt APKs. Release builds require private signing configuration. Validation commands are in [CONTRIBUTING.md](../CONTRIBUTING.md); outstanding real-TV acceptance is listed in [release](../docs/release.md).
