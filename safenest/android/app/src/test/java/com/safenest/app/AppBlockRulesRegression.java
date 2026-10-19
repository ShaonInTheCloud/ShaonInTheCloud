package com.safenest.app;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Gambling-app and VPN-browser recognition. No Android SDK required. */
public final class AppBlockRulesRegression {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Set<String> exact = AppBlockRules.parseList(List.of("# comment", "com.krikya.krikya   # Krikya", "org.xbet.client1", ""), false);
        Set<String> brands = AppBlockRules.parseList(List.of("Baji", "Raja Baji", "jeetbuzz", "1xBet", "Leon.bet", "bet365", "# x"), true);
        Set<String> vpn = Set.of("com.opera.browser", "com.opera.mini.native", "com.aloha.browser", "com.cloudmosa.puffinfree");
        Predicate<String> listed = Set.of("bet365.com", "melbet.org", "dafabet.com")::contains;
        java.util.function.BiFunction<String, String, String> r = (pkg, label) -> AppBlockRules.reason(pkg, label, exact, brands, vpn, listed);

        check(exact.equals(Set.of("com.krikya.krikya", "org.xbet.client1")), "list parsing strips comments");
        check(brands.contains("rajabaji") && brands.contains("leonbet"), "brands are compacted");
        // VPN browsers are blocked whole
        check(r.apply("com.opera.browser", "Opera").equals(AppBlockRules.VPN_BROWSER), "Opera");
        check(r.apply("com.opera.mini.native", null).equals(AppBlockRules.VPN_BROWSER), "Opera Mini");
        check(r.apply("com.cloudmosa.puffinFree", "Puffin").equals(AppBlockRules.VPN_BROWSER), "case-insensitive package");
        // Gambling apps
        check(r.apply("com.krikya.krikya", "Krikya").equals(AppBlockRules.GAMBLING_PACKAGE), "exact list");
        check(r.apply("com.bet365wrapper.bet365_application", "bet365").equals(AppBlockRules.GAMBLING_BRAND), "bet365 brand word");
        check(r.apply("com.bet365.sports", null).equals(AppBlockRules.GAMBLING_DOMAIN), "reversed package domain on blocklist");
        check(r.apply("com.application.yongbao.bj", "Baji").equals(AppBlockRules.GAMBLING_BRAND), "brand found only in the app name");
        check(r.apply("com.xyz.app", "Raja Baji").equals(AppBlockRules.GAMBLING_BRAND), "multi-word brand name");
        check(r.apply("bd.jeetbuzz.app", null).equals(AppBlockRules.GAMBLING_BRAND), "brand in package");
        check(r.apply("com.lucky.casino.slots", null).equals(AppBlockRules.GAMBLING_WORD), "casino word");
        check(r.apply("com.foo.bar", "Teen Patti Gold Poker").equals(AppBlockRules.GAMBLING_WORD), "poker in name");
        check(r.apply("org.linebet.client", null).equals(AppBlockRules.GAMBLING_WORD), "-bet shape");
        check(r.apply("com.betwinner.app", null).equals(AppBlockRules.GAMBLING_WORD), "bet- shape");
        check(r.apply("com.sportybet.android", null).equals(AppBlockRules.GAMBLING_WORD), "sportybet");
        check(r.apply("com.app.betting", "Sports").equals(AppBlockRules.GAMBLING_WORD), "betting word");
        // Ordinary apps stay usable
        for (String[] ok : new String[][] {
            {"com.whatsapp", "WhatsApp"}, {"com.facebook.katana", "Facebook"}, {"com.google.android.youtube", "YouTube"},
            {"com.bkash.customerapp", "bKash"}, {"com.konasl.nagad", "Nagad"}, {"com.daraz.android", "Daraz"},
            {"com.alphabet.app", "Alphabet Learning"}, {"com.betterhelp.app", "BetterHelp"}, {"com.beta.tester", "Beta"},
            {"com.diabetes.tracker", "Diabetes Tracker"}, {"org.tibet.news", "Tibet News"}, {"com.between.app", "Between"},
            {"com.timeslot.booking", "Timeslot"}, {"com.cricbuzz.android", "Cricbuzz"}, {"com.dsinfoway.betterbangla", "Better Bangla"},
            {"com.android.chrome", "Chrome"}, {"com.spotify.music", "Spotify"}, {"com.app.client", "Client"},
        }) check(r.apply(ok[0], ok[1]).isEmpty(), "must not block " + ok[0]);
        check(AppBlockRules.packageDomains("com.app.x").isEmpty(), "generic second label ignored");
        // Address bars
        check(AppBlockRules.addressBarIds("com.microsoft.emmx") != null, "Edge address bar (Edge has a built-in VPN)");
        check(AppBlockRules.addressBarIds("com.sec.android.app.sbrowser") != null, "Samsung Internet");
        check(AppBlockRules.addressBarIds("com.opera.browser") == null, "Opera is blocked whole, not address-checked");
        // Install pages
        Set<String> names = Set.of("opera", "opera mini", "aloha browser", "tor browser");
        check(AppBlockRules.blocksInstall(List.of("Opera browser with AI"), List.of("Install"), brands, names), "Opera install");
        check(AppBlockRules.blocksInstall(List.of("Baji Live"), List.of("Install"), brands, names), "gambling brand install");
        check(AppBlockRules.blocksInstall(List.of("Lucky Casino Slots"), List.of("Install"), brands, names), "casino install");
        check(!AppBlockRules.blocksInstall(List.of("Baji Live"), List.of("Uninstall"), brands, names), "uninstalling stays allowed");
        check(!AppBlockRules.blocksInstall(List.of("WhatsApp Messenger"), List.of("Install"), brands, names), "normal install");
        check(!AppBlockRules.blocksInstall(List.of("Operator Tools"), List.of("Install"), brands, names), "'Opera' prefix only as a word");
        // VPN apps: catalogue names (vpn_apps.txt shares the vpn_browsers format) and the generic words
        Set<String> vpnNames = new java.util.HashSet<>(names); vpnNames.add("psiphon"); vpnNames.add("proton vpn");
        check(AppBlockRules.blocksInstall(List.of("Psiphon"), List.of("Install"), brands, vpnNames), "VPN app by catalogue name");
        check(AppBlockRules.blocksInstall(List.of("Proton VPN: Fast & Secure VPN"), List.of("Install"), brands, vpnNames), "catalogue name with tagline");
        check(AppBlockRules.blocksInstall(List.of("Some New Free VPN - Unlimited"), List.of("Install"), brands, names), "unlisted VPN by the word");
        check(AppBlockRules.blocksInstall(List.of("Socks5 Proxy Client"), List.of("Update"), brands, names), "proxy app by the word, update too");
        check(!AppBlockRules.blocksInstall(List.of("Proxyfire Launcher"), List.of("Install"), brands, names), "'proxy' only as a whole word");
        check(!AppBlockRules.blocksInstall(List.of("Psiphon"), List.of("Uninstall"), brands, vpnNames), "removing a VPN stays allowed");
        // IP blocklist and raw-IP address bars
        check(IpRules.parseBlockEntry("203.0.113.7") != null, "single public IPv4");
        check(IpRules.parseBlockEntry("198.51.100.0/24 # comment") != null, "public /24");
        check(IpRules.parseBlockEntry("8.0.0.0/8") == null, "too broad a range is ignored");
        check(IpRules.parseBlockEntry("192.168.1.0/24") == null, "private range ignored");
        check(IpRules.parseBlockEntry("10.0.0.1") == null && IpRules.parseBlockEntry("127.0.0.1") == null, "private/loopback ignored");
        check(IpRules.parseBlockEntry("2001:db8::1") != null && IpRules.parseBlockEntry("fe80::1") == null, "IPv6 public vs link-local");
        check(IpRules.parseBlockEntry("casino.com") == null && IpRules.parseBlockEntry("") == null, "names and blanks ignored");
        check(IpRules.isPublicIpAddressBar("203.0.113.7"), "bare public IP in address bar");
        check(IpRules.isPublicIpAddressBar("https://203.0.113.7:8443/play?x=1"), "IP URL with port and path");
        check(!IpRules.isPublicIpAddressBar("192.168.1.1"), "router page stays usable");
        check(!IpRules.isPublicIpAddressBar("http://10.0.0.138/admin"), "LAN page stays usable");
        check(!IpRules.isPublicIpAddressBar("example.com/203.0.113.7"), "IP only in the path is not the host");
        check(!IpRules.isPublicIpAddressBar("best casino 2024"), "search text");
        System.out.println("AppBlockRulesRegression: " + checks + " checks passed");
    }
}
