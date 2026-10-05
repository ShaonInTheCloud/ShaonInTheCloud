# SafeNest 0.3.1 verification

Date: 2026-09-28. This update addresses managed-policy verification and administrator-controlled release.

## Completed

- Compiled all six production Java helpers and their Java tests with JDK17.
- Compiled all 12 non-UI production Kotlin files and Kotlin tests with Kotlin2.0.21 against actual AOSP Android15/API35 classes. Android APIs were not replaced with hand-written stubs.
- **36 JUnit tests passed**, including 11 managed-policy status cases and five recovery credential cases. The latter cover an independently generated PBKDF2 vector, wrong codes, random salts, invalid/corrupt records, Unicode/bounds and bounded cooldown calculations.
- Parsed all **18 production Kotlin files**: zero syntax errors. This is not Compose type-checking.
- Parsed all four XML files and checked the six manifest component classes/resource references.
- The integrated source/test hashes did not change during validation.
- Separately compiled the exact managed-dialog transaction helpers with their real non-UI dependencies against the Android API classpath.

The only non-UI compiler warning concerns deprecated `ConnectivityManager.allNetworks` in existing network selection code.

## What the update changes

- Reads Device Owner, administrator activity, managed Always-on package, VPN-settings restriction, uninstall restriction and supported Lockdown state from Android.
- Requires mandatory readbacks before saving managed-setup success. Saved policy journal state alone no longer appears as proof of enforcement in the settings screen.
- Adds recovery-code creation, confirmation and authentication before repair/release. Only a salted PBKDF2-HMAC-SHA256 verifier is persisted, with600,000 iterations and bounded retry delay. Crypto/policy work runs on IO.
- Serializes managed-dialog operations across activity recreation and completes credential/journal cleanup if the UI is cancelled. Partial or uncertain recovery retains the code.
- Preserves a labeled legacy release path for installations that never configured a code.
- Adds device-owner setup instructions and execution-time checks on normal stop requests.

## Not verified

There is no connected emulator/phone or usable Android SDK/Compose dependency set in this workspace. No full Android36 Gradle/Compose/resource build, APK generation, UI rendering, Android policy enforcement, recovery-file persistence fault test, or on-device test was completed. The source archive is not a tested release APK.

In particular, system Disconnect, reboot behavior, OEM Settings, actual allowed/blocked browsing, wrong-code UI flows, interrupted policy rollback and documented developer ADB recovery still require the test plan on a disposable test device. Policy readbacks establish reported configuration, not universal blocking or network health.

No billing/subscription backend or automatic production device-owner provisioning was added. This build is not impossible to remove through reset, root, privileged debugging or administrative recovery. Debug ADB recovery is deliberately outside the enforcement boundary.

See **device-owner-setup.md** for setup and **device-test-plan.md** for pending device checks. Earlier network/catalog evidence and limits remain in **verification-0.3.md**.
