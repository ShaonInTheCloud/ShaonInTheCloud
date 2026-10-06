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
            check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "0.4.4-test"), List.of("SafeNest Test"), List.of(action)), "test Settings: " + action);
        }
        check(SystemScreenGuard.blocksTestControl("com.google.android.packageinstaller", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "test uninstall confirmation without title IDs");
        check(SystemScreenGuard.blocksTestControl("com.android.settings", List.of("com.safenest.app.lab"), List.of(), List.of("Disconnect")), "test package identity");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("Another VPN"), List.of("Another VPN"), List.of("Forget VPN")), "another VPN remains accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest"), List.of("SafeNest"), List.of("Uninstall")), "lab guard leaves production app alone");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test", "Another VPN"), List.of("Another VPN"), List.of("Disconnect")), "background test label cannot target another VPN detail");
        check(!SystemScreenGuard.blocksTestControl("com.android.chrome", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "test phrase in browser is not a control");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test"), List.of(), List.of("Use SafeNest Test", "Turn off")), "test Accessibility revocation remains accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.settings", List.of("SafeNest Test"), List.of(), List.of("Allow", "Always-on VPN")), "test permissions and network toggles remain accessible");
        check(!SystemScreenGuard.blocksTestControl("com.android.vending", List.of("SafeNest Test"), List.of(), List.of("Uninstall")), "lab inspection limited to Settings and installers");
        System.out.println("Paid-window and UI-guard regression: " + checks + " checks passed.");
    }
}
