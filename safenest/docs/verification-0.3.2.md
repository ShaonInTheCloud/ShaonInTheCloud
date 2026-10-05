# SafeNest 0.3.2 verification — 2026-09-29

Chrome and Firefox are exempt from Accessibility Home actions, including old saved app selections. Website address scanning no longer triggers Home. Selected nonessential blocked apps retain Home enforcement. Website filtering remains DNS-based, with managed Chrome URL policies where enrolled; this does not eliminate encrypted-DNS or proxy bypasses on unmanaged devices.

Validation completed:
- 38 JUnit tests passed, including browser exemption and blocked-app routing regressions.
- Production Java and non-UI Kotlin compiled against Android 15/API 35 with JDK 17.
- Resource XML parsed; service source checked for absence of address-bar/domain-to-Home logic.

Not validated: full Compose/Gradle APK build, installation, emulator or physical phone behavior. This is source code, not a tested release APK.

Subscription enforcement is not implemented. Existing Device Owner/recovery-code controls are not tied to paid entitlement. Backend inspection found only the profiles table; payment verification and term lifecycle require integration.

The supplied list is preserved in data/research-candidates-2026-09-29.json as 54 inactive REVIEW_REQUIRED candidates (44 gambling, 10 adult), not verified active rules. Two example placeholders are excluded. The broader intelligence pipeline remains pending.
