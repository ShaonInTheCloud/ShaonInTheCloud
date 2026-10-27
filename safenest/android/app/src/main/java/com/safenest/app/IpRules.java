package com.safenest.app;

import java.util.Locale;

/**
 * Pure checks for the IP blocklist and raw-IP addresses typed into a browser. No Android dependency.
 * A gambling site opened as http://203.0.113.7 never asks DNS, so the DNS filter cannot see it.
 */
public final class IpRules {
    private IpRules() {}

    /** Parsed CIDR as {address bytes, prefix}; null when malformed, private/reserved, or too broad to be safe. */
    public static Object[] parseBlockEntry(String raw) {
        if (raw == null) return null;
        String line = raw.trim();
        int hash = line.indexOf('#');
        if (hash >= 0) line = line.substring(0, hash).trim();
        if (line.isEmpty()) return null;
        String addr = line; int prefix;
        int slash = line.indexOf('/');
        byte[] bytes;
        if (slash >= 0) { addr = line.substring(0, slash); }
        bytes = v4(addr);
        if (bytes == null) bytes = v6(addr);
        if (bytes == null) return null;
        int max = bytes.length * 8;
        try { prefix = slash >= 0 ? Integer.parseInt(line.substring(slash + 1)) : max; } catch (NumberFormatException e) { return null; }
        // Never black-hole a large range by mistake: at most a /20 in IPv4 or a /32 in IPv6.
        if (prefix > max || prefix < (bytes.length == 4 ? 20 : 32)) return null;
        if (bytes.length == 4 && !isPublicV4(bytes)) return null;
        if (bytes.length == 16 && ((bytes[0] & 0xfe) == 0xfc || (bytes[0] == (byte) 0xfe && (bytes[1] & 0xc0) == 0x80) || allZero(bytes, 15))) return null;
        return new Object[] {bytes, prefix};
    }

    private static boolean allZero(byte[] b, int upto) { for (int i = 0; i < upto; i++) if (b[i] != 0) return false; return true; }

    /** IPv4 dotted quad, or null. */
    public static byte[] v4(String s) {
        String[] p = s.split("\\.", -1);
        if (p.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (p[i].isEmpty() || p[i].length() > 3 || !p[i].chars().allMatch(Character::isDigit)) return null;
            int v = Integer.parseInt(p[i]);
            if (v > 255) return null;
            out[i] = (byte) v;
        }
        return out;
    }

    private static byte[] v6(String s) {
        if (!s.contains(":") || !s.matches("[0-9a-fA-F:]+")) return null;
        try { byte[] b = java.net.InetAddress.getByName(s).getAddress(); return b.length == 16 ? b : null; }
        catch (Exception e) { return null; }
    }

    /** Internet-routable IPv4: not private, loopback, link-local, CGNAT, multicast or reserved. */
    public static boolean isPublicV4(byte[] b) {
        int a = b[0] & 0xff, c = b[1] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && c >= 64 && c <= 127) return false;
        if (a == 169 && c == 254) return false;
        if (a == 172 && c >= 16 && c <= 31) return false;
        if (a == 192 && c == 168) return false;
        if (a == 192 && c == 0 && (b[2] & 0xff) == 0) return false;
        if (a == 198 && (c == 18 || c == 19)) return false;
        return true;
    }

    /**
     * A browser address bar showing a public IPv4 site ("203.0.113.7", "http://203.0.113.7:8080/x").
     * Home-router pages (192.168.x.x, 10.x) stay usable.
     */
    public static boolean isPublicIpAddressBar(String text) {
        if (text == null) return false;
        String v = text.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty() || v.length() > 2048 || v.chars().anyMatch(Character::isWhitespace)) return false;
        int scheme = v.indexOf("://");
        if (scheme >= 0) { String sc = v.substring(0, scheme); if (!sc.equals("http") && !sc.equals("https")) return false; v = v.substring(scheme + 3); }
        int end = v.length();
        for (char ch : new char[] {'/', '?', '#'}) { int i = v.indexOf(ch); if (i >= 0 && i < end) end = i; }
        String host = v.substring(0, end);
        int at = host.lastIndexOf('@'); if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':'); if (colon >= 0) host = host.substring(0, colon);
        byte[] b = v4(host);
        return b != null && isPublicV4(b);
    }
}
