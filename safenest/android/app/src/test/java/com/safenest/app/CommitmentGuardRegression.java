package com.safenest.app;

import java.util.List;

/** Positive flows plus unrelated Settings/browser/installer negatives. No Android SDK required. */
public final class CommitmentGuardRegression {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        long t = 1_800_000_000_000L;
        check(CommitmentRules.validWindow(t - 1000, t + 1000, t), "valid paid window");
        check(!CommitmentRules.validWindow(t + 1, t + 1000, t), "future term must not activate");
        check(!CommitmentRules.validWindow(t - 1000, t, t), "expiry boundary");
        check(!CommitmentRules.active(false, t + 1000, t), "uncommitted verification must not enforce");
        check(!CommitmentRules.active(true, t, t), "no enforcement after expiry");
        // The wall clock is not an input, so no clock change (forward or back, before or after reboot) can move these.
        check(CommitmentRules.effectiveNow(20_000, t, 10_000, t) == t + 10_000, "same boot: server time plus uptime");
        check(CommitmentRules.effectiveNow(20_000, t, 10_000, t + 50_000) == t + 50_000, "same boot never goes below high-water");
        check(CommitmentRules.effectiveNow(100, t, 10_000, t + 5000) == t + 5100, "reboot adds only uptime since boot to high-water");
        check(CommitmentRules.effectiveNow(15_000, t, 10_000, t) == t + 5000, "missed reboot under-counts, so it can only delay release");
        // A 7-day term across two reboots, mirroring checkpoint(): high-water each check, re-anchor after a reboot.
        long day = 24L * 60 * 60 * 1000, ends = t + 7 * day;
        long high = CommitmentRules.effectiveNow(60_000 + 3 * day, t, 60_000, t);       // boot 1: verified at 1 min, last check 3 days on
        long anchor = CommitmentRules.effectiveNow(1_000, t, 60_000, high);             // boot 2 at 1 s: reboot detected
        high = CommitmentRules.effectiveNow(3 * day, anchor, 1_000, high);
        check(high == t + 6 * day, "six days of uptime across one reboot");
        anchor = CommitmentRules.effectiveNow(500, anchor, 1_000, high);                // boot 3 at 0.5 s
        check(CommitmentRules.active(true, ends, CommitmentRules.effectiveNow(day - 1, anchor, 500, high)), "locked 1 ms before 7 days of uptime");
        check(!CommitmentRules.active(true, ends, CommitmentRules.effectiveNow(day, anchor, 500, high)), "released after exactly 7 days of uptime");
        check(SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("SafeNest", "Disconnect this VPN?"), List.of("Cancel", "Disconnect"), false), "SafeNest disconnect dialog");
        check(SystemScreenGuard.blocksSafeNestControl("com.google.android.packageinstaller", List.of("SafeNest"), List.of("Uninstall"), false), "SafeNest uninstall");
        check(SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("SafeNest", "Use SafeNest"), List.of(), true), "own accessibility toggle");
        check(!SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("Accessibility", "SafeNest app guard"), List.of(), true), "unrelated toggle in accessibility list");
        check(!SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("Another VPN"), List.of("Disconnect"), false), "other VPN settings unaffected");
        check(!SystemScreenGuard.blocksSafeNestControl("com.android.chrome", List.of("SafeNest"), List.of("Uninstall"), false), "webpage is not Settings");
        check(!SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("SafeNest", "Wi-Fi"), List.of("Save"), false), "unrelated settings");
        check(SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("NordVPN: fast VPN"), List.of("Install")), "known VPN install");
        check(!SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("Facebook"), List.of("Install")), "normal Play install");
        check(!SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("NordVPN"), List.of("Uninstall")), "removing competing VPN remains allowed");
        check(!SystemScreenGuard.blocksVpnInstall("com.android.chrome", List.of("NordVPN"), List.of("Install")), "web page text not inspected");
        // Pages stay open; the tap on a destructive control and its confirmation dialog bounce.
        for (String action : List.of("Forget VPN", "Uninstall", "Force stop", "Clear storage", "Clear cache")) {
            check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "0.4.5-test", action), List.of("SafeNest Test"), List.of(action)), "own page with a " + action + " button stays open");
            check(SystemScreenGuard.isDestructiveControl(action), "tapping " + action + " is destructive");
        }
        check(SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("1.1.1.1 + WARP: Safer Internet"), List.of("Install")), "WARP install by name");
        check(SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("Unknown Fast VPN Proxy"), List.of("Install")), "any app calling itself a VPN");
        check(SystemScreenGuard.blocksVpnInstall("com.sec.android.app.samsungapps", List.of("Proton VPN"), List.of("Install")), "Galaxy Store install");
        check(!SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("WhatsApp Messenger"), List.of("Install")), "non-VPN app installs normally");
        check(!SystemScreenGuard.blocksVpnInstall("com.android.vending", List.of("Vpnalytics Dashboard"), List.of("Install")), "vpn inside a longer word is not a VPN");
        check(SystemScreenGuard.blocksPrivateDns("com.android.settings", List.of("Private DNS", "Off", "Automatic", "Private DNS provider hostname"), List.of("Cancel", "Save")), "Private DNS dialog");
        check(!SystemScreenGuard.blocksPrivateDns("com.android.settings", List.of("Network & internet", "Private DNS", "Wi-Fi"), List.of("Wi-Fi")), "Private DNS row in the network list stays usable");
        check(SystemScreenGuard.blocksOtherVpnControl("com.android.settings", List.of("Another VPN", "Always-on VPN"), List.of("Cancel", "Connect")), "other VPN connect dialog");
        check(SystemScreenGuard.blocksOtherVpnControl("com.android.settings", List.of("Connection request", "Another VPN wants to set up a VPN connection"), List.of("Cancel", "OK")), "VPN consent dialog for another app");
        check(!SystemScreenGuard.blocksOtherVpnControl("com.android.settings", List.of("SafeNest local DNS filter", "Always-on VPN"), List.of("Connect")), "SafeNest own VPN dialog is not another VPN");
        check(!SystemScreenGuard.blocksOtherVpnControl("com.android.settings", List.of("Wi-Fi", "Bluetooth"), List.of("Connect")), "ordinary connect buttons unaffected");
        for (String harmless : List.of("Notifications", "Permissions", "Always-on VPN", "Open", "Storage", "Battery", "Allow"))
            check(!SystemScreenGuard.isDestructiveControl(harmless), harmless + " tap is harmless");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test"), List.of("SafeNest Test"), List.of("Disconnect", "Cancel")), "own Disconnect dialog bounces");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("Force stop?", "If you force stop an app, it may misbehave."), List.of("Force stop?"), List.of("Cancel", "OK"), true).equals("test_force_stop_confirm"), "Force stop? dialog right after own App info bounces");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("Force stop?", "If you force stop an app, it may misbehave."), List.of("Force stop?"), List.of("Cancel", "OK"), false).isEmpty(), "Force stop? dialog for another app stays usable");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("Delete app data?", "This app's data, including files and settings, will be deleted permanently from this device"), List.of(), List.of("Cancel", "Delete"), true).equals("test_clear_storage_confirm"), "Delete app data? dialog right after own Storage page bounces");
        check(SystemScreenGuard.testControlReason("com.google.android.packageinstaller", List.of("SafeNest Test", "Do you want to uninstall this app?"), List.of(), List.of("Cancel", "OK")).equals("test_uninstall_confirm"), "uninstall confirmation with OK button");
        check(SystemScreenGuard.isForeignDetailPage("com.android.settings", List.of("Chrome", "Uninstall", "Force stop"), List.of("Chrome")), "another app's App info resets own-page memory");
        check(!SystemScreenGuard.isForeignDetailPage("com.android.settings", List.of("SafeNest Test", "Uninstall", "Force stop"), List.of("App info")), "own App info is not foreign");
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "test uninstall confirmation without title IDs");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("com.safenest.app.lab"), List.of(), List.of("Disconnect")), "test package identity");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Another VPN"), List.of("Another VPN"), List.of("Forget VPN")), "another VPN remains accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest", "Archive", "Uninstall", "Force stop", "Notifications"), List.of("App info"), List.of("Uninstall", "Force stop")), "regular SafeNest App info stays open (owner request 17 Oct 2026)");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Uninstall"), List.of("App info", "\u00a0", "  "), List.of("Uninstall")), "own App info with blank headings stays open");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Another VPN"), List.of("Another VPN"), List.of("Disconnect")), "background test label cannot target another VPN detail");
        check(!SystemScreenGuard.blocksTestControl("com.android.chrome", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "test phrase in browser is not a control");
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest Owner Test"), List.of(), List.of("Uninstall")), "owner test uninstall confirmation");
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("com.safenest.app.ownerlab"), List.of(), List.of("OK")), "owner test package identity on uninstall confirmation");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Owner Test app guard", "Use SafeNest Owner Test app guard"), List.of(), List.of()).equals("test_accessibility"), "owner test accessibility switch");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest Owner Test").isEmpty(), "a window titled with the app name may be the VPN detail page; content decides");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Owner Testing Notes"), List.of("SafeNest Owner Testing Notes"), List.of("Uninstall")), "similar name is not owner test");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Accessibility", "SafeNest Test app guard", "TalkBack"), List.of("Accessibility"), List.of("SafeNest Test app guard")), "Accessibility list stays available");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test"), List.of(), List.of("Allow", "Always-on VPN")), "test permissions and network toggles remain accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.vending", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "lab inspection limited to Settings and installers");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test app guard", "Use SafeNest Test app guard", "App info"), List.of("SafeNest Test app guard"), List.of()), "exact own Accessibility detail from screenshot");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("TalkBack", "Use TalkBack"), List.of("TalkBack"), List.of()), "other Accessibility services unaffected");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("VPN", "1.1.1.1", "SafeNest Test"), List.of("VPN", "1.1.1.1", "SafeNest Test"), List.of("1.1.1.1", "SafeNest Test", "Settings")), "exact supplied VPN list stays accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Settings", "SafeNest Test", "Apps", "Network & internet"), List.of("Settings"), List.of("Apps")), "Settings home stays accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Always-on VPN", "Forget VPN"), List.of("SafeNest Test", "Always-on VPN"), List.of("Forget VPN")), "own VPN detail stays open so Always-on VPN can be switched on");
        check(SystemScreenGuard.isOwnVpnDetail("com.android.settings", List.of("SafeNest Test", "Always-on VPN", "Forget VPN")), "own VPN detail recognised for Always-on enforcement");
        check(!SystemScreenGuard.isOwnVpnDetail("com.android.settings", List.of("1.1.1.1", "Always-on VPN", "Forget VPN")), "another VPN detail is not ours");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Test", "Always-on VPN", "Forget this VPN?"), List.of(), List.of("Cancel", "Forget")).equals("test_forget_vpn_confirm"), "Forget confirmation on own VPN bounces");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Test"), List.of("SafeNest Test"), List.of("Disconnect", "Cancel")).equals("test_disconnect_confirm"), "own Disconnect dialog bounces");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "Always-on VPN", "Forget VPN"), List.of("1.1.1.1"), List.of()), "1.1.1.1 VPN detail guarded");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1"), List.of("1.1.1.1"), List.of("Connect", "Cancel")), "1.1.1.1 connect dialog guarded");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "Notifications"), List.of("1.1.1.1"), List.of("Allow notifications")), "another VPN ordinary app settings unaffected");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "SafeNest Test", "Forget VPN"), List.of("Unrelated VPN"), List.of("Forget VPN")), "background VPN mention cannot target unrelated profile");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Test app guard", "Use SafeNest Test app guard"), List.of(), List.of()).equals("test_accessibility"), "diagnostic reason distinguishes own switch");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Test", "Version", "0.4.4-test", "Always-on VPN", "Block connections without VPN", "Forget VPN"), List.of(), List.of("Always-on VPN", "Forget VPN")).isEmpty(), "supplied VPN detail with unlabeled toolbar stays open");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Always-on VPN", "Forget VPN"), List.of("Always-on VPN", "Forget VPN"), List.of()), "own VPN detail with preference titles stays open");
        // Android's VPN permission prompt: refused for every app but SafeNest.
        check(SystemScreenGuard.blocksForeignVpnConsent("com.android.vpndialogs", List.of("Connection request", "1.1.1.1 wants to set up a VPN connection that allows it to monitor network traffic. Only accept if you trust the source.", "Cancel", "OK")), "1.1.1.1 connection request refused");
        check(!SystemScreenGuard.blocksForeignVpnConsent("com.android.vpndialogs", List.of("Connection request", "SafeNest Test wants to set up a VPN connection that allows it to monitor network traffic.", "Cancel", "OK")), "SafeNest's own connection request allowed");
        check(!SystemScreenGuard.blocksForeignVpnConsent("com.android.settings", List.of("Connection request", "1.1.1.1 wants to set up a VPN connection")), "only the system VPN dialog package counts");
        check(SystemScreenGuard.isSystemSurface("com.android.vpndialogs"), "VPN dialog package is inspected");
        check(!SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("SafeNest", "Always-on VPN", "Block connections without VPN", "Forget VPN"), List.of("Always-on VPN", "Forget VPN"), false), "consumer: own VPN detail stays open");
        check(SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("SafeNest", "Always-on VPN", "Forget this VPN?"), List.of("Cancel", "Forget"), false), "consumer: Forget confirmation bounces");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("VPN", "SafeNest Test", "Always-on VPN", "1.1.1.1"), List.of("VPN"), List.of("SafeNest Test", "Settings")), "Always-on status in VPN list must not guard the list");
        // App info > Storage for the lab app, from the owner's 17 Oct 2026 screenshot: heading "Storage" is page chrome, not another app.
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Storage", "SafeNest Test", "0.5.0-test", "Clear storage", "Clear cache", "Space used", "App size", "23.92 MB", "User data", "Cache", "Total"), List.of("Storage"), List.of("Clear storage", "Clear cache")), "own Storage page (owner screenshot) stays open; the taps bounce");
        check(SystemScreenGuard.isDestructiveControl("Clear storage") && SystemScreenGuard.isDestructiveControl("Clear cache") && SystemScreenGuard.isDestructiveControl("স্টোরেজ সাফ করুন"), "Clear storage / Clear cache taps bounce");
        check(SystemScreenGuard.hasOwnIdentity(List.of("Storage", "SafeNest Owner Test", "Clear storage")), "own identity remembered from the Storage page");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Storage", "Chrome", "Clear storage", "Clear cache"), List.of("Storage"), List.of("Clear storage", "Clear cache")), "another app's Storage page stays usable");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Storage", "Internal shared storage", "Apps", "SafeNest Test", "23.92 MB"), List.of("Storage"), List.of("Apps", "Free up space")), "device Storage overview listing the app stays usable");
        check(!SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("Storage", "SafeNest", "Clear storage", "Clear cache"), List.of("Clear storage", "Clear cache"), false), "consumer build: own Storage page stays open");
        check(SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("Delete app data?", "will be deleted permanently"), List.of("Cancel", "Delete"), false, true), "consumer build: Delete app data? after own page bounces");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Unrelated VPN", "Always-on VPN", "Block connections without VPN", "Forget VPN"), List.of("Unrelated VPN"), List.of("Forget VPN")), "strong unrelated profile heading beats a background app label");
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest"), List.of("SafeNest"), List.of("Cancel", "Uninstall")), "regular SafeNest uninstall confirmation during consented lab test");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest", "Chrome", "Uninstall", "Force stop"), List.of("Chrome"), List.of("Uninstall", "Force stop")), "other app uninstall page stays available");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "Always-on VPN", "Forget VPN"), List.of(), List.of()), "known VPN management page without toolbar IDs");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest Test").isEmpty(), "own app-name title alone is ambiguous (App info or VPN detail); content scan decides");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest").isEmpty(), "regular own app-name title alone does not bounce");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest Test app guard").equals("test_accessibility"), "focused own Accessibility title");
        for (String title : List.of("Settings", "VPN", "Accessibility", "Chrome", "SafeNest Test and Chrome", "1.1.1.1"))
            check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", title).isEmpty(), "generic/unrelated focused title stays available: " + title);
        check(SystemScreenGuard.testFocusedWindowReason("com.android.chrome", "SafeNest Test").isEmpty(), "same title in another app is ignored");
        check(SystemScreenGuard.testFocusedWindowReason("com.safenest.app.lab", "SafeNest Test").isEmpty(), "own app and Stop test remain usable");
        check(SystemScreenGuard.testFocusedWindowReason("com.google.android.packageinstaller", "SafeNest Test").isEmpty(), "installer name alone is insufficient");
        storageControls();
        WindowPackageCache windows = new WindowPackageCache();
        windows.observe(24, "com.android.settings");
        check("com.android.settings".equals(windows.owner(24)) && windows.owner(25) == null, "ownership never transfers to a different focused window ID");
        windows.observe(-1, "com.android.settings"); windows.observe(24, null);
        check(windows.owner(-1) == null && "com.android.settings".equals(windows.owner(24)), "missing event metadata cannot erase valid ownership or bind an unknown window");
        windows.observe(25, "com.safenest.app.lab");
        check("com.android.settings".equals(windows.owner(24)), "hidden Settings window retains ownership when another task becomes visible");
        windows.observe(24, "com.android.chrome");
        check(SystemScreenGuard.testFocusedWindowReason(windows.owner(24), "SafeNest Test").isEmpty(), "reused window in another app cannot trigger own Settings guard");
        for (int id = 30; id < 43; id++) windows.observe(id, "com.android.settings");
        check(windows.owner(24) == null && windows.owner(30) == null && "com.android.settings".equals(windows.owner(42)), "ownership cache is bounded to twelve windows");
        windows.clear();
        check(windows.owner(42) == null, "unbind clears ephemeral ownership");
        System.out.println("Paid-window and UI-guard regression: " + checks + " checks passed.");
    }

    private static void storageControls() {
        String[][] locales = {
            {"Storage", "Clear storage", "Clear data", "Clear cache", "Clear all data", "Manage space", "Delete app data"},
            {"স্টোরেজ", "স্টোরেজ সাফ করুন", "ডেটা সাফ করুন", "ক্যাশে সাফ করুন", "স্টোরেজ মুছুন", "ডেটা মুছুন", "ক্যাশে মুছুন"},
            {"Tallennustila", "Tyhjennä tallennustila", "Tyhjennä tiedot", "Tyhjennä välimuisti", "Poista tiedot"}
        };
        List<String> settingsPackages = List.of("com.android.settings", "com.hihonor.settings", "com.huawei.systemmanager",
            "com.hihonor.systemmanager", "com.miui.securitycenter", "com.samsung.android.lool", "com.coloros.safecenter",
            "com.oplus.safecenter", "com.vivo.permissionmanager", "com.iqoo.secure", "com.oneplus.security", "com.transsion.phonemaster");
        for (String pkg : settingsPackages) {
            check(SystemScreenGuard.isSystemSurface(pkg), "OEM Settings surface retained: " + pkg);
            for (String[] locale : locales) {
                String heading = locale[0];
                for (int i = 1; i < locale.length; i++) {
                    String action = locale[i];
                    List<String> controls = List.of(action);
                    List<String> ownLabels = List.of(heading, "SafeNest", action);
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, ownLabels, controls, false), "consumer own Storage page stays open: " + pkg + " / " + action);
                    check(SystemScreenGuard.isDestructiveControl(action), "tapping the storage action is destructive: " + pkg + " / " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, ownLabels, List.of(heading), controls, false), "consumer selected Storage page stays open: " + action);
                    for (String identity : List.of("SafeNest", "SafeNest Test", "SafeNest Owner Test", "com.safenest.app", "com.safenest.app.lab", "com.safenest.app.ownerlab")) {
                        check(SystemScreenGuard.testControlReason(pkg, List.of(heading, identity, action), List.of(heading), controls)
                            .isEmpty(), "lab/ownerlab own Storage page stays open: " + identity + " / " + action);
                    }
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "Chrome", action), List.of(heading), controls, false), "consumer unrelated app storage: " + action);
                    check(!SystemScreenGuard.blocksTestControl(pkg, List.of(heading, "Chrome", action), List.of(heading), controls), "lab unrelated app storage: " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "Chrome", "SafeNest", action), List.of(heading, "Chrome"), controls, false), "foreign app heading beats background SafeNest label: " + action);
                    check(!SystemScreenGuard.blocksTestControl(pkg, List.of(heading, "Chrome", "SafeNest Test", action), List.of(heading, "Chrome"), controls), "lab foreign app heading beats background label: " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl("com.android.chrome", ownLabels, controls, false), "browser storage text ignored: " + action);
                    check(!SystemScreenGuard.blocksTestControl("com.android.chrome", ownLabels, List.of(heading), controls), "lab browser storage text ignored: " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, ownLabels, List.of(heading), List.of(), false), "non-actionable storage label stays usable: " + action);
                    check(!SystemScreenGuard.blocksTestControl(pkg, ownLabels, List.of(heading), List.of()), "lab non-actionable storage label stays usable: " + action);
                    check(SystemScreenGuard.isDestructiveControl("\u00a0" + action.toUpperCase(java.util.Locale.ROOT) + "  "), "case and surrounding whitespace normalized: " + action);
                }
                List<String> overview = List.of(heading, "Internal shared storage", "Apps", "SafeNest", "SafeNest Test", "23.92 MB");
                check(!SystemScreenGuard.blocksSafeNestControl(pkg, overview, List.of(heading), List.of("Apps", "Free up space"), false), "consumer device storage overview remains accessible");
                check(!SystemScreenGuard.blocksTestControl(pkg, overview, List.of(heading), List.of("Apps", "Free up space")), "lab device storage overview remains accessible");
            }
            for (String heading : List.of("Storage & cache", "Storage and cache", "Space used", "Manage storage", "স্টোরেজ ও ক্যাশে", "Tallennustila ja välimuisti")) {
                check(!SystemScreenGuard.isForeignDetailPage(pkg, List.of(heading, "SafeNest Test", "Clear cache"), List.of(heading, "\u00a0")), "storage heading is page chrome: " + heading);
                check(!SystemScreenGuard.blocksTestControl(pkg, List.of(heading, "SafeNest Test", "Clear cache"), List.of(heading, "\u00a0"), List.of("Clear cache")), "lab own Storage & cache page stays open: " + heading);
                check(!SystemScreenGuard.isForeignDetailPage(pkg, List.of(heading, "SafeNest", "Clear cache"), List.of(heading)), "consumer storage heading is page chrome: " + heading);
                check(!SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "SafeNest", "Clear cache"), List.of(heading), List.of("Clear cache"), false), "consumer own Storage & cache page stays open: " + heading);
            }
            for (String page : List.of("Settings", "Apps", "Network & internet", "Wi-Fi", "Bluetooth", "Accessibility", "Notifications", "Permissions", "Battery")) {
                List<String> labels = List.of(page, "SafeNest", "SafeNest Test");
                check(!SystemScreenGuard.blocksSafeNestControl(pkg, labels, List.of(page), List.of("Open", "Allow", "Save"), false), "consumer ordinary Settings navigation: " + page);
                check(!SystemScreenGuard.blocksTestControl(pkg, labels, List.of(page), List.of("Open", "Allow", "Save")), "lab ordinary Settings navigation: " + page);
            }
        }
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("Storage", "SafeNest Test", "0.5.0-test", "Clear storage", "Clear cache", "Space used", "App size", "23.92 MB", "User data", "Cache", "Total"),
            List.of("Storage"), List.of("Clear storage", "Clear cache")).isEmpty(), "original supplied Storage page (owner screenshot) stays open; the taps bounce");
        check(SystemScreenGuard.hasOwnIdentity(List.of("Storage", "com.safenest.app")), "consumer package identity without heading IDs");
        for (String store : List.of("com.android.packageinstaller", "com.google.android.packageinstaller", "com.samsung.android.packageinstaller", "com.miui.packageinstaller", "com.huawei.appmarket", "com.hihonor.appmarket", "com.sec.android.app.samsungapps", "com.xiaomi.market", "com.xiaomi.mipicks", "com.oppo.market", "com.heytap.market", "com.bbk.appstore", "com.vivo.appstore", "com.transsnet.store", "com.aurora.store", "com.android.vending")) {
            check(SystemScreenGuard.blocksVpnInstall(store, List.of("Unknown Fast VPN Proxy"), List.of("Install")), "broader VPN/OEM install protection retained: " + store);
            check(!SystemScreenGuard.blocksVpnInstall(store, List.of("Facebook"), List.of("Install")), "ordinary app install remains accessible: " + store);
        }
    }
}
