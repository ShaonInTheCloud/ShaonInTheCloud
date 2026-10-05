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
        System.out.println("Paid-window and UI-guard regression: " + checks + " checks passed.");
    }
}
