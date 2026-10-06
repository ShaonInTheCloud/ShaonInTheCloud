# Launch continuation — 6 October 2026

Release status: development/testing. Paid sales and a production Play upload remain blocked.

Recovered and verified GitHub main `7ef13f8448032f332c70e79e2684a08e7d34fa07`, exact tree `fdd300d48be0108251360311c8feddbb3f566957`. Its 0.4.6-test CI compiled/linted/tested all three distributions and passed thirteen consented Settings-guard emulator checks. Ordinary Settings remained accessible. The Honor phone still needs a retest.

The 0.4.7 continuation replaces the default plain DNS route with certificate-verified Cloudflare HTTPS, preserves an active strict user-selected Private DNS provider on Android 10+, and refuses a plain fallback. It adds English/Bangla resolver disclosures, transport diagnostics, fifty transport regressions and three real connected-device internet checks. See `encrypted-dns-0.4.7.md`. A fresh full Android run must verify this exact source before sharing its APK as tested.

The separate manual signed Play bundle workflow is now prepared. It requires owner-controlled environment secrets and the expected public upload certificate fingerprint, checks the merged release manifest and every signed AAB payload, and writes public hash/evidence files. Disposable-key tests verify correct signing, wrong credentials/fingerprints, unsigned and tampered payloads and invalid release variants. See `release-signing.md`. No upload key was generated for the owner or committed; no signed production release or Play Store upload has been performed.

Local checks: 50 new DNS HTTPS checks plus all existing core regressions passed; 5 signing regressions passed; 22 account/security and 9 access/CORS tests passed; website build and YAML parsing passed. Local checks do not compile Android or replace physical-device evidence.

The GitHub source upload was rejected by automatic approval review. Read-only verification confirmed the destination is the existing public repository and the owner has admin/push permission, but review still requires explicit approval for this new 24-file source/workflow update. GitHub main remains at the verified 0.4.6 source. No alternate branch/upload route was used. A local Android assembly attempt also failed before compilation because Gradle downloads are network-blocked and this workspace has no Android SDK. No 0.4.7 APK or fresh connected-device result exists yet.

Outstanding launch gates remain: merchant sandbox/final prices and trusted entitlement issuance, real authentication/recovery/deletion completion, live account response headers, support/legal identity and retention, owner upload signing and Play Console/reviewer access, physical-device/network/battery testing, catalogue operations, final permission/transport policy review and monitoring/rollback. The public website still serves the existing development release; no public download or payment change is included in this continuation. Earlier applied database migration history is preserved.
