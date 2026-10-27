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
        "com.hihonor.systemmanager", "com.miui.securitycenter", "com.samsung.android.lool",
        "com.coloros.safecenter", "com.oplus.safecenter", "com.vivo.permissionmanager",
        "com.iqoo.secure", "com.oneplus.security", "com.transsion.phonemaster");
    private static final Set<String> INSTALLERS = set(
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
        "com.huawei.appmarket", "com.hihonor.appmarket", "com.sec.android.app.samsungapps",
        "com.xiaomi.market", "com.xiaomi.mipicks", "com.oppo.market", "com.heytap.market",
        "com.bbk.appstore", "com.vivo.appstore", "com.transsnet.store", "com.aurora.store");
    /** Android's own VPN permission prompt. Every third-party VPN must pass it once. */
    public static final String VPN_DIALOGS = "com.android.vpndialogs";
    // Page headings describe Settings, not a different selected application.
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
    // Known brands only: used by the lab test-control classifier, which must not treat the
    // generic word VPN as a brand.
    private static final Pattern VPN_NAME = Pattern.compile(
        "(?i)(?<![a-z0-9])(nordvpn|surfshark|expressvpn|proton\\s?vpn|psiphon|turbo\\s?vpn|" +
        "thunder\\s?vpn|supervpn|windscribe|tunnelbear|hotspot\\s?shield|private internet access|" +
        "cyberghost|1\\.1\\.1\\.1)(?![a-z0-9])");
    // Install guard: any app that calls itself a VPN/proxy/tunnel, plus brands that do not say so.
    private static final Pattern VPN_ANY = Pattern.compile(
        "(?i)(?<![a-z0-9])(vpn|proxy|nordvpn|surfshark|expressvpn|proton\\s?vpn|psiphon|turbo\\s?vpn|" +
        "thunder\\s?vpn|supervpn|windscribe|tunnelbear|hotspot\\s?shield|private internet access|" +
        "cyberghost|1\\.1\\.1\\.1|warp|orbot|tor browser|outline|wireguard|openvpn|shadowsocks|" +
        "v2ray|v2rayng|clash|hiddify|nekobox|sing-box|betternet|hola|zenmate|speedify|ivacy|purevpn|" +
        "atlas\\s?vpn|mullvad|ultrasurf|intra|dns\\s?changer|secure\\s?dns|ভিপিএন)(?![a-z0-9])");

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
        // Android's "Stop <service>?" dialog names the service, not the app, and the switch page behind it is
        // hidden from the service on Android 14+, so the dialog itself must count as ours. Only a question that
        // names one of SafeNest's own service labels as a whole phrase counts; another app's "App Guard" does not.
        boolean disableService = text.stream().anyMatch(SystemScreenGuard::isOwnServiceQuestion);
        boolean own = text.stream().anyMatch(SystemScreenGuard::isSafeNestIdentity) || recentlyOnOwnPage || disableService;
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
    /**
     * Title-aware form kept for callers that read page headings. Owner contract (17 Oct 2026):
     * SafeNest's own pages (App info, Storage, VPN detail) stay open in every build; only the
     * destructive tap and its confirmation dialog bounce. Headings therefore never widen the guard.
     */
    public static boolean blocksSafeNestControl(String pkg, Collection<String> labels, Collection<String> titles,
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
        return install && titles.stream().anyMatch(s -> s.length() <= 140 && VPN_ANY.matcher(s).find());
    }
    /** Private DNS dialog/page: switching it would route DNS around the local filter. */
    public static boolean blocksPrivateDns(String pkg, Collection<String> labels, Collection<String> actions) {
        if (!SETTINGS.contains(pkg)) return false;
        boolean page = labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("private dns") || s.equals("select private dns mode") || s.equals("private dns provider hostname") ||
            s.equals("ব্যক্তিগত dns") || s.equals("private dns mode"));
        if (!page) return false;
        // The dialog is the only place the mode changes; the network-settings row alone stays usable.
        return labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("private dns provider hostname") || s.equals("automatic") || s.equals("off") || s.equals("বন্ধ"))
            || actions.stream().map(SystemScreenGuard::normalize).anyMatch(s -> s.equals("save") || s.equals("সংরক্ষণ"));
    }
    /** Another VPN's connect/always-on dialog in Settings while SafeNest holds the protection period. */
    public static boolean blocksOtherVpnControl(String pkg, Collection<String> labels, Collection<String> actions) {
        if (!SETTINGS.contains(pkg)) return false;
        boolean safeNest = labels.stream().map(SystemScreenGuard::normalize).anyMatch(s -> s.contains("safenest"));
        if (safeNest) return false;
        boolean vpn = labels.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("always-on vpn") || s.equals("block connections without vpn") || s.endsWith(" vpn") || s.equals("vpn") ||
            s.contains("connection request") || s.contains("wants to set up a vpn connection"));
        if (!vpn) return false;
        return actions.stream().map(SystemScreenGuard::normalize).anyMatch(s ->
            s.equals("connect") || s.equals("ok") || s.equals("always-on vpn") || s.equals("সংযোগ") || s.equals("ঠিক আছে"));
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
    /** Next step for the own-VPN-page watch. */
    public enum OwnVpnWatchStep { CONTINUE, LEAVE, STOP }
    /** How long one arming of the own-VPN-page watch may run; any later event on the page re-arms it. */
    public static final long OWN_VPN_WATCH_MS = 5L * 60L * 1000L;
    /**
     * Owner exception (18 Oct 2026): during an active guard SafeNest's own VPN page stays open while Always-on is
     * off, so it can be switched on, and the same visit must end as soon as it is on. Android 14+ hides that switch
     * from the service and the flip produces no usable event, so the service watches the running VPN service's own
     * isAlwaysOn while that exact page stays in front. Leave the moment it is on; stop watching when the guard
     * ends, the page is no longer in front, or the bounded window runs out. Never watches any other page.
     */
    public static OwnVpnWatchStep ownVpnWatchStep(boolean connected, boolean guardActive, boolean samePageInFront,
                                                  boolean alwaysOnIsSafeNest, long elapsedMs) {
        if (!connected || !guardActive || !samePageInFront) return OwnVpnWatchStep.STOP;
        if (alwaysOnIsSafeNest) return OwnVpnWatchStep.LEAVE;
        return elapsedMs >= OWN_VPN_WATCH_MS ? OwnVpnWatchStep.STOP : OwnVpnWatchStep.CONTINUE;
    }

    /** SafeNest's own Accessibility service labels (not translated), matched as a whole phrase. */
    private static final Pattern OWN_SERVICE_LABEL = Pattern.compile(
        "(?<![\\p{L}\\p{N}])safenest(?: test| owner test)? app guard(?![\\p{L}\\p{N}])");
    /** A dialog question ("Stop SafeNest app guard?", "SafeNest app guard বন্ধ করবেন?") naming SafeNest's own service. */
    static boolean isOwnServiceQuestion(String normalized) {
        return normalized.length() <= 200 && normalized.indexOf('?') >= 0 && OWN_SERVICE_LABEL.matcher(normalized).find();
    }
    private static boolean isSafeNestIdentity(String s) {
        return s.equals("safenest test") || s.equals("com.safenest.app.lab") ||
            s.equals("safenest owner test") || s.equals("com.safenest.app.ownerlab") || s.equals("safenest test local dns filter") ||
            s.equals("safenest") || s.equals("com.safenest.app") || s.equals("safenest local dns filter");
    }
    private static String normalize(String s) { return s == null ? "" : s.replace(' ', ' ').trim().toLowerCase(Locale.ROOT); }
}
