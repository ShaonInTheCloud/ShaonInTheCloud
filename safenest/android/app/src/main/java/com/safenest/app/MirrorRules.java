package com.safenest.app;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mirror detection: gambling brands open new domains every week (babu88a.com, jeetbuzz-bd.live,
 * mbaji999.net) long before any list carries them. A site whose registrable name is a known
 * brand plus a short tail (or a short lead) is treated as that brand. Pure Java, no Android.
 *
 * Shares blocked_apps/gambling_app_brands.txt with the app blocker and the iPhone DNS filter.
 */
public final class MirrorRules {
    private MirrorRules() {}

    /** Second-level public suffixes seen in Bangladesh and the usual mirror TLDs. */
    private static final Set<String> TWO_PART_SUFFIXES = Set.of(
        "com.bd", "net.bd", "org.bd", "co.uk", "org.uk", "co.in", "com.au", "co.nz", "com.sg", "com.my", "com.ph",
        "co.za", "com.br", "com.tr", "com.pk", "com.np", "co.ke", "com.ng", "com.bn", "co.jp", "com.cn");
    /** Tails that make even a short brand unambiguous (baji-live, 1win-bd). */
    private static final Pattern MIRROR_TAIL = Pattern.compile(
        "^-?(?:[0-9]{1,4}|[a-z]|app|apk|bd|bdt|bet|bets|bangla|bangladesh|live|login|link|links|mirror|new|now|official|" +
        "online|vip|win|wins|casino|game|games|play|pro|site|web|club|zone|go|ok|fun|cash|india|in|bn|desh)?[0-9]{0,4}$");
    /** Any short alphanumeric tail is a mirror when the brand itself is long enough to be unmistakable. */
    private static final Pattern LOOSE_TAIL = Pattern.compile("^-?[a-z0-9]{0,8}(?:-[a-z0-9]{1,8})?$");
    private static final Pattern LEAD = Pattern.compile("^(?:www|m|the|my|go|get|new|bd|app|m-|bd-|my-|go-)?$");
    /** Ordinary words that contain or equal a brand name; never treat these labels as mirrors. */
    private static final Set<String> NOT_MIRROR = Set.of(
        "bajirao", "bajigur", "betwayne", "betanova", "melbetter", "unibetter", "bovadaho", "velkia", "velkis",
        "bet365sports");

    /** The label before the public suffix: "babu88a" for www.babu88a.com or babu88a.com.bd. */
    public static String registrableLabel(String hostname) {
        if (hostname == null) return "";
        String host = hostname.toLowerCase(Locale.ROOT).trim();
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        String[] parts = host.split("\\.");
        if (parts.length < 2) return "";
        int suffixParts = 1;
        if (parts.length >= 3 && TWO_PART_SUFFIXES.contains(parts[parts.length - 2] + "." + parts[parts.length - 1])) suffixParts = 2;
        if (parts.length <= suffixParts) return "";
        return parts[parts.length - 1 - suffixParts];
    }

    /** Returns the brand this hostname mirrors, or null when it is not a mirror. */
    public static String mirrorOf(String hostname, Collection<String> brands) {
        String label = registrableLabel(hostname);
        if (label.length() < 4 || NOT_MIRROR.contains(label)) return null;
        for (String brand : brands) {
            if (brand == null || brand.length() < 4 || brand.length() > label.length()) continue;
            int at = label.indexOf(brand);
            while (at >= 0) {
                String lead = label.substring(0, at), tail = label.substring(at + brand.length());
                boolean leadOk = LEAD.matcher(lead).matches();
                boolean tailOk = brand.length() >= 6 ? LOOSE_TAIL.matcher(tail).matches() : MIRROR_TAIL.matcher(tail).matches();
                if (leadOk && tailOk) return brand;
                at = label.indexOf(brand, at + 1);
            }
        }
        return null;
    }

    public static boolean isMirror(String hostname, Collection<String> brands) { return mirrorOf(hostname, brands) != null; }
}
