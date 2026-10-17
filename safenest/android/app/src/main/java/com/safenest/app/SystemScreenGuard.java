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
    // Headings that name a Settings page, not an application: they never hide the
    // selected app's own identity. Storage is App info's sub-page with Clear storage.
    private static final Set<String> PAGE_CHROME = set(
        "settings", "app info", "vpn", "accessibility", "version", "always-on vpn",
        "block connections without vpn", "forget vpn", "storage", "storage & cache", "storage and cache",
        "space used", "manage storage", "স্টোরেজ", "স্টোরেজ ও ক্যাশে", "tallennustila", "tallennustila ja välimuisti");
    // Wiping app data ends the commitment as surely as uninstalling, so Storage
    // controls are guarded like Uninstall on both the consumer and lab paths.
    private static final Set<String> STORAGE_ACTIONS = set(
        "clear storage", "clear data", "clear cache", "clear all data", "manage space", "delete app data",
        "স্টোরেজ সাফ করুন", "ডেটা সাফ করুন", "ক্যাশে সাফ করুন", "স্টোরেজ মুছুন", "ডেটা মুছুন", "ক্যাশে মুছুন",
        "tyhjennä tallennustila", "tyhjennä tiedot", "tyhjennä välimuisti", "poista tiedot");
    private static final Set<String> PROTECTED_ACTIONS = set(
        "disconnect", "uninstall", "deactivate", "deactivate and uninstall", "disable",
        "force stop", "clear storage", "clear data", "clear cache", "clear all data", "delete app data",
        "delete vpn", "forget vpn", "forget",
        "remove", "remove profile", "delete", "turn off", "stop",
        "সংযোগ বিচ্ছিন্ন", "আনইনস্টল", "নিষ্ক্রিয়", "নিষ্ক্রিয়", "বন্ধ করুন",
        "poista", "poista asennus", "katkaise yhteys", "poista käytöstä");
    private static final Pattern VPN_NAME = Pattern.compile(
        "(?i)(?<![a-z0-9])(nordvpn|surfshark|expressvpn|proton\\s?vpn|psiphon|turbo\\s?vpn|" +
        "thunder\\s?vpn|supervpn|windscribe|tunnelbear|hotspot\\s?shield|private internet access|" +
        "cyberghost|1\\.1\\.1\\.1)(?![a-z0-9])");

    /** Android's own VPN permission prompt. Every third-party VPN must pass it once. */
    public static final String VPN_DIALOGS = "com.android.vpndialogs";
    public static boolean isSystemSurface(String pkg) {
        return SETTINGS.contains(pkg) || INSTALLERS.contains(pkg) || "com.android.vending".equals(pkg) || VPN_DIALOGS.equals(pkg);
    }
    /** The VPN "Connection request" prompt for any app other than SafeNest. SafeNest's own prompt stays usable. */
    public static boolean blocksForeignVpnConsent(String pkg, Collection<String> labels) {
        if (!VPN_DIALOGS.equals(pkg)) return false;
        boolean own = false, vpnPrompt = false;
        for (String raw : labels) {
            String s = normalize(raw);
            if (isSafeNestIdentity(s) || s.contains("safenest")) own = true;
            if (s.contains("vpn") || s.contains("connection request")) vpnPrompt = true;
        }
        return vpnPrompt && !own;
    }
    /** SafeNest's own VPN detail page in Settings (Always-on VPN lives here). Entering it is allowed. */
    public static boolean isOwnVpnDetail(String pkg, Collection<String> labels) {
        if (!SETTINGS.contains(pkg)) return false;
        boolean own = false, alwaysOn = false;
        for (String raw : labels) {
            String s = normalize(raw);
            if (isSafeNestIdentity(s)) own = true;
            if (s.equals("always-on vpn") || s.equals("সবসময় চালু vpn") || s.equals("aina päällä oleva vpn")) alwaysOn = true;
        }
        return own && alwaysOn;
    }
    /** The confirmation that actually removes the profile: a dialog whose confirm button is Forget. */
    public static boolean isForgetConfirmation(Collection<String> labels, Collection<String> actions) {
        boolean confirm = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("forget") || s.equals("ভুলে যান") || s.equals("unohda"));
        boolean question = labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.startsWith("forget this vpn") || s.startsWith("forget vpn?") || s.contains("vpn will be removed"));
        return confirm || question;
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
        // The VPN detail page must stay reachable so Always-on can be switched on; its
        // Forget VPN button only matters once the Forget confirmation appears.
        if (isOwnVpnDetail(pkg, labels)) return isForgetConfirmation(labels, actions);
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
        if (text.equals("safenest test app guard") || text.equals("safenest owner test app guard")) return "test_accessibility";
        // A window titled with the app name is either App info or the VPN detail page; only
        // content tells them apart, so the content scan decides.
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
        if (settings && ((text.contains("use safenest test app guard") && text.contains("safenest test app guard")) ||
            (text.contains("use safenest owner test app guard") && text.contains("safenest owner test app guard")))) return "test_accessibility";
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
        // Removing SafeNest's VPN profile ends protection: the Forget confirmation always bounces.
        if (settings && own && isForgetConfirmation(labels, actions)) return "test_vpn_forget";
        // A name in the VPN list is insufficient. Require actual detail/connection controls.
        if (settings && (vpnDetail || connectionDialog)) {
            if (own && (ownTitle || pageTitles.isEmpty())) {
                if (isForgetConfirmation(labels, actions)) return "test_vpn_forget";
                if (connectionDialog && !vpnDetail) return "test_vpn_detail";
                return ""; // own detail page stays open so Always-on VPN can be enabled
            }
            if (knownVpnTitle || (pageTitles.isEmpty() && knownVpnLabel && vpnDetail)) return "test_other_vpn_detail";
        }
        if (!own || (!pageTitles.isEmpty() && !ownTitle)) return "";
        boolean removal = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("disconnect") || s.equals("forget vpn") || s.equals("delete vpn") ||
            s.equals("forget") || s.equals("uninstall") || s.equals("force stop") ||
            s.equals("সংযোগ বিচ্ছিন্ন") || s.equals("আনইনস্টল") ||
            s.equals("katkaise yhteys") || s.equals("poista asennus"));
        if (removal) return "test_app_control";
        // App info › Storage: Clear storage wipes the commitment, Clear cache sits beside it.
        boolean storage = actions.stream().map(SystemScreenGuard::normalize).anyMatch(STORAGE_ACTIONS::contains);
        return storage ? "test_app_storage" : "";
    }
    private static boolean isSafeNestIdentity(String s) {
        return s.equals("safenest test") || s.equals("com.safenest.app.lab") ||
            s.equals("safenest owner test") || s.equals("com.safenest.app.ownerlab") || s.equals("safenest test local dns filter") ||
            s.equals("safenest") || s.equals("com.safenest.app") || s.equals("safenest local dns filter");
    }
    private static String normalize(String s) { return s == null ? "" : s.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT); }
}
