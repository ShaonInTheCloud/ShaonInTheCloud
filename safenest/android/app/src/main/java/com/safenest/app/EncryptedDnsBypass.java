package com.safenest.app;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Names that let a browser or app move DNS lookups out of SafeNest's local DNS filter.
 *
 * <p>Pure matching only: no network access. Used inside SafeNest's own VPN, so it only affects
 * this device and only while the user's protection is active. It answers a lookup for these
 * names with NXDOMAIN, the same way a blocked gambling name is answered.
 *
 * <p>Two kinds of names are listed:
 * <ul>
 *   <li>Public encrypted-DNS (DoH/DoT) resolver hostnames. Clients must resolve these before
 *       they can use the resolver, so refusing the lookup makes them fall back to system DNS.</li>
 *   <li>Canary names published by Mozilla and Apple. A network that answers NXDOMAIN for them
 *       tells Firefox to leave its default DoH off and Apple devices to turn iCloud Private
 *       Relay off for that network.</li>
 * </ul>
 *
 * <p>Known limits: a client that connects to a resolver by IP address, or uses a resolver not
 * listed here, is not covered. Matching is by exact name or subdomain of the listed name, never
 * by keyword, and never covers a whole provider domain such as google.com.
 */
public final class EncryptedDnsBypass {
    private EncryptedDnsBypass() {}

    private static final Set<String> NAMES;
    static {
        Set<String> names = new LinkedHashSet<>();
        // Public resolver hostnames that serve DNS over HTTPS or DNS over TLS.
        names.add("dns.google");
        names.add("cloudflare-dns.com");
        names.add("one.one.one.one");
        names.add("dns.quad9.net");
        names.add("dns11.quad9.net");
        names.add("doh.opendns.com");
        names.add("dns.adguard-dns.com");
        names.add("dns.adguard.com");
        names.add("dns.nextdns.io");
        // Canary names that tell browsers and devices to keep DNS on the network resolver.
        names.add("use-application-dns.net");
        names.add("mask.icloud.com");
        names.add("mask-h2.icloud.com");
        NAMES = Collections.unmodifiableSet(names);
    }

    /** True when the lookup is for a listed resolver or canary name or one of its subdomains. */
    public static boolean isBypassName(String hostname) {
        return DomainRules.isBlocked(hostname, NAMES);
    }

    /** Read-only copy of the listed names, for diagnostics and tests. */
    public static Set<String> names() {
        return NAMES;
    }
}
