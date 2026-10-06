# SafeNest Test 0.4.6

Local QA app: `com.safenest.app.lab`, launcher label **SafeNest Test**. It installs beside production SafeNest. No login, payment or server entitlement is needed in `labDebug`. The domain catalog and search stay hidden; custom additions and imports remain available.

## Permission-first setup

1. Install the APK and open **SafeNest Test**. If Android reports a signing conflict, copy your feedback notes before removing the previous Test app and reinstalling. CI debug certificates are not a stable release identity.
2. Tap **Start test**. Setup opens first. Under **App guard and Accessibility**, tap **Review and enable test guard**, read the disclosure and agree.
3. In Android Accessibility, enable **SafeNest Test app guard** yourself. Some sideloaded installations require **Allow restricted settings** in App info first. Return to SafeNest Test. Setup must show Accessibility enabled and the service connected before activation.
4. Tap **Start test**, review the activation disclosure and approve Android's VPN request. Keep **Block connections without VPN** off: this DNS-only service does not relay all traffic. Use one SafeNest VPN at a time.

## Guard tests

During an active 60-minute session, recognized **SafeNest Test app guard** Accessibility detail, **SafeNest** and **SafeNest Test** VPN detail/connection pages and removal/force-stop controls return Home. Recognized settings/connection pages for known VPN profiles such as **1.1.1.1** are also guarded. Main Settings, the Accessibility services list, the VPN list and unrelated app settings remain usable. A VPN name alone is insufficient to trigger the guard: the matcher also requires specific VPN detail labels or connection controls. The own Accessibility page uses the exact **Use SafeNest Test app guard** label. Detection varies by phone/language and cannot undo an OS action already completed.

The guard sends Back from the protected detail/dialog before a delayed Home action. This removes the protected page from the Settings back stack so reopening Settings restores an unguarded parent. Stop/expiry/revocation cancels delayed actions, short unknown-window transitions get bounded retries, and delayed Home never follows the user into an unrelated app. Observations are not recorded or uploaded; browser page text is not inspected.

## Focused regression on a device

1. Before starting, enable Accessibility yourself; the permission page must remain usable during setup.
2. Start the test. In **Test tools**, verify version **0.4.6-test**, Accessibility enabled, Guard connected, and Control guard active are all correct.
3. Open ordinary Settings, Network & internet, Accessibility list and the VPN list shown in the screenshot: these must stay open.
4. Enter **SafeNest Test app guard** Accessibility detail: the guard should leave it and return Home while the test is active.
5. Tap either the SafeNest Test VPN row or its gear, then do the same for 1.1.1.1: recognized detail/connection screens should leave and return Home. Check that Settings/VPN list can be opened again after each redirect.
6. Test App info/removal controls for both SafeNest and SafeNest Test. Check unrelated app info and another Accessibility service remain usable.
7. Press **Stop test** and reopen all these pages: they should be available normally.
8. If a result differs, copy **Test tools → Copy feedback for ChatGPT**, with phone model/Android version. Feedback includes Accessibility, connection/guard state, a coarse last-action reason and the last navigation result; it does not include Settings labels.

This Accessibility test guard is not Device Owner enrollment. CI now runs a disposable Android 16 emulator in addition to compilation and pure Java regressions. Its instrumented test uses real consent buttons, Accessibility, app-info pages and the Always-on VPN setting; the test keeps Accessibility services enabled while inspecting the UI. Evidence is saved in the safenest-guard-device-QA artifact. Confirm the device job result before calling a build device-tested. A phone with a different Settings interface still requires a check on that phone.

**Stop test** remains available inside SafeNest Test on every page. It clears the session, guard selection and control consent, then stops DNS. Check that the same Settings screens become usable afterward. Each restart needs fresh guard consent; Accessibility may remain granted. Expiry or reboot ends the local session. Monotonic time enforces the one-hour limit. Local access requires both DEBUG and the lab flag; lab release variants are disabled. Play/direct paid-access behavior is unchanged.

## Website and feature tests

In **Test tools**, add the harmless example.org rule and open it; confirm example.com still loads and check the last blocked DNS request. Cached connections or browser Secure DNS can bypass the DNS test. Add custom sites under **Protect**. Save bugs/features in Test tools, copy feedback, and paste it into the ChatGPT conversation. Stop the test before removing a test rule.

Build: `./gradlew :app:testLabDebugUnitTest :app:lintLabDebug :app:assembleLabDebug`.
CI also checks Play/direct tests and lint, assembles debug APKs and builds the unsigned Play release bundle. The `safenest-test-apk` artifact contains only the lab APK. The matcher regressions cover the supplied Accessibility/VPN list screenshots, protected details and unrelated-screen negatives. Navigation regressions cover Back-before-Home, timing transitions, cancellation and unrelated app safety. Automated builds are not physical-device verification, Play acceptance, payment validation or signing continuity. No public website/store release is implied.

## Screenshot fixes in 0.4.6

The supplied uninstall screenshot names the regular SafeNest app; the VPN screenshot shows 0.4.4-test. The finite test guard now covers both installed SafeNest identities after fresh consent. It distinguishes page headers from preference-row titles, handles toolbar titles without resource IDs, and checks VPN management-page controls before redirecting. Short bounded rescans handle window events that arrive before content is ready. Test tools report the control guard active only when Accessibility is enabled and the service is connected.

Events with a window ID must match the active root's ID, so an older page in the same Settings package cannot trigger the new page's navigation. Blank section headings are ignored. Repeated content events share one Back/Home transaction, with bounded focus-transition retries. Home completion is recorded after sending Home or observing that Back already reached the launcher. The pending transaction releases when Home appears, allowing a newly opened protected page to be guarded immediately. Stop, expiry, unbind and movement to an unrelated app still cancel navigation.

The lab configuration also receives window-focus changes and retrieves interactive-window metadata. It checks at most 12 windows for the focused window and retrieves only that window's root. This avoids stale touch-window focus after Back. If a different app or system dialog owns focus, the guard does not inspect Settings behind it or force a delayed Home action. Labels remain bounded to the supported system/installer surfaces; no additional app text is read or retained.
