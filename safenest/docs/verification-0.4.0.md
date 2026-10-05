# SafeNest 0.4.0 verification

## Build evidence

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` completed successfully with Gradle 8.13, a full OpenJDK 17, Android SDK 36 and build tools 35.0.0.
- The installable debug APK reports package `com.safenest.app`, version name `0.4.0`, version code `17`, minimum API 26 and target API 36.
- APK Signature Scheme v2 verification passed. This uses a development signing certificate; no release signing key is supplied.
- All 41 Android JVM unit tests passed. These include DNS/transport/catalog regressions, app/address parsing, managed Chrome policies, recovery credentials and 19 paid-window/system-screen checks.
- Standalone DNS codec checks covered 20,075 assertions and 20,000 malformed inputs. DNS alias checks covered 10,082 assertions and 10,000 malformed responses. Upstream tests used real loopback UDP/TCP sockets. Catalog checks used real P-256 signatures.
- Android lint: 0 errors, 0 fatal issues, 40 warnings. Nonfatal style/API, backup, manifest and resource warnings remain; this is not a clean production-release audit.
- APK SHA-256: `09b18a7149c70bbdb707d9328d85f063d197eb916d05c31075b86bba6902d77c`.

## Backend evidence

- Deployed authenticated `protection-access` version 2 to the existing SafeNest Supabase project; JWT verification is enabled.
- A live unauthenticated POST returned HTTP 401.
- Eight endpoint tests passed: missing/invalid auth, client payment spoofing, anonymous accounts, trusted finite periods, backend failures, exact-period support checks, filter injection and malformed backend responses.
- Database metadata confirms row-level security is enabled. Anonymous clients cannot SELECT; authenticated clients can SELECT but cannot INSERT, UPDATE or DELETE paid entitlements. The existing policy restricts reads to the authenticated owner.
- There are no active paid entitlements in the live project. Checkout and verified payment-to-entitlement issuance are not connected. No paid subscription or fake transaction was created for these tests.

## What was not verified

No Android emulator or Honor phone was connected here. There was no physical-device DNS, Chrome/Firefox address-bar, VPN app, Settings/uninstall, Device Owner, reboot or expiry-job flow test. JVM UI-classifier tests verify labels and decisions, not the operating system performing a Home action. Build success is not proof of universal uninstall/disconnect resistance or VPN-bypass protection.

This client remains a DNS-only split tunnel with a finite, consented Accessibility UI barrier. Leave Android **Block connections without VPN** OFF. Another VPN or proxy can still bypass some layers, and device recovery remains possible. Read `subscription-enforcement.md` before distributing it.

## Reproduce locally

From `SafeNest/android`, with a full JDK 17 and SDK 36:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

From `SafeNest`, with Node 24 for TypeScript stripping:

```sh
./run-core-tests.sh
node --experimental-strip-types --test supabase/functions/protection-access/test/access.test.mjs
```

For a phone test, first establish a legitimate server-confirmed paid account and read both consent screens. Test an ordinary site, a harmless blocked domain such as `example.org`, a harmless chosen app, a detected VPN app, the SafeNest uninstall/disconnect screens and unrelated Settings. Observe actual Home redirection and preserve logcat errors without recording credentials or browsing history. Check automatic expiry and supported backend revocation on a dedicated test account/device before customer rollout.

When upgrading an existing installation, Android requires the same signing key. Rebuilding this source in the original Android Studio environment normally uses its existing debug key. A debug APK built in another environment cannot replace an app signed with your original key.
