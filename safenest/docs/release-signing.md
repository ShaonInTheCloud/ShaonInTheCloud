# SafeNest upload signing

The ordinary CI job produces debug APKs and an **unsigned** Play AAB. The separate `SafeNest signed Play bundle` workflow produces a certificate-verified `playRelease` AAB only after the owner supplies upload signing configuration. It does not upload to Play or enable payments.

## Owner configuration

Create or reuse the intended Play upload key on an owner-controlled computer. Use Android Studio's signing flow; preserve the key, alias and passwords securely and keep a separate backup. When Play App Signing is enabled, the upload certificate differs from Google's distribution/app-signing certificate. Existing debug APKs are not compatible release upgrades.

In GitHub, configure the `android-release` environment with access restricted to `main` and the owner's desired environment protection rules. Put these values in **environment secrets**, never Git or workflow input fields:

| Secret | Value |
|---|---|
| `SAFENEST_UPLOAD_KEYSTORE_BASE64` | Base64 of the intended upload keystore |
| `SAFENEST_UPLOAD_STORE_PASSWORD` | Keystore password |
| `SAFENEST_UPLOAD_KEY_ALIAS` | Intended private-key alias |
| `SAFENEST_UPLOAD_KEY_PASSWORD` | Alias password |

Set the environment variable `SAFENEST_UPLOAD_CERT_SHA256` to the public upload certificate SHA-256 (64 hex digits, with optional colons). Compare it independently with the intended upload certificate in Play Console/Android Studio. Do not configure the Google app-signing certificate here.

Do not paste the keystore or passwords in chat. They are read only during the manual signing step. Missing configuration or a certificate mismatch fails before Gradle signs a release. The temporary keystore is removed when that step exits. It is excluded from every artifact.

## Build and verify

After the normal release checks for the exact source pass, manually run `SafeNest signed Play bundle` from `main`; enter the exact reviewed version name. The job runs core/signing regressions, Play release unit tests and lint, then builds `bundlePlayRelease`.

The verifier checks the actual merged package, version/code, backup and cleartext policy, absence of debug/test-only flags and Device Administrator receiver, and expected permission-protected services. It reads every AAB payload entry through the JDK JAR signature verifier, requires a single signer matching the intended upload certificate, and writes the AAB SHA-256 and public release evidence. An unsigned bundle, a mismatched signer or a changed payload is rejected.

Download `safenest-signed-play-release` from the successful run. Upload the checked AAB to an internal Play track only after the remaining review-access, legal/contact, privacy, payment and device checks are complete. This workflow does not bypass those launch requirements. No signed release has been produced while the owner's configuration is unavailable.

Direct-distribution APK signing needs a separate distribution/upgrade plan: a Play-installed app signed by Google's app-signing key cannot be updated with an APK signed only by the upload key. This workflow intentionally generates the Play bundle only.

Primary references: [Android app signing](https://developer.android.com/studio/publish/app-signing), [GitHub environment configuration](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

## 0.4.12: two release keys

Release signing now reads two independent keys from the environment, each applied only to its own flavor's release build (debug builds keep the debug key):

| Flavor | Use | Environment variables |
|---|---|---|
| `play` | AAB uploaded to Google Play (Play App Signing re-signs for users) | `SAFENEST_UPLOAD_KEYSTORE`, `SAFENEST_UPLOAD_STORE_PASSWORD`, `SAFENEST_UPLOAD_KEY_ALIAS`, `SAFENEST_UPLOAD_KEY_PASSWORD` |
| `direct` | Website APK and Strong lock QR enrollment | `SAFENEST_DIRECT_KEYSTORE`, `SAFENEST_DIRECT_STORE_PASSWORD`, `SAFENEST_DIRECT_KEY_ALIAS`, `SAFENEST_DIRECT_KEY_PASSWORD` |

Public certificate fingerprints (SHA-256):

- Upload: `64:59:CC:EC:36:32:00:31:7A:E0:AD:AC:C6:5F:64:D4:B1:D6:A2:97:BC:BA:D2:A2:CE:0D:32:FD:73:E5:CA:D9`
- Direct: `E1:72:5D:0F:C6:23:C0:2A:69:93:FD:21:23:EA:8B:B1:C5:26:21:14:7F:5B:AC:9E:04:F1:A2:F7:0E:FF:64:62`

The direct key is permanent for every Strong lock phone: the provisioning QR names its certificate, and Android only accepts updates signed with it. Losing it means re-enrolling every managed phone. Keep two offline backups. The upload key can be reset through Play Console support if lost.
