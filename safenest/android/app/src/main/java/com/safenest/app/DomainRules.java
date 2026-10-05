package com.safenest.app;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pure hostname normalization and suffix matching; never performs DNS or opens a URL. */
public final class DomainRules {
    private DomainRules() {}

    /** Accept a domain or explicit HTTP(S) URL, rejecting credentials and ambiguous bare queries. */
    public static String normalize(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.isEmpty() || value.length() > 8192) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 32 || c == 127 || Character.isWhitespace(c) || c == '\\') return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String authority;
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            try {
                URI uri = new URI(value);
                if (uri.getRawUserInfo() != null || uri.getRawAuthority() == null) return null;
                authority = uri.getRawAuthority();
            } catch (URISyntaxException invalid) { return null; }
        } else {
            // A query/path without an explicit URL scheme is not a hostname.
            if (value.indexOf('/') >= 0 || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) return null;
            authority = value;
        }
        if (authority.indexOf('@') >= 0 || authority.indexOf('%') >= 0 || authority.indexOf('[') >= 0 || authority.indexOf(']') >= 0) return null;
        int colon = authority.indexOf(':');
        String host = authority;
        if (colon >= 0) {
            if (colon != authority.lastIndexOf(':')) return null;
            host = authority.substring(0, colon);
            String portText = authority.substring(colon + 1);
            if (portText.isEmpty() || portText.length() > 5) return null;
            for (int i = 0; i < portText.length(); i++) if (portText.charAt(i) < '0' || portText.charAt(i) > '9') return null;
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) return null;
        }
        String normalized = normalizeHostname(host);
        if (normalized == null) return null;
        if (normalized.startsWith("www.") && normalized.substring(4).indexOf('.') >= 0) normalized = normalized.substring(4);
        return normalized.indexOf('.') >= 0 ? normalized : null;
    }

    /** Canonical ASCII DNS name, preserving all labels (including www). */
    public static String normalizeHostname(String hostname) {
        if (hostname == null || hostname.isEmpty() || hostname.length() > 1024) return null;
        String ascii;
        try { ascii = IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException invalid) { return null; }
        if (ascii.endsWith(".")) ascii = ascii.substring(0, ascii.length() - 1);
        if (ascii.length() > 253 || ascii.isEmpty()) return null;
        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2) return null;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') return null;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) return null;
            }
        }
        String last = labels[labels.length - 1];
        if (last.length() < 2) return null;
        boolean digitsOnly = true;
        for (int i = 0; i < last.length(); i++) if (last.charAt(i) < '0' || last.charAt(i) > '9') digitsOnly = false;
        return digitsOnly ? null : ascii;
    }

    public static boolean matches(String hostname, String rule) {
        String host = normalizeHostname(hostname);
        String base = normalizeHostname(rule);
        return host != null && base != null && (host.equals(base) || host.endsWith("." + base));
    }

    /** With normalized rules, lookup cost depends on the number of host labels, not catalog size. */
    public static boolean isBlocked(String hostname, Set<String> normalizedRules) {
        String host = normalizeHostname(hostname);
        if (host == null) return false;
        while (host.indexOf('.') >= 0) {
            if (normalizedRules.contains(host)) return true;
            host = host.substring(host.indexOf('.') + 1);
        }
        return false;
    }

    /** Additive import: invalid entries are skipped and old rules are retained. */
    public static Set<String> merge(Collection<String> existing, Collection<String> additions) {
        Set<String> merged = new LinkedHashSet<>();
        for (String value : existing) { String normalized = normalize(value); if (normalized != null) merged.add(normalized); }
        for (String value : additions) { String normalized = normalize(value); if (normalized != null) merged.add(normalized); }
        return merged;
    }
}
