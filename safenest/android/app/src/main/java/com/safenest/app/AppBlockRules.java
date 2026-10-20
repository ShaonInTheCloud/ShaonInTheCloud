package com.safenest.app;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pure rules for apps SafeNest sends Home from, with no Android dependency (see AppBlockRulesRegression).
 *
 * Gambling apps are recognised, strongest signal first, by:
 *   1. an exact package ID from the updatable list (assets/blocked_apps/gambling_packages.txt);
 *   2. the package's own reversed domain being on the gambling blocklist (com.bet365.x -> bet365.com);
 *   3. a known gambling brand as a whole word of the package ID or app name (krikya, baji, jeetbuzz, 1xbet...);
 *   4. gambling words as whole words of the package ID or app name (casino, poker, slots, betting...);
 *   5. "-bet" / "bet-" brand shapes in the package ID (melbet, linebet, 22bet, betway, betwinner),
 *      with a list of ordinary words that look alike (alphabet, better, between, beta...).
 * Browsers with a built-in VPN or proxy move traffic out of the on-device DNS filter, so like Gamban they are
 * blocked as a whole (assets/blocked_apps/vpn_browsers.txt), as are VPN apps (assets/blocked_apps/vpn_apps.txt).
 * Other browsers stay usable and their address bar is checked against the blocklist instead, which works even
 * when that browser's own VPN is on. On store install pages, a title with the whole word "vpn" or "proxy" is
 * blocked too, so an unlisted VPN cannot be installed during protection.
 */
public final class AppBlockRules {
    private AppBlockRules() {}

    public static final String VPN_BROWSER = "vpn_browser";
    public static final String GAMBLING_PACKAGE = "gambling_package";
    public static final String GAMBLING_DOMAIN = "gambling_domain";
    public static final String GAMBLING_BRAND = "gambling_brand";
    public static final String GAMBLING_WORD = "gambling_word";

    /** Store titles with these whole words are VPN/proxy apps; the install page bounces even for an unlisted brand. */
    private static final Set<String> VPN_WORDS = Set.of("vpn", "proxy", "ভিপিএন", "প্রক্সি");
    private static final Set<String> WORDS = Set.of(
        "bet", "bets", "betting", "bettings", "casino", "casinos", "poker", "slots", "jackpot", "jackpots",
        "sportsbook", "baccarat", "roulette", "blackjack", "teenpatti", "rummy", "satta", "matka", "lottery", "lotto",
        "gambling", "wager", "bookmaker", "bookie", "sabong");
    /** Ordinary words that the -bet / bet- shapes would otherwise catch. */
    private static final List<String> NOT_BET = List.of(
        "alphabet", "alfabet", "sorbet", "sherbet", "tibet", "abet", "quibbet",
        "better", "betty", "between", "bethel", "bethany", "bethesda", "beta", "betamax", "betoken", "betray",
        "betroth", "bethlehem", "betel", "beth", "betula", "betacam", "betaseries");
    private static final Pattern SUFFIX_BET = Pattern.compile("^[a-z0-9]{1,8}bet$");
    private static final Pattern PREFIX_BET = Pattern.compile("^bet[a-z0-9]{2,12}$");
    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");
    // Keep combining marks inside words: Bangla vowel signs and virama are marks, not letters.
    // Separate from SPLIT so existing gambling/package recognition retains its behavior.
    private static final Pattern VPN_TITLE_SPLIT = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");

    private static boolean hasVpnTitleWord(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            // Joiners can change Bangla conjunct rendering without changing the word.
            .replace("\u200c", "").replace("\u200d", "");
        for (String word : VPN_TITLE_SPLIT.split(normalized)) {
            if (VPN_WORDS.contains(word)) return true;
        }
        return false;
    }

    /** Second-level labels too generic to treat a reversed package domain as a gambling site. */
    private static final Set<String> GENERIC_LABELS = Set.of(
        "app", "apps", "android", "mobile", "google", "example", "my", "the", "client", "game", "games", "play", "free", "pro");

    /** Address-bar view IDs per browser. Only these views are read; page content never is. */
    public static final Map<String, List<String>> ADDRESS_BARS = Map.ofEntries(
        Map.entry("com.android.chrome", List.of("url_bar")),
        Map.entry("com.chrome.beta", List.of("url_bar")),
        Map.entry("com.chrome.dev", List.of("url_bar")),
        Map.entry("com.chrome.canary", List.of("url_bar")),
        Map.entry("com.microsoft.emmx", List.of("url_bar")),
        Map.entry("com.brave.browser", List.of("url_bar")),
        Map.entry("com.vivaldi.browser", List.of("url_bar")),
        Map.entry("com.kiwibrowser.browser", List.of("url_bar")),
        Map.entry("com.sec.android.app.sbrowser", List.of("location_bar_edit_text")),
        Map.entry("com.sec.android.app.sbrowser.beta", List.of("location_bar_edit_text")),
        Map.entry("org.mozilla.firefox", List.of("mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view")),
        Map.entry("org.mozilla.firefox_beta", List.of("mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view")),
        Map.entry("org.mozilla.fenix", List.of("mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view")),
        Map.entry("org.mozilla.focus", List.of("mozac_browser_toolbar_url_view", "display_url")),
        Map.entry("com.duckduckgo.mobile.android", List.of("omnibarTextInput")),
        Map.entry("com.mi.globalbrowser", List.of("url")),
        Map.entry("com.yandex.browser", List.of("bro_omnibar_address_title_text", "suggest_omnibox_query_edit")));

