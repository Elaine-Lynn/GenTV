# TV input compatibility

GenTV uses the standard Android TV passthrough input URI for HDMI and AV. Model-specific routes also support vendor actions and source numbers, a vendor source panel (activity or service), Coocaa's source provider, YaOS URI schemes and an explicit Live TV player/tuner. These routes are inherited community references; they are not certification for every model of a brand.

## Shared model catalog

`phone/framework/data/source-profiles.json` contains 72 source-routing profiles. The TV APK and phone APK both bundle this same file. Profiles include the applicable records from the upstream v3.2.0 snapshot named in `THIRD_PARTY_NOTICES.md` (`42fe7b09d61fb9175cb03b8c659cd63c9c1dd9a4`), existing local records and the documented XGIMI Horizon S Pro and Vidda MT9653 routes. Licensing is recorded in `THIRD_PARTY_NOTICES.md`.

Existing model files remain under `phone/framework/data/devices/`. Additional source-only references live under `phone/framework/data/input-profiles/`; they do not introduce cleanup lists or package-disable operations. The catalog test checks routing fields against those files.

Selection requires a matching brand/manufacturer and a specific model or codename. When a profile specifies an Android major version, that must also match. Shared board identifiers with conflicting routes cause automatic selection to abstain. Unknown devices keep standard Android behavior; no vendor source numbers are inferred from a brand alone.

For standalone TV installation, the selected route supplies defaults before the owner's `config.json` overlay. For phone installation, missing routing fields are written and read back before the desktop opens. Existing explicit fields take precedence. No accessibility service or additional permission is enabled by selecting a profile.

## Entry and configuration behavior

- Settings can open Inputs even when the optional header button is hidden. The menu anchors to Settings in that case. If there are no inputs or configured vendor routes, it reports that no inputs were found.
- The compact header shows an HDMI reminder only for a visible passthrough HDMI input reporting connected. AV, disconnected/standby ports and vendor-panel availability do not activate the reminder. Settings retains the full Inputs menu regardless of reminder visibility. The noncompact header retains the general Inputs entry.
- Changing wallpaper/theme or rewriting dock apps preserves existing hardware routing fields, including empty strings and false values. A theme cannot overwrite the owner's existing route.
- Configuration reads use the permission-protected theme provider on firmware that hides Android/data from ADB. Read failures stop configuration replacement; configuration writes are read back before proceeding.
- Explicit `live_tv_input_id: "content://android.media.tv/channel"` supports the upstream MediaTek root-channel route for returning from HDMI to broadcast TV.

## Validation limits

JVM tests cover profile matching, conflicting boards, Android version restrictions, catalog consistency, routing preservation and phone delivery order. The Android instrumentation option `input_sources_only=true` exercises vendor-panel availability and a Settings request with the header hidden, using a local activity fixture.

The DBB3 Pro / Android 9 test device has no `tv_input` service. It passed the seven instrumentation assertions, but cannot establish HDMI picture, sound, CEC or vendor-player behavior on a television. Those require a real TV with input hardware, the correct firmware and owner confirmation. Turn ADB debugging off after device testing.
