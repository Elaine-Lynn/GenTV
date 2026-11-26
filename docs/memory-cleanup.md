# Launcher memory cleanup

The Control Center memory button runs a user-initiated, local-ADB cleanup and
shows a native Canvas line-art rocket. It does not need root, Shizuku, a server,
or a rendered video asset.

## Mechanism and Android versions

Android 14 and later restrict `ActivityManager.killBackgroundProcesses()` to the
caller's own processes. Earlier versions expose it with the normal
`KILL_BACKGROUND_PROCESSES` permission, but process enumeration is restricted and
the API is not a reliable cross-version implementation for other applications.

GenTV reuses its existing loopback-only legacy ADB transport and locally generated
identity. The application reads the actual legacy TCP port reported by the TV, falling
back only to a previously supplied port. There is no port-entry dialog. The owner
accepts Android's authorization prompt if needed. Only shell
UID 2000 is accepted. Wireless-debugging TLS pairing is not implemented by this
transport; failure is shown explicitly.

The command is `am kill-all`: ActivityManager chooses cached/background processes
that are safe to kill. It is intentionally not `am force-stop`, which completely
stops a package, and not arbitrary PID killing. Foreground/important services stay
under Android's protection. No packages are disabled, uninstalled or cleared.
Some background services can survive, and killed apps can restart when needed.

The implementation snapshots application process identities (`USER,PID,NAME`)
before and after the command, waits 900 ms, and reads system available memory via
`ActivityManager.MemoryInfo.availMem`. The MB value is the nonnegative observed
change, rounded down in units of 1024 * 1024 bytes. Concurrent system activity
can affect this number: it is not an exact sum of terminated processes' heaps.
Process exits are observed differences, not a claim that every exit was caused
by the command. Without an observed application exit, the displayed MB is zero
even if available memory fluctuates upward. Zero is a valid result. Command/connection errors never show a
success message. Back cancels pending work and closes the connection; a command
already delivered to Android cannot be undone.

The desktop remains visible through a lightly dimmed transparent overlay; the
Control Center panel remains visible beneath the rocket throughout cleanup. No desktop
artwork is trimmed during the effect. Animation lasts approximately 2.2 seconds (660 ms ignition shake, 440 ms slow lift,
then 1100 ms accelerating flight beyond the top edge) and the result waits for the cleanup
operation to finish. Reduced-motion settings use a static rocket. The result
closes after 1.8 seconds, or on OK/Back, restoring the button's focus.

## Primary references

- [ActivityManager API](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String))
- [Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all#kill-background-processes)
- [AOSP ActivityManager shell commands](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/ActivityManagerShellCommand.java)

Real-TV compatibility still depends on the firmware's ADB availability and owner
authorization. An emulator result does not establish real-TV compatibility.

## Validation (2026-11-24)

Android 16 TV emulator: the complete button → local ADB → kill → result flow
observed five application process exits and 39 MB more available memory. The
ActivityManager log confirmed `kill all background` for those PIDs, including
the development example application. Subsequent runs with no eligible processes
can legitimately release zero. Android 9 emulator: the configured legacy port
was not listening; the UI showed a connection failure rather than success.
No real TV was changed for this feature validation.
