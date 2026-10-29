# GenTV implementation and validation

Current public source: [Elaine-Lynn/GenTV](https://github.com/Elaine-Lynn/GenTV). TV identity: `com.gentv.tv`; phone identity: `com.gentv.mobile`; phone debug identity: `com.gentv.mobile.debug`.

The TV source is under `launcher/`. The phone source is under `phone/`, including the shared framework and offline device data. The phone builds the current GenTV TV APK from source and verifies its identity, signing certificate and hash before embedding it. It does not deliver alternative desktop products.

Desktop requests use the authorised ADB shell and the permission-protected receiver described in [desktop-protocol.md](desktop-protocol.md). No receiver permission is relaxed by the branding migration. Legacy schema input and installed-package recognition remain explicit compatibility paths; they do not redefine current GenTV identity.

The website is `https://www.gentv.me`. The API base is build configuration (`gentvApiBaseUrl`); official releases use `https://api.gentv.app/tv/`, and builds without it have no online features. Configuration in source does not independently verify every deployed endpoint.

Validation commands are in [CONTRIBUTING.md](../CONTRIBUTING.md#before-opening-a-pull-request). Historical measurements remain in [emulator-performance.md](emulator-performance.md); they are not new measurements or a comparison against a device verified today. Optional local performance baselines are not part of the distributed product tree.

Existing user data, signing keys, application IDs and update streams are retained. Release version numbers must be incremented for a later published update.
