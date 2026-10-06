# SafeNest Test 0.4.4

Local QA app: `com.safenest.app.lab`, launcher label **SafeNest Test**. It installs beside production SafeNest. No login, payment or server entitlement is needed in `labDebug`. The domain catalog and search stay hidden; custom additions and imports remain available.

## Permission-first setup

1. Install the APK and open **SafeNest Test**. If Android reports a signing conflict, copy your feedback notes before removing the previous Test app and reinstalling. CI debug certificates are not a stable release identity.
2. Tap **Start test**. Setup opens first. Under **App guard and Accessibility**, tap **Review and enable test guard**, read the disclosure and agree.
3. In Android Accessibility, enable **SafeNest Test app guard** yourself. Some sideloaded installations require **Allow restricted settings** in App info first. Return to SafeNest Test. Setup must show Accessibility enabled and the service connected before activation.
4. Tap **Start test**, review the activation disclosure and approve Android's VPN request. Keep **Block connections without VPN** off: this DNS-only service does not relay all traffic. Use one SafeNest VPN at a time.

## Guard tests

During the active 60-minute session, recognized SafeNest Test Settings/installer screens with enabled **Disconnect**, **Forget VPN**, **Uninstall** or **Force stop** controls return Home. The guard can redirect as soon as the screen opens, before a tap. Recognition checks foreground package, exact test-app identity, visible bounded control labels and clickable rows (including labels inside those rows). Observations are not recorded or uploaded. Browser page text is not inspected.

Check the VPN detail screen and SafeNest Test App info from the supplied screenshots. Record whether each returns Home and the phone model/Android version. Also check unrelated app info, another VPN, network settings and Accessibility revocation; these must remain usable. This Accessibility guard is not Device Owner enrollment and does not cancel an OS action that has already completed. OEM or localized screens that do not match need further device evidence.

**Stop test** remains available inside SafeNest Test on every page. It clears the session, guard selection and control consent, then stops DNS. Check that the same Settings screens become usable afterward. Each restart needs fresh guard consent; Accessibility may remain granted. Expiry or reboot ends the local session. Monotonic time enforces the one-hour limit. Local access requires both DEBUG and the lab flag; lab release variants are disabled. Play/direct paid-access behavior is unchanged.

## Website and feature tests

In **Test tools**, add the harmless example.org rule and open it; confirm example.com still loads and check the last blocked DNS request. Cached connections or browser Secure DNS can bypass the DNS test. Add custom sites under **Protect**. Save bugs/features in Test tools, copy feedback, and paste it into the ChatGPT conversation. Stop the test before removing a test rule.

Build: `./gradlew :app:testLabDebugUnitTest :app:lintLabDebug :app:assembleLabDebug`.
CI also checks Play/direct tests and lint, assembles debug APKs and builds the unsigned Play release bundle. The `safenest-test-apk` artifact contains only the lab APK. The static matcher regressions cover all four controls and unrelated-screen negatives. Automated builds are not physical-device verification, Play acceptance, payment validation or signing continuity. No public website/store release is implied.
