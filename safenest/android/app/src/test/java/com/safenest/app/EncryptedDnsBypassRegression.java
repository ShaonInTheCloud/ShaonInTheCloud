package com.safenest.app;

/** Standalone checks; no Android classes. Run with run-core-tests.sh. */
public final class EncryptedDnsBypassRegression {
    private EncryptedDnsBypassRegression() {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void runAll() {
        // Listed resolver names and their subdomains are matched, in any case and with a trailing dot.
        check(EncryptedDnsBypass.isBypassName("dns.google"), "dns.google");
        check(EncryptedDnsBypass.isBypassName("DNS.Google."), "case and trailing dot");
        check(EncryptedDnsBypass.isBypassName("dns64.dns.google"), "subdomain of dns.google");
        check(EncryptedDnsBypass.isBypassName("mozilla.cloudflare-dns.com"), "resolver subdomain");
        check(EncryptedDnsBypass.isBypassName("abc123.cloudflare-dns.com"), "per-profile subdomain");
        check(EncryptedDnsBypass.isBypassName("one.one.one.one"), "one.one.one.one");
        check(EncryptedDnsBypass.isBypassName("dns.quad9.net"), "quad9");
        check(EncryptedDnsBypass.isBypassName("use-application-dns.net"), "Mozilla canary");
        check(EncryptedDnsBypass.isBypassName("mask.icloud.com"), "Apple relay canary");
        check(EncryptedDnsBypass.isBypassName("mask-h2.icloud.com"), "Apple relay canary h2");

        // Whole provider domains and unrelated names must never match.
        check(!EncryptedDnsBypass.isBypassName("google.com"), "google.com");
        check(!EncryptedDnsBypass.isBypassName("www.google.com"), "www.google.com");
        check(!EncryptedDnsBypass.isBypassName("cloudflare.com"), "cloudflare.com");
        check(!EncryptedDnsBypass.isBypassName("one.one"), "partial label match");
        check(!EncryptedDnsBypass.isBypassName("icloud.com"), "icloud.com");
        check(!EncryptedDnsBypass.isBypassName("appleid.icloud.com"), "other icloud subdomain");
        check(!EncryptedDnsBypass.isBypassName("notdns.google.example.com"), "name embedded in other domain");
        check(!EncryptedDnsBypass.isBypassName("mydns.google.com"), "keyword-like lookalike");
        check(!EncryptedDnsBypass.isBypassName("xdns.google"), "different first label");

        // Invalid input is false, never an exception.
        check(!EncryptedDnsBypass.isBypassName(null), "null");
        check(!EncryptedDnsBypass.isBypassName(""), "empty");
        check(!EncryptedDnsBypass.isBypassName("localhost"), "single label");
        check(!EncryptedDnsBypass.isBypassName("8.8.8.8"), "ip literal is not a name");

        // The list stays normalized so DomainRules matching keeps working.
        for (String name : EncryptedDnsBypass.names()) {
            check(DomainRules.normalizeHostname(name) != null && DomainRules.normalizeHostname(name).equals(name),
                "list entry not normalized: " + name);
        }
        try {
            EncryptedDnsBypass.names().add("example.com");
            check(false, "names() must be read-only");
        } catch (UnsupportedOperationException expected) { /* ok */ }
    }

    public static void main(String[] args) {
        runAll();
        System.out.println("EncryptedDnsBypassRegression passed");
    }
}
