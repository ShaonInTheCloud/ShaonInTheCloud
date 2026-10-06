# SafeNest Test 0.4.5

Local QA app: `com.safenest.app.lab`, launcher label **SafeNest Test**. It installs beside production SafeNest. No login, payment or server entitlement is needed in `labDebug`. The domain catalog and search stay hidden; custom additions and imports remain available.

## Permission-first setup

1. Install the APK and open **SafeNest Test**. If Android reports a signing conflict, copy your feedback notes before removing the previous Test app and reinstalling. CI debug certificates are not a stable release identity.
2. Tap **Start test**. Setup opens first. Under **App guard and Accessibility**, tap **Review and enable test guard**, read the disclosure and agree.
3. In Android Accessibility, enable **SafeNest Test app guard** yourself. Some sideloaded installations require **Allow restricted settings** in App info first. Return to SafeNest Test. Setup must show Accessibility enabled and the service connected before activation.
4. Tap **Start test**, review the activation disclosure and approve Android's VPN request. Keep **Block connections without VPN** off: this DNS-only service does not relay all traffic. Use one SafeNest VPN at a time.

## Guard tests

During an active 60-minute session, recognized **SafeNest Test app guard** Accessibility detail, **SafeNest Test** VPN detail/connection pages and SafeNest Test removal/force-stop controls return Home. Recognized settings/connection pages for known VPN profiles such as **1.1.1.1** are also guarded. Main Settings, the Accessibility services list, the VPN list and unrelated app settings remain usable. A VPN name alone is insufficient to trigger the guard: the matcher also requires specific VPN detail labels or connection controls. The own Accessibility page uses the exact **Use SafeNest Test app guard** label. Detection varies by phone/language and cannot undo an OS action already completed.

The guard sends Back from the protected detail/dialog before a delayed Home action. This removes the protected page from the Settings back stack so reopening Settings restores an unguarded parent. Stop/expiry/revocation cancels delayed actions, short unknown-window transitions get bounded retries, and delayed Home never follows the user into an unrelated app. Observations are not recorded or uploaded; browser page text is not inspected.

## Focused regression on a device

1. Before starting, enable Accessibility yourself; the permission page must remain usable during setup.
2. Start the test. In **Test tools**, verify version **0.4.5-test**, Accessibility enabled, Guard connected, and Control guard active are all correct.
3. Open ordinary Settings, Network & internet, Accessibility list and the VPN list shown in the screenshot: these must stay open.
4. Enter **SafeNest Test app guard** Accessibility detail: the guard should leave it and return Home while the test is active.
5. Tap either the SafeNest Test VPN row or its gear, then do the same for 1.1.1.1: recognized detail/connection screens should leave and return Home. Check that Settings/VPN list can be opened again after each redirect.
6. Test SafeNest Test App info/removal controls. Check unrelated app info and another Accessibility service remain usable.
7. Press **Stop test** and reopen all these pages: they should be available normally.
8. If a result differs, copy **Test tools → Copy feedback for ChatGPT**, with phone model/Android version. Feedback now includes Accessibility, connection/guard state and a coarse last-action reason; it does not include Settings labels.

This Accessibility test guard is not Device Owner enrollment. Physical/emulator execution remains a separate validation step from compilation and pure Java regressions.

**Stop test** remains available inside SafeNest Test on every page. It clears the session, guard selection and control consent, then stops DNS. Check that the same Settings screens become usable afterward. Each restart needs fresh guard consent; Accessibility may remain granted. Expiry or reboot ends the local session. Monotonic time enforces the one-hour limit. Local access requires both DEBUG and the lab flag; lab release variants are disabled. Play/direct paid-access behavior is unchanged.

## Website and feature tests

In **Test tools**, add the harmless example.org rule and open it; confirm example.com still loads and check the last blocked DNS request. Cached connections or browser Secure DNS can bypass the DNS test. Add custom sites under **Protect**. Save bugs/features in Test tools, copy feedback, and paste it into the ChatGPT conversation. Stop the test before removing a test rule.

Build: `./gradlew :app:testLabDebugUnitTest :app:lintLabDebug :app:assembleLabDebug`.
CI also checks Play/direct tests and lint, assembles debug APKs and builds the unsigned Play release bundle. The `safenest-test-apk` artifact contains only the lab APK. The matcher regressions cover the supplied Accessibility/VPN list screenshots, protected details and unrelated-screen negatives. Navigation regressions cover Back-before-Home, timing transitions, cancellation and unrelated app safety. Automated builds are not physical-device verification, Play acceptance, payment validation or signing continuity. No public website/store release is implied.
