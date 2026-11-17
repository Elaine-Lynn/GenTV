# GenTV desktop management

GenTV Remote manages the GenTV TV receiver `com.gentv.tv/com.gentv.tv.DesktopReceiver` over the owner's authorised ADB connection. It can read state, edit a local draft, save, undo and produce a validation report.

The desktop owns its configuration and revision. A stale revision is refused; names, ordering, hidden applications, folders, input labels and local pictures are validated before applying. Profiles of any other schema are refused, and ambiguous data is not guessed.

Current protocol: [desktop-protocol.md](../../docs/desktop-protocol.md). Build the phone according to [README.md](../README.md). The source-built TV APK is verified before installation. Installing alone leaves the default Home unchanged; once the user confirms GenTV on the TV, the app hands Home over in stages, recording the original Home for restore (see [getting started](../../docs/getting-started.md#connect-and-install-with-the-cli)). Preview-only installation keeps the existing Home. Check the physical remote, reboot, HDMI and recovery on the actual TV before accepting it.
