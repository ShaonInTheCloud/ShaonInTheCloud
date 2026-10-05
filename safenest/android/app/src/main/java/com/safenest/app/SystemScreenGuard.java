package com.safenest.app;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

/** Narrow UI matching. Never blocks all Settings, all Play Store, or every installer. */
public final class SystemScreenGuard {
    private SystemScreenGuard() {}
    private static Set<String> set(String... entries) { return new HashSet<>(Arrays.asList(entries)); }
    private static final Set<String> SETTINGS = set(
        "com.android.settings", "com.hihonor.settings", "com.huawei.systemmanager",
        "com.hihonor.systemmanager", "com.miui.securitycenter");
    private static final Set<String> INSTALLERS = set(
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
        "com.huawei.appmarket", "com.hihonor.appmarket");
    private static final Set<String> PROTECTED_ACTIONS = set(
        "disconnect", "uninstall", "deactivate", "deactivate and uninstall", "disable",
        "force stop", "clear storage", "clear data", "delete vpn", "forget vpn", "forget",
        "remove", "remove profile", "delete", "turn off", "stop",
        "সংযোগ বিচ্ছিন্ন", "আনইনস্টল", "নিষ্ক্রিয়", "নিষ্ক্রিয়", "বন্ধ করুন",
        "poista", "poista asennus", "katkaise yhteys", "poista käytöstä");
    private static final Pattern VPN_NAME = Pattern.compile(
        "(?i)(?<![a-z0-9])(nordvpn|surfshark|expressvpn|proton\\s?vpn|psiphon|turbo\\s?vpn|" +
        "thunder\\s?vpn|supervpn|windscribe|tunnelbear|hotspot\\s?shield|private internet access|" +
        "cyberghost|1\\.1\\.1\\.1)(?![a-z0-9])");

    public static boolean isSystemSurface(String pkg) {
        return SETTINGS.contains(pkg) || INSTALLERS.contains(pkg) || "com.android.vending".equals(pkg);
    }
    public static boolean blocksSafeNestControl(String pkg, Collection<String> labels,
                                                Collection<String> actions, boolean checkedToggle) {
        if (!SETTINGS.contains(pkg) && !INSTALLERS.contains(pkg)) return false;
        boolean target = labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("safenest") || s.equals("safenest app guard") || s.equals("com.safenest.app") ||
            s.equals("safenest local dns filter") || s.equals("use safenest") || s.equals("use safenest app guard"));
        if (!target) return false;
        boolean ownToggle = checkedToggle && labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("use safenest") || s.equals("use safenest app guard"));
        return ownToggle || actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            PROTECTED_ACTIONS.contains(s) || s.startsWith("deactivate and uninstall"));
    }
    public static boolean blocksVpnInstall(String pkg, Collection<String> titles, Collection<String> actions) {
        if (!INSTALLERS.contains(pkg) && !"com.android.vending".equals(pkg)) return false;
        boolean install = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("install") || s.equals("update") || s.equals("ইনস্টল") || s.equals("asenna"));
        return install && titles.stream().anyMatch(s -> s.length() <= 140 && VPN_NAME.matcher(s).find());
    }
    private static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
}
