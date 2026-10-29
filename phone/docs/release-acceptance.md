# Mobile release acceptance

Real-device checks for GenTV Remote (`com.gentv.mobile`) up to 3.7.1/code17, run on one Android 15 phone and one Android 9 TV box. They do not certify other phones or TVs. No device identifiers, addresses, Wi-Fi names or account information are recorded here.

## Confirmed on real devices

- The phone discovered the TV and connected over authorised ADB. Up/down keys sent from the phone moved focus on the TV, and the phone listed the TV's launchable apps.
- A small test file was sent to `Downloads/GenTV`; a separate read on the TV matched it byte for byte and by SHA-256. Only that test file was removed afterwards.
- After these tests, the TV's installed packages, disabled packages and default Home matched the baseline recorded beforehand. No app was disabled or uninstalled and the default Home was not changed.
- Feedback counts as sent only after an explicit `success: true` reply. One test submission was confirmed on the server and then deleted.
- Anonymous statistics were switched on briefly to confirm one update-check event, then switched off.
- In-app update from 0.9.0/code11 to 0.9.2/code13, both signed with the same release key. Android required installation from the app to be allowed by hand; no permission bypass was used. Download and APK verification completed before installation was requested.
- Returning from the system authorisation screen after the app process was recreated: pending update metadata is kept in private cache and fully revalidated. Restore and tamper cases have regression tests.
- 0.9.3/code14 was installed over USB on top of the previous version and launched with the GenTV Remote name.
- Release packaging fails when the private signing configuration is absent.

## Not yet covered

- Losing the network, cancelling a download and cancelling installation on a real phone.
- A full real-device regression of the current 3.7.x release, and any other phone or TV model.
- The opt-in TV-emulator test, which is skipped by default.
