package com.safenest.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pure Java regression cases for rules, callable from JUnit and the standalone runner. */
public final class DomainRulesRegression {
    private static int assertions;
    private DomainRulesRegression() {}
    public static void main(String[] args) {
        runAll();
        System.out.println("Domain rules regression passed: " + assertions + " assertions.");
    }
    public static void runAll() {
        assertions = 0;
        equal("example.com", DomainRules.normalize("https://www.Example.com/path?q=1"), "explicit URL parsed safely");
        equal("example.com", DomainRules.normalize(" HTTP://EXAMPLE.COM:8080/other#fragment "), "valid URL port and fragment");
        equal("example.com", DomainRules.normalize("Example.com."), "absolute trailing dot");
        equal("example.com", DomainRules.normalize("example.com:443"), "bare explicit port");
        equal("www.com", DomainRules.normalize("www.com"), "www can itself be a registrable name");
        equal("xn--bcher-kva.example", DomainRules.normalize("https://Bücher.example/path"), "Unicode converted to punycode");
        equal("xn--bcher-kva.example", DomainRules.normalize("XN--BCHER-KVA.EXAMPLE"), "ACE normalized");
        String[] rejected = {
            "", "not a domain", "example.com?next=evil.test", "example.com#evil.test", "example.com/path", "-bad.example",
            "example.c", "com", ".com", "*.example.com", "example..com", "example.com..", "192.0.2.1", "999.999.999.999",
            "[::1]", "https://[::1]/", "example.com:0", "example.com:65536", "example.com:", "example.com:abc",
            "https://user:password@example.com/", "https://safe.example@evil.example/", "https://%65xample.com/",
            "http:///example.com", "https:example.com", "ftp://example.com", "javascript:example.com", "https://example.com\\evil",
            "example.com\nevil.example", "foo_bar.example", "https://example.com/%zz", "example.123"
        };
        for (String bad : rejected) check(DomainRules.normalize(bad) == null, "reject " + bad.replace('\n', ' '));
        check(DomainRules.normalize(null) == null, "null rejected");
        check(DomainRules.normalize(String.join("", Collections.nCopies(64, "a")) + ".example") == null, "long label rejected");
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            equal("i.example", DomainRules.normalize("I.EXAMPLE"), "case folding independent of device locale");
        } finally { Locale.setDefault(original); }
        check(DomainRules.matches("bet.example", "bet.example"), "exact rule");
        check(DomainRules.matches("M.BET.EXAMPLE.", "bet.example"), "case-insensitive subdomain rule");
        check(!DomainRules.matches("notbet.example", "bet.example"), "not a substring match");
        check(!DomainRules.matches("bet.example.attacker.test", "bet.example"), "suffix boundary enforced");
        check(!DomainRules.matches("bet.example@evil.test", "bet.example"), "credentials never match");
        check(DomainRules.matches("m.bücher.example", "xn--bcher-kva.example"), "IDN subdomain matching");
        Set<String> rules = new LinkedHashSet<>(Arrays.asList("bet.example", "xn--bcher-kva.example"));
        check(DomainRules.isBlocked("a.b.bet.example", rules), "cached suffix lookup");
        check(DomainRules.isBlocked("bücher.example", rules), "cached IDN lookup");
        check(!DomainRules.isBlocked("bet.example.evil.test", rules), "cached lookup excludes suffix spoofing");
        check(!DomainRules.isBlocked("example.com", Collections.singleton("com")), "top-level-only imported rule cannot block everything");
        Set<String> merged = DomainRules.merge(rules, Arrays.asList("https://www.new.example/path", "BET.EXAMPLE", "invalid", "https://user@bad.example", "bücher.example"));
        check(merged.containsAll(rules), "batch retains old rules");
        equal(3, merged.size(), "batch deduplicates normalized additions");
        check(merged.contains("new.example"), "batch adds new normalized rule");
        Set<String> many = new LinkedHashSet<>();
        for (int i = 0; i < 25000; i++) many.add("site" + i + ".example");
        check(DomainRules.isBlocked("sub.site24999.example", many), "large catalog exact suffix hit");
        check(!DomainRules.isBlocked("unlisted.example", many), "large catalog ordinary host remains allowed");
    }
    private static void equal(Object expected, Object actual, String why) { check(expected.equals(actual), why + ": expected " + expected + ", got " + actual); }
    private static void check(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
}
