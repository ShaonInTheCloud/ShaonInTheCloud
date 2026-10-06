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
        System.out.println("Paid-window and UI-guard regression: " + checks + " checks passed.");
    }
}
