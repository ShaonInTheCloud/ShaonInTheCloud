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
