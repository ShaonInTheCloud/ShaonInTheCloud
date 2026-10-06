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
    private static final Set<String> PAGE_CHROME = set(
        "settings", "app info", "vpn", "accessibility", "version", "always-on vpn",
        "block connections without vpn", "forget vpn");
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
    /** Lab-only detail pages. Never matches Settings home, Accessibility list or the VPN list. */
    public static boolean blocksTestControl(String pkg, Collection<String> labels,
                                           Collection<String> titles, Collection<String> actions) {
        return !testControlReason(pkg, labels, titles, actions).isEmpty();
    }
    /** Only an exact selected SafeNest Settings title, paired with verified window ownership. */
    public static String testFocusedWindowReason(String pkg, String title) {
        if (!SETTINGS.contains(pkg) || title == null || title.length() > 240) return "";
        String text = normalize(title);
        if (text.equals("safenest test app guard")) return "test_accessibility";
        if (text.equals("safenest") || text.equals("safenest test")) return "test_settings_detail";
        return "";
    }
    public static String testControlReason(String pkg, Collection<String> labels,
                                          Collection<String> titles, Collection<String> actions) {
        boolean settings = SETTINGS.contains(pkg);
        if (!settings && !INSTALLERS.contains(pkg)) return "";
        Set<String> text = new HashSet<>();
        labels.stream().map(SystemScreenGuard::normalize).forEach(text::add);
        Set<String> pageTitles = new HashSet<>();
        titles.stream().map(SystemScreenGuard::normalize).filter(s -> !s.isEmpty() && !PAGE_CHROME.contains(s)).forEach(pageTitles::add);
        // The exact Use label occurs on our service detail page, not the services list.
        if (settings && text.contains("use safenest test app guard") &&
            text.contains("safenest test app guard")) return "test_accessibility";
        boolean own = text.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity);
        boolean ownTitle = pageTitles.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity);
        boolean knownVpnTitle = pageTitles.stream().anyMatch(s -> s.length() <= 140 && VPN_NAME.matcher(s).find());
        boolean knownVpnLabel = text.stream().anyMatch(s -> s.length() <= 140 && VPN_NAME.matcher(s).find());
        // Toolbar text has no resource ID on some Settings versions. Two distinct
        // management controls identify a detail page without treating list summaries
        // or preference-row titles as the selected application's heading.
        boolean vpnDetail = (text.contains("always-on vpn") &&
            (text.contains("forget vpn") || text.contains("delete vpn") ||
             text.contains("block connections without vpn") || text.contains("version"))) ||
            (text.contains("block connections without vpn") && text.contains("forget vpn"));
        boolean connectionDialog = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("connect") || s.equals("disconnect") || s.equals("katkaise yhteys") || s.equals("সংযোগ বিচ্ছিন্ন"));
        // A name in the VPN list is insufficient. Require actual detail/connection controls.
        if (settings && (vpnDetail || connectionDialog)) {
            if (own && (ownTitle || pageTitles.isEmpty())) return "test_vpn_detail";
            if (knownVpnTitle || (pageTitles.isEmpty() && knownVpnLabel && vpnDetail)) return "test_other_vpn_detail";
        }
        if (!own || (!pageTitles.isEmpty() && !ownTitle)) return "";
        boolean removal = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("disconnect") || s.equals("forget vpn") || s.equals("delete vpn") ||
            s.equals("forget") || s.equals("uninstall") || s.equals("force stop") ||
            s.equals("সংযোগ বিচ্ছিন্ন") || s.equals("আনইনস্টল") ||
            s.equals("katkaise yhteys") || s.equals("poista asennus"));
        return removal ? "test_app_control" : "";
    }
    private static boolean isSafeNestIdentity(String s) {
        return s.equals("safenest test") || s.equals("com.safenest.app.lab") || s.equals("safenest test local dns filter") ||
            s.equals("safenest") || s.equals("com.safenest.app") || s.equals("safenest local dns filter");
    }
    private static String normalize(String s) { return s == null ? "" : s.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT); }
}
