# Before public release

- Complete full Gradle tests, debug APK and signed release builds. Core compilation is not a complete Android build. See verification-0.3.1.md, verification-0.3.md and BUILDING.md.
- Run device-test-plan.md on actual target devices. Prove ordinary sites load while listed test domains fail on Wi-Fi and mobile data; record supported OS/browser versions.
- Resolve or explicitly scope client TCP DNS, other-browser DoH, proxies, direct-IP access, other profiles and background VPN limitations. Do not claim every gambling/adult site or VPN is blocked.
- Test permission denial/revocation, reboot, process death, battery restrictions, network changes and emergency recovery. Test managed-policy rollback after partial failure and upgrades.
- Build a signed AAB using [Android's signing guide](https://developer.android.com/studio/publish/app-signing). Back up keys securely and exclude them from source archives.
- Check current [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878) and [account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465). The source targets API 36; configuration alone does not prove store eligibility.
- Complete [VpnService declarations](https://support.google.com/googleplay/android-developer/answer/12564964) and [Accessibility API declarations/disclosures](https://support.google.com/googleplay/android-developer/answer/10964491). SafeNest is not an accessibility tool. Keep explicit consent and Android permission controls usable. Verify the final distribution model against current policy; approval is not guaranteed.
- Prepare a hosted privacy policy, support contact, Data safety answers and accurate screenshots/capability descriptions. Document upstream DNS fallback and local Accessibility processing.
- Prepare signed provisioning and enrollment/recovery support for managed deployments. Device-owner authority is not an ordinary permission prompt. No production enrollment QR is included.
- Connect and operate a licensed maintained catalog using the included signature/version checks. Test key rotation, replay rejection, offline/stale rules, corrupted storage, publisher mistakes and correction releases. Starter domains are not a complete category database.
- Review Bangla, accessibility, imports/exports and local data deletion. Keep reflections out of analytics.
- Validate accounts, billing and backend services separately before advertising them. Account authentication, private profiles, account deletion and read-only entitlement verification are implemented. Real checkout and receipt-driven entitlement issuance remain unavailable; completing a profile does not activate protection.

- Before enabling managed mode for any customer, test policy readbacks, real system Disconnect behavior, administrator recovery code verification, upgrade from legacy no-code installations, and interrupted partial recovery. Validate the enrollment process independently of billing. The debug build and ADB recovery are not a production tamper-resistance boundary.
