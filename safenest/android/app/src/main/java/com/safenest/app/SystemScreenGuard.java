package com.safenest.app;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Narrow UI matching for the Accessibility guard. Model: SafeNest's own detail pages in
 * Settings (App info, Storage, VPN) stay open; what bounces to Home is a destructive
 * ACTION — the tap on Uninstall / Force stop / Clear storage / Forget VPN, and the
 * confirmation dialog behind it. Never blocks all Settings, all Play Store, or every installer.
 */
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
    /** Android's own VPN permission prompt. Every third-party VPN must pass it once. */
    public static final String VPN_DIALOGS = "com.android.vpndialogs";
    // Headings that name a Settings page, not an application: they never hide the selected app's identity.
    private static final Set<String> PAGE_CHROME = set(
        "settings", "app info", "vpn", "accessibility", "version", "always-on vpn",
        "block connections without vpn", "forget vpn", "storage", "storage & cache", "storage and cache",
        "space used", "manage storage", "স্টোরেজ", "স্টোরেজ ও ক্যাশে", "tallennustila", "tallennustila ja välimuisti");
    /** Controls whose TAP ends or weakens the commitment. Matched against the tapped control's text. */
    private static final Set<String> DESTRUCTIVE_CONTROLS = set(
        "uninstall", "force stop", "disable", "deactivate", "deactivate and uninstall",
        "clear storage", "clear data", "clear cache", "clear all data", "manage space", "delete app data",
        "forget vpn", "forget", "delete vpn", "disconnect", "remove", "remove profile", "turn off", "stop",
        "আনইনস্টল", "জোর করে বন্ধ করুন", "বন্ধ করুন", "নিষ্ক্রিয়", "সংযোগ বিচ্ছিন্ন",
        "স্টোরেজ সাফ করুন", "ডেটা সাফ করুন", "ক্যাশে সাফ করুন", "স্টোরেজ মুছুন", "ডেটা মুছুন", "ক্যাশে মুছুন", "ভুলে যান",
        "poista", "poista asennus", "pakota lopetus", "katkaise yhteys", "poista käytöstä",
        "tyhjennä tallennustila", "tyhjennä tiedot", "tyhjennä välimuisti", "poista tiedot", "unohda");
    private static final Set<String> CONFIRM_BUTTONS = set("ok", "uninstall", "force stop", "delete", "clear", "forget", "disconnect", "stop", "disable",
        "ঠিক আছে", "আনইনস্টল", "মুছুন", "ভুলে যান", "সংযোগ বিচ্ছিন্ন", "poista", "poista asennus", "unohda", "katkaise yhteys");
    private static final Pattern VPN_NAME = Pattern.compile(
        "(?i)(?<![a-z0-9])(nordvpn|surfshark|expressvpn|proton\\s?vpn|psiphon|turbo\\s?vpn|" +
        "thunder\\s?vpn|supervpn|windscribe|tunnelbear|hotspot\\s?shield|private internet access|" +
        "cyberghost|1\\.1\\.1\\.1)(?![a-z0-9])");

    public static boolean isSystemSurface(String pkg) {
        return SETTINGS.contains(pkg) || INSTALLERS.contains(pkg) || "com.android.vending".equals(pkg) || VPN_DIALOGS.equals(pkg);
    }
    public static boolean isSettingsPackage(String pkg) { return SETTINGS.contains(pkg); }
    public static boolean isInstallerPackage(String pkg) { return INSTALLERS.contains(pkg); }

    /** Any label naming SafeNest (consumer, lab or owner-lab build). */
    public static boolean hasOwnIdentity(Collection<String> labels) {
        return labels.stream().map(SystemScreenGuard::normalize).anyMatch(SystemScreenGuard::isSafeNestIdentity);
    }
    /** The text of a tapped control that must not be used on SafeNest during a lock. */
    public static boolean isDestructiveControl(String text) {
        String s = normalize(text);
        return DESTRUCTIVE_CONTROLS.contains(s) || s.startsWith("deactivate and uninstall");
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
    /** The confirmation that removes the VPN profile: a dialog whose confirm button is Forget. */
    public static boolean isForgetConfirmation(Collection<String> labels, Collection<String> actions) {
        boolean confirm = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("forget") || s.equals("ভুলে যান") || s.equals("unohda"));
        boolean question = labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.startsWith("forget this vpn") || s.startsWith("forget vpn?") || s.contains("vpn will be removed"));
        return confirm || question;
    }

    /**
     * A confirmation dialog for a destructive action on SafeNest. Some dialogs (Force stop?,
     * Delete app data?) do not repeat the app name, so the caller says whether SafeNest's own
     * page was on screen moments ago.
     */
    public static String confirmationReason(String pkg, Collection<String> labels, Collection<String> actions, boolean recentlyOnOwnPage) {
        boolean settings = SETTINGS.contains(pkg), installer = INSTALLERS.contains(pkg);
        if (!settings && !installer) return "";
        Set<String> text = new HashSet<>();
        labels.stream().map(SystemScreenGuard::normalize).forEach(text::add);
        Set<String> buttons = new HashSet<>();
        actions.stream().map(SystemScreenGuard::normalize).forEach(buttons::add);
        boolean own = text.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity) || recentlyOnOwnPage;
        if (!own) return "";
        boolean confirm = buttons.stream().anyMatch(CONFIRM_BUTTONS::contains);
        if (installer) {
            // Uninstall confirmation: app name plus OK/Uninstall. (Play Store and the installer's
            // install screens never carry our identity, so they are unaffected.)
            return confirm ? "uninstall_confirm" : "";
        }
        if (isForgetConfirmation(labels, actions)) return "forget_vpn_confirm";
        boolean forceStop = text.stream().anyMatch(s -> s.startsWith("force stop?") || s.startsWith("force stop this app") ||
            s.contains("may misbehave") || s.startsWith("জোর করে বন্ধ করবেন") || s.startsWith("pakotetaanko"));
        boolean wipe = text.stream().anyMatch(s -> s.startsWith("delete app data") || s.startsWith("clear app data") ||
            s.contains("will be deleted permanently") || s.contains("app data?") || s.startsWith("অ্যাপের ডেটা মুছবেন") || s.startsWith("poistetaanko sovelluksen tiedot"));
        boolean disconnect = buttons.contains("disconnect") || buttons.contains("katkaise yhteys") || buttons.contains("সংযোগ বিচ্ছিন্ন");
        boolean disableService = text.stream().anyMatch(s -> s.startsWith("stop safenest") || s.startsWith("turn off safenest") || s.contains("app guard?"));
        if (forceStop && confirm) return "force_stop_confirm";
        if (wipe && confirm) return "clear_storage_confirm";
        if (disconnect) return "disconnect_confirm";
        if (disableService && confirm) return "accessibility_confirm";
        return "";
    }

    /** Consumer builds: own Accessibility switch, destructive confirmations. Pages stay open. */
    public static boolean blocksSafeNestControl(String pkg, Collection<String> labels,
                                                Collection<String> actions, boolean checkedToggle) {
        return blocksSafeNestControl(pkg, labels, actions, checkedToggle, false);
    }
    public static boolean blocksSafeNestControl(String pkg, Collection<String> labels,
                                                Collection<String> actions, boolean checkedToggle, boolean recentlyOnOwnPage) {
        if (!SETTINGS.contains(pkg) && !INSTALLERS.contains(pkg)) return false;
        boolean ownToggle = checkedToggle && labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("use safenest") || s.equals("use safenest app guard"));
        return ownToggle || !confirmationReason(pkg, labels, actions, recentlyOnOwnPage).isEmpty();
    }
    public static boolean blocksVpnInstall(String pkg, Collection<String> titles, Collection<String> actions) {
        if (!INSTALLERS.contains(pkg) && !"com.android.vending".equals(pkg)) return false;
        boolean install = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("install") || s.equals("update") || s.equals("ইনস্টল") || s.equals("asenna"));
        return install && titles.stream().anyMatch(s -> s.length() <= 140 && VPN_NAME.matcher(s).find());
    }
    /** Lab-only. Never matches Settings home, Accessibility list, the VPN list or SafeNest's own detail pages. */
    public static boolean blocksTestControl(String pkg, Collection<String> labels,
                                           Collection<String> titles, Collection<String> actions) {
        return !testControlReason(pkg, labels, titles, actions).isEmpty();
    }
    /** Only the exact own Accessibility service title: switching it off kills the guard before any dialog could be read. */
    public static String testFocusedWindowReason(String pkg, String title) {
        if (!SETTINGS.contains(pkg) || title == null || title.length() > 240) return "";
        String text = normalize(title);
        if (text.equals("safenest test app guard") || text.equals("safenest owner test app guard")) return "test_accessibility";
        return "";
    }
    public static String testControlReason(String pkg, Collection<String> labels,
                                          Collection<String> titles, Collection<String> actions) {
        return testControlReason(pkg, labels, titles, actions, false);
    }
    public static String testControlReason(String pkg, Collection<String> labels,
                                          Collection<String> titles, Collection<String> actions, boolean recentlyOnOwnPage) {
        boolean settings = SETTINGS.contains(pkg);
        if (!settings && !INSTALLERS.contains(pkg)) return "";
        Set<String> text = new HashSet<>();
        labels.stream().map(SystemScreenGuard::normalize).forEach(text::add);
        Set<String> pageTitles = new HashSet<>();
        titles.stream().map(SystemScreenGuard::normalize).filter(s -> !s.isEmpty() && !PAGE_CHROME.contains(s)).forEach(pageTitles::add);
        // Own Accessibility detail page: the Use switch is the only control, and it is fatal.
        if (settings && ((text.contains("use safenest test app guard") && text.contains("safenest test app guard")) ||
            (text.contains("use safenest owner test app guard") && text.contains("safenest owner test app guard")))) return "test_accessibility";
        boolean own = text.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity);
        boolean ownTitle = pageTitles.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity);
        // Another app's page may list SafeNest in a summary; a foreign heading means it is not ours.
        if (!pageTitles.isEmpty() && !ownTitle && own) own = false;
        String confirmation = confirmationReason(pkg, labels, actions, recentlyOnOwnPage);
        if (!confirmation.isEmpty() && (own || !ownTitleConflict(pageTitles))) return "test_" + confirmation;
        // Other VPN profiles: their detail page or connect dialog is a takeover path.
        boolean knownVpnTitle = pageTitles.stream().anyMatch(s -> s.length() <= 140 && VPN_NAME.matcher(s).find());
        boolean knownVpnLabel = text.stream().anyMatch(s -> s.length() <= 140 && VPN_NAME.matcher(s).find());
        boolean vpnDetail = (text.contains("always-on vpn") &&
            (text.contains("forget vpn") || text.contains("delete vpn") ||
             text.contains("block connections without vpn") || text.contains("version"))) ||
            (text.contains("block connections without vpn") && text.contains("forget vpn"));
        boolean connectionDialog = actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("connect") || s.equals("disconnect") || s.equals("katkaise yhteys") || s.equals("সংযোগ বিচ্ছিন্ন"));
        if (settings && !own && (vpnDetail || connectionDialog) &&
            (knownVpnTitle || (pageTitles.isEmpty() && knownVpnLabel && vpnDetail))) return "test_other_vpn_detail";
        return "";
    }
    /** A foreign application's heading on the page (never page chrome). */
    private static boolean ownTitleConflict(Set<String> pageTitles) {
        if (pageTitles.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity)) return false;
        // Dialog headings ("Force stop?", "Delete app data?") are questions, not app names.
        return pageTitles.stream().anyMatch(s -> !s.endsWith("?") && s.length() <= 40 && !DESTRUCTIVE_CONTROLS.contains(s));
    }
    /** Another app's detail page in Settings: its heading names an app that is not SafeNest. */
    public static boolean isForeignDetailPage(String pkg, Collection<String> labels, Collection<String> titles) {
        if (!SETTINGS.contains(pkg)) return false;
        Set<String> pageTitles = new HashSet<>();
        titles.stream().map(SystemScreenGuard::normalize).filter(s -> !s.isEmpty() && !PAGE_CHROME.contains(s)).forEach(pageTitles::add);
        if (!ownTitleConflict(pageTitles)) return false;
        return labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("uninstall") || s.equals("force stop") || s.equals("always-on vpn") || s.equals("clear storage") || s.equals("disable"));
    }
    private static boolean isSafeNestIdentity(String s) {
        return s.equals("safenest test") || s.equals("com.safenest.app.lab") ||
            s.equals("safenest owner test") || s.equals("com.safenest.app.ownerlab") || s.equals("safenest test local dns filter") ||
            s.equals("safenest") || s.equals("com.safenest.app") || s.equals("safenest local dns filter");
    }
    private static String normalize(String s) { return s == null ? "" : s.replace(' ', ' ').trim().toLowerCase(Locale.ROOT); }
}