    public static List<String> addressBarIds(String pkg) { return ADDRESS_BARS.get(pkg); }

    /** Lower-case alphanumeric words of a package ID or app name ("Baji Live-App" -> baji, live, app). */
    public static Set<String> words(String value) {
        Set<String> out = new HashSet<>();
        if (value == null) return out;
        for (String w : SPLIT.split(value.toLowerCase(Locale.ROOT))) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** Brand list entries normalised to their compact form ("Pin-Up" -> pinup, "Leon.bet" -> leonbet). */
    public static String compact(String brand) {
        return brand == null ? "" : brand.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static boolean betShape(String word) {
        if (!(SUFFIX_BET.matcher(word).matches() || PREFIX_BET.matcher(word).matches())) return false;
        for (String ordinary : NOT_BET) if (word.equals(ordinary) || (ordinary.length() >= 4 && word.startsWith(ordinary))) return false;
        return true;
    }

    /** Reversed-domain candidates of a package ID: com.bet365.mobile -> bet365.com. */
    public static List<String> packageDomains(String pkg) {
        String[] parts = pkg.toLowerCase(Locale.ROOT).split("\\.");
        if (parts.length < 2) return List.of();
        String tld = parts[0], name = parts[1];
        if (tld.length() < 2 || tld.length() > 6 || name.length() < 3 || GENERIC_LABELS.contains(name) || !name.matches("[a-z0-9-]+")) return List.of();
        return List.of(name + "." + tld);
    }

    /**
     * Why an app should be blocked, or "" to leave it alone.
     * @param label the launcher name, may be null
     * @param domainListed true when a hostname is on the gambling blocklist (exact name)
     */
    public static String reason(String pkg, String label, Set<String> exactPackages, Set<String> brands,
                                Set<String> vpnBrowsers, Predicate<String> domainListed) {
        if (pkg == null || pkg.isEmpty()) return "";
        String id = pkg.toLowerCase(Locale.ROOT);
        if (vpnBrowsers.contains(id)) return VPN_BROWSER;
        if (exactPackages.contains(id)) return GAMBLING_PACKAGE;
        for (String domain : packageDomains(id)) if (domainListed.test(domain)) return GAMBLING_DOMAIN;
        Set<String> idWords = words(id), nameWords = words(label);
        for (String w : idWords) if (brands.contains(w)) return GAMBLING_BRAND;
        if (label != null && brands.contains(compact(label))) return GAMBLING_BRAND;
        for (String w : nameWords) if (brands.contains(w)) return GAMBLING_BRAND;
        for (String w : idWords) if (WORDS.contains(w) || betShape(w)) return GAMBLING_WORD;
        for (String w : nameWords) if (WORDS.contains(w)) return GAMBLING_WORD;
        return "";
    }

    /** Play Store / installer page for a gambling app or a VPN browser: the app title with an Install button. */
    public static boolean blocksInstall(Collection<String> titles, Collection<String> actions, Set<String> brands, Set<String> vpnBrowserNames) {
        boolean install = actions.stream().map(a -> a.trim().toLowerCase(Locale.ROOT)).anyMatch(a ->
            a.equals("install") || a.equals("update") || a.equals("ইনস্টল") || a.equals("asenna"));
        if (!install) return false;
        for (String title : titles) {
            if (title == null || title.length() > 140) continue;
            String lower = title.toLowerCase(Locale.ROOT);
            for (String name : vpnBrowserNames) if (!name.isEmpty() && (lower.equals(name) || lower.startsWith(name + " ") || lower.startsWith(name + ":"))) return true;
            if (hasVpnTitleWord(title)) return true;
            Set<String> w = words(title);
            if (brands.contains(compact(title))) return true;
            for (String word : w) if (brands.contains(word) || WORDS.contains(word)) return true;
        }
        return false;
    }

    /** Parses an updatable list asset: one entry per line, '#' comments, compact or lower-case. */
    public static Set<String> parseList(Iterable<String> lines, boolean compactEntries) {
        Set<String> out = new HashSet<>();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash).trim();
            if (line.isEmpty()) continue;
            String value = compactEntries ? compact(line) : line.toLowerCase(Locale.ROOT);
            if (compactEntries ? value.length() >= 4 : value.contains(".")) out.add(value);
        }
        return out;
    }
}
