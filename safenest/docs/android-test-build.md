# SafeNest Test 0.4.3

This is a local QA app for testing and planning the next features. Package: `com.safenest.app.lab`; launcher label: SafeNest Test. It installs beside the existing SafeNest app. The built-in domain list and search are hidden in every flavor; filtering, custom website additions and JSON imports remain available.

No login, payment or server-issued entitlement is needed in `labDebug`. Start test asks for local VPN consent and begins a 60-minute session. Stop test clears that session, disables the optional app guard and stops the local DNS service. Restart as often as needed. Monotonic time controls expiry; a reboot ends the local session. Paid entitlement storage and production account state are separate. Local access requires both the lab flag and DEBUG; lab release variants are disabled. Play/direct still require verified paid access.

## Phone test

1. Install the APK and open **SafeNest Test**. Use one SafeNest VPN at a time.
2. Tap **Start test**, review the disclosure and grant Android's VPN permission. Accessibility is optional for DNS-only testing. Keep **Block connections without VPN** off.
3. Open **Test tools**, add the harmless example.org rule, then open its blocked-site test. Confirm example.com still loads. Check the last blocked request and DNS status. Cached connections or browser Secure DNS can bypass the test; close tabs, check browser DNS settings and retry before drawing conclusions.
4. Add custom sites under **Protect**. The built-in list stays hidden.
5. For app blocking, use **Permissions and app blocking**, review its disclosure, select apps and grant Accessibility yourself. Some phones require Allow restricted settings for a sideloaded APK. Browser/system/recovery apps remain excluded; Android controls are available.
6. Tap **Stop test**. Check that normal browsing is restored and the test guard no longer returns Home from selected apps. Remove the harmless rule after stopping if desired.
7. Try background/foreground transitions, network changes and a reboot. A reboot ends the local session; start another test manually.
8. Write bugs/features in **Test tools**, save or copy the feedback and paste it into the ChatGPT conversation for the next iteration.

This test app does not validate payment processing, production login, phone-wide VPN circumvention resistance, signing continuity or Play acceptance. Those remain release gates. The APK is a debug QA artifact; no new public website download or store release is implied.

Build: `./gradlew :app:testLabDebugUnitTest :app:lintLabDebug :app:assembleLabDebug`.

CI also checks Play/direct unit tests and lint, assembles their debug APKs and creates the unsigned Play release bundle. The dedicated `safenest-test-apk` artifact contains only the lab APK.
