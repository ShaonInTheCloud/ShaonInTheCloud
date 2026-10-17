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
        check(CommitmentRules.effectiveNow(t - 90_000, 20_000, t, 10_000, t, true) == t + 10_000, "same-boot rollback");
        check(CommitmentRules.effectiveNow(t + 90_000, 20_000, t, 10_000, t, true) == t + 10_000, "same-boot forward clock cannot unlock");
        check(CommitmentRules.effectiveNow(t - 90_000, 100, t, 10_000, t + 5000, false) == t + 5000, "reboot retains highwater");
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
        for (String action : List.of("Disconnect", "Forget VPN", "Uninstall", "Force stop")) {
            check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "0.4.5-test"), List.of("SafeNest Test"), List.of(action)), "test Settings: " + action);
        }
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "test uninstall confirmation without title IDs");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("com.safenest.app.lab"), List.of(), List.of("Disconnect")), "test package identity");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Another VPN"), List.of("Another VPN"), List.of("Forget VPN")), "another VPN remains accessible");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest", "Archive", "Uninstall", "Force stop", "Notifications"), List.of("App info"), List.of("Uninstall", "Force stop")), "regular SafeNest App info from supplied first screenshot");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Uninstall"), List.of("App info", "\u00a0", "  "), List.of("Uninstall")), "blank Compose section headings do not hide own uninstall page");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Another VPN"), List.of("Another VPN"), List.of("Disconnect")), "background test label cannot target another VPN detail");
        check(!SystemScreenGuard.blocksTestControl("com.android.chrome", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "test phrase in browser is not a control");
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest Owner Test"), List.of(), List.of("Uninstall")), "owner test uninstall confirmation");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("com.safenest.app.ownerlab"), List.of(), List.of("Force stop")), "owner test package identity");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Owner Test app guard", "Use SafeNest Owner Test app guard"), List.of(), List.of()).equals("test_accessibility"), "owner test accessibility switch");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest Owner Test").equals("test_settings_detail"), "owner test focused detail");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Owner Testing Notes"), List.of("SafeNest Owner Testing Notes"), List.of("Uninstall")), "similar name is not owner test");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Accessibility", "SafeNest Test app guard", "TalkBack"), List.of("Accessibility"), List.of("SafeNest Test app guard")), "Accessibility list stays available");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test"), List.of(), List.of("Allow", "Always-on VPN")), "test permissions and network toggles remain accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.vending", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "lab inspection limited to Settings and installers");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test app guard", "Use SafeNest Test app guard", "App info"), List.of("SafeNest Test app guard"), List.of()), "exact own Accessibility detail from screenshot");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("TalkBack", "Use TalkBack"), List.of("TalkBack"), List.of()), "other Accessibility services unaffected");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("VPN", "1.1.1.1", "SafeNest Test"), List.of("VPN", "1.1.1.1", "SafeNest Test"), List.of("1.1.1.1", "SafeNest Test", "Settings")), "exact supplied VPN list stays accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Settings", "SafeNest Test", "Apps", "Network & internet"), List.of("Settings"), List.of("Apps")), "Settings home stays accessible");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Always-on VPN", "Forget VPN"), List.of("SafeNest Test", "Always-on VPN"), List.of()), "own VPN detail guarded even before action is enabled");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "Always-on VPN", "Forget VPN"), List.of("1.1.1.1"), List.of()), "1.1.1.1 VPN detail guarded");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1"), List.of("1.1.1.1"), List.of("Connect", "Cancel")), "1.1.1.1 connect dialog guarded");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "Notifications"), List.of("1.1.1.1"), List.of("Allow notifications")), "another VPN ordinary app settings unaffected");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "SafeNest Test", "Forget VPN"), List.of("Unrelated VPN"), List.of("Forget VPN")), "background VPN mention cannot target unrelated profile");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Test app guard", "Use SafeNest Test app guard"), List.of(), List.of()).equals("test_accessibility"), "diagnostic reason distinguishes own switch");
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("SafeNest Test", "Version", "0.4.4-test", "Always-on VPN", "Block connections without VPN", "Forget VPN"), List.of(), List.of("Always-on VPN", "Forget VPN")).equals("test_vpn_detail"), "supplied VPN detail with unlabeled toolbar");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Always-on VPN", "Forget VPN"), List.of("Always-on VPN", "Forget VPN"), List.of()), "preference titles cannot conceal own VPN detail");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("VPN", "SafeNest Test", "Always-on VPN", "1.1.1.1"), List.of("VPN"), List.of("SafeNest Test", "Settings")), "Always-on status in VPN list must not guard the list");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Unrelated VPN", "Always-on VPN", "Block connections without VPN", "Forget VPN"), List.of("Unrelated VPN"), List.of("Forget VPN")), "strong unrelated profile heading beats a background app label");
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest"), List.of("SafeNest"), List.of("Cancel", "Uninstall")), "regular SafeNest uninstall confirmation during consented lab test");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest", "Chrome", "Uninstall", "Force stop"), List.of("Chrome"), List.of("Uninstall", "Force stop")), "other app uninstall page stays available");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("1.1.1.1", "Always-on VPN", "Forget VPN"), List.of(), List.of()), "known VPN management page without toolbar IDs");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest Test").equals("test_settings_detail"), "focused own Settings title works without page nodes");
        check(SystemScreenGuard.testFocusedWindowReason("com.android.settings", "SafeNest").equals("test_settings_detail"), "regular own Settings title works without page nodes");
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
                    check(SystemScreenGuard.blocksSafeNestControl(pkg, ownLabels, controls, false), "consumer storage action: " + pkg + " / " + action);
                    check(SystemScreenGuard.blocksSafeNestControl(pkg, ownLabels, List.of(heading), controls, false), "consumer selected Storage page: " + action);
                    for (String identity : List.of("SafeNest", "SafeNest Test", "SafeNest Owner Test", "com.safenest.app", "com.safenest.app.lab", "com.safenest.app.ownerlab")) {
                        check(SystemScreenGuard.testControlReason(pkg, List.of(heading, identity, action), List.of(heading), controls)
                            .equals("test_app_storage"), "lab/ownerlab own Storage page: " + identity + " / " + action);
                    }
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "Chrome", action), List.of(heading), controls, false), "consumer unrelated app storage: " + action);
                    check(!SystemScreenGuard.blocksTestControl(pkg, List.of(heading, "Chrome", action), List.of(heading), controls), "lab unrelated app storage: " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "Chrome", "SafeNest", action), List.of(heading, "Chrome"), controls, false), "foreign app heading beats background SafeNest label: " + action);
                    check(!SystemScreenGuard.blocksTestControl(pkg, List.of(heading, "Chrome", "SafeNest Test", action), List.of(heading, "Chrome"), controls), "lab foreign app heading beats background label: " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl("com.android.chrome", ownLabels, controls, false), "browser storage text ignored: " + action);
                    check(!SystemScreenGuard.blocksTestControl("com.android.chrome", ownLabels, List.of(heading), controls), "lab browser storage text ignored: " + action);
                    check(!SystemScreenGuard.blocksSafeNestControl(pkg, ownLabels, List.of(heading), List.of(), false), "non-actionable storage label stays usable: " + action);
                    check(!SystemScreenGuard.blocksTestControl(pkg, ownLabels, List.of(heading), List.of()), "lab non-actionable storage label stays usable: " + action);
                    check(SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "SafeNest"), List.of("\u00a0" + action.toUpperCase(java.util.Locale.ROOT) + "  "), false), "case and surrounding whitespace normalized: " + action);
                }
                List<String> overview = List.of(heading, "Internal shared storage", "Apps", "SafeNest", "SafeNest Test", "23.92 MB");
                check(!SystemScreenGuard.blocksSafeNestControl(pkg, overview, List.of(heading), List.of("Apps", "Free up space"), false), "consumer device storage overview remains accessible");
                check(!SystemScreenGuard.blocksTestControl(pkg, overview, List.of(heading), List.of("Apps", "Free up space")), "lab device storage overview remains accessible");
            }
            for (String heading : List.of("Storage & cache", "Storage and cache", "Space used", "Manage storage", "স্টোরেজ ও ক্যাশে", "Tallennustila ja välimuisti")) {
                check(SystemScreenGuard.blocksTestControl(pkg, List.of(heading, "SafeNest Test", "Clear cache"), List.of(heading, "\u00a0"), List.of("Clear cache")), "storage heading is page chrome: " + heading);
                check(SystemScreenGuard.blocksSafeNestControl(pkg, List.of(heading, "SafeNest", "Clear cache"), List.of(heading), List.of("Clear cache"), false), "consumer storage heading is page chrome: " + heading);
            }
            for (String page : List.of("Settings", "Apps", "Network & internet", "Wi-Fi", "Bluetooth", "Accessibility", "Notifications", "Permissions", "Battery")) {
                List<String> labels = List.of(page, "SafeNest", "SafeNest Test");
                check(!SystemScreenGuard.blocksSafeNestControl(pkg, labels, List.of(page), List.of("Open", "Allow", "Save"), false), "consumer ordinary Settings navigation: " + page);
                check(!SystemScreenGuard.blocksTestControl(pkg, labels, List.of(page), List.of("Open", "Allow", "Save")), "lab ordinary Settings navigation: " + page);
            }
        }
        check(SystemScreenGuard.testControlReason("com.android.settings", List.of("Storage", "SafeNest Test", "0.5.0-test", "Clear storage", "Clear cache", "Space used", "App size", "23.92 MB", "User data", "Cache", "Total"),
            List.of("Storage"), List.of("Clear storage", "Clear cache")).equals("test_app_storage"), "original supplied Storage-page regression");
        check(SystemScreenGuard.blocksSafeNestControl("com.android.settings", List.of("Storage", "com.safenest.app"), List.of(), List.of("Clear cache"), false), "consumer package identity without heading IDs");
        for (String store : List.of("com.android.packageinstaller", "com.google.android.packageinstaller", "com.samsung.android.packageinstaller", "com.miui.packageinstaller", "com.huawei.appmarket", "com.hihonor.appmarket", "com.sec.android.app.samsungapps", "com.xiaomi.market", "com.xiaomi.mipicks", "com.oppo.market", "com.heytap.market", "com.bbk.appstore", "com.vivo.appstore", "com.transsnet.store", "com.aurora.store", "com.android.vending")) {
            check(SystemScreenGuard.blocksVpnInstall(store, List.of("Unknown Fast VPN Proxy"), List.of("Install")), "broader VPN/OEM install protection retained: " + store);
            check(!SystemScreenGuard.blocksVpnInstall(store, List.of("Facebook"), List.of("Install")), "ordinary app install remains accessible: " + store);
        }
    }
}
