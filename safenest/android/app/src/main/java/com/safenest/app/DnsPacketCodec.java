package com.safenest.app;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Android-independent DNS/IPv4 codec. It only accepts complete, single-question UDP DNS requests. */
public final class DnsPacketCodec {
    public static final int MAX_DNS_UDP_SIZE = 1232;
    private DnsPacketCodec() {}

    public static final class Query {
        public final byte[] dns;
        public final String hostname;
        public final int type;
        public final int dnsClass;
        public final int questionEnd;
        public final int udpLimit;
        private Query(byte[] dns, Name name, int udpLimit) {
            this.dns = dns;
            this.hostname = name.text;
            this.questionEnd = name.end + 4;
            this.type = u16(dns, name.end);
            this.dnsClass = u16(dns, name.end + 2);
            this.udpLimit = udpLimit;
        }
    }

    public static final class Request {
        public final Query query;
        public final int sourcePort;
        private final byte[] source;
        private final byte[] destination;
        private final int packetId;
        private Request(byte[] packet, int header, Query query) {
            this.query = query;
            this.sourcePort = u16(packet, header);
            this.source = Arrays.copyOfRange(packet, 12, 16);
            this.destination = Arrays.copyOfRange(packet, 16, 20);
            this.packetId = u16(packet, 4);
        }
    }

    /** Reject fragments, malformed lengths, wrong destination and non-UDP packets before indexing. */
    public static Request parseRequest(byte[] packet, byte[] resolverAddress) {
        if (packet == null || packet.length < 28 || resolverAddress == null || resolverAddress.length != 4) return null;
        if ((packet[0] & 0xf0) != 0x40 || (packet[9] & 0xff) != 17) return null;
        int header = (packet[0] & 0x0f) * 4;
        int total = u16(packet, 2);
        if (header < 20 || total > packet.length || total < header + 8 || (u16(packet, 6) & 0x3fff) != 0) return null;
        for (int i = 0; i < 4; i++) if (packet[16 + i] != resolverAddress[i]) return null;
        if (u16(packet, header + 2) != 53 || u16(packet, header) == 0) return null;
        int udpLength = u16(packet, header + 4);
        if (udpLength < 20 || udpLength != total - header) return null;
        // TUN supplies complete IP packets, including valid IP checksums.
        if (checksum(packet, 0, header, 0) != 0) return null;
        if (u16(packet, header + 6) != 0 && !validUdpChecksum(packet, header, udpLength)) return null;
        byte[] dns = Arrays.copyOfRange(packet, header + 8, total);
        Query query = parseQuery(dns);
        return query == null ? null : new Request(packet, header, query);
    }

    public static Query parseQuery(byte[] dns) {
        if (dns == null || dns.length < 17 || dns.length > 65507) return null;
        // QR=0, opcode=QUERY, TC=0, one question, no answer/authority section.
        if ((dns[2] & 0xfe) != 0 || (dns[3] & 0x0f) != 0 || u16(dns, 4) != 1 || u16(dns, 6) != 0 || u16(dns, 8) != 0) return null;
        Name name = readName(dns, 12, false);
        if (name == null || name.end + 4 > dns.length) return null;
        int at = name.end + 4;
        int udpLimit = 512;
        boolean optFound = false;
        int additional = u16(dns, 10);
        // EDNS is the only supported extra query record. Reject TSIG/SIG rather than forge a response.
        if (additional > 1) return null;
        if (additional == 1) {
            Name rrName = readName(dns, at, false);
            if (rrName == null || !rrName.text.isEmpty() || rrName.end + 10 > dns.length) return null;
            int base = rrName.end;
            if (u16(dns, base) != 41 || (dns[base + 5] & 0xff) != 0) return null; // EDNS version 0
            int dataLength = u16(dns, base + 8);
            at = base + 10 + dataLength;
            if (at > dns.length) return null;
            int optionAt = base + 10;
            while (optionAt < at) {
                if (optionAt + 4 > at) return null;
                optionAt += 4 + u16(dns, optionAt + 2);
                if (optionAt > at) return null;
            }
            udpLimit = Math.max(512, Math.min(MAX_DNS_UDP_SIZE, u16(dns, base + 2)));
            optFound = true;
        }
        if (at != dns.length) return null;
        return new Query(dns.clone(), name, optFound ? udpLimit : 512);
    }

    /** NXDOMAIN (3), SERVFAIL (2), or NOERROR/NODATA (0); excludes query OPT bytes/counts. */
    public static byte[] error(Query query, int rcode) {
        byte[] out = Arrays.copyOf(query.dns, query.questionEnd);
        out[2] = (byte) (0x80 | (query.dns[2] & 1));
        out[3] = (byte) (0x80 | (query.dns[3] & 0x10) | (rcode & 15));
        put16(out, 4, 1);
        put16(out, 6, 0);
        put16(out, 8, 0);
        put16(out, 10, 0);
        return out;
    }

    /** Synthesized A/AAAA results never claim DNSSEC validation and fit the client's UDP limit. */
    public static byte[] addresses(Query query, List<byte[]> addresses) {
        if (query.type != 1 && query.type != 28) throw new IllegalArgumentException("Only A and AAAA may contain addresses");
        int addressLength = query.type == 1 ? 4 : 16;
        int count = Math.min(addresses.size(), Math.max(0, (query.udpLimit - query.questionEnd) / (12 + addressLength)));
        byte[] out = Arrays.copyOf(error(query, 0), query.questionEnd + count * (12 + addressLength));
        put16(out, 6, count);
        int at = query.questionEnd;
        for (int i = 0; i < count; i++) {
            byte[] address = addresses.get(i);
            if (address == null || address.length != addressLength) throw new IllegalArgumentException("Address family does not match question");
            out[at++] = (byte) 0xc0;
            out[at++] = 0x0c;
            put16(out, at, query.type); at += 2;
            put16(out, at, 1); at += 2;
            put16(out, at, 0); at += 2;
            put16(out, at, 60); at += 2;
            put16(out, at, addressLength); at += 2;
            System.arraycopy(address, 0, out, at, addressLength); at += addressLength;
        }
        return out;
    }

    /** Validate transaction ID, response bit, question and all record bounds before using an upstream reply. */
    public static boolean matchesResponse(Query query, byte[] reply) {
        if (reply == null || reply.length < 17 || reply.length > 65535) return false;
        if (u16(reply, 0) != u16(query.dns, 0) || (reply[2] & 0xf8) != 0x80 || u16(reply, 4) != 1) return false;
        Name name = readName(reply, 12, false);
        if (name == null || name.end + 4 > reply.length || !name.text.equals(query.hostname)
                || u16(reply, name.end) != query.type || u16(reply, name.end + 2) != query.dnsClass) return false;
        int at = name.end + 4;
        int records = u16(reply, 6) + u16(reply, 8) + u16(reply, 10);
        if (records > (reply.length - at) / 11) return false;
        for (int i = 0; i < records; i++) {
            Name rr = readName(reply, at, true);
            if (rr == null || rr.end + 10 > reply.length) return false;
            at = rr.end + 10 + u16(reply, rr.end + 8);
            if (at > reply.length) return false;
        }
        return at == reply.length;
    }

    /** Return a proper TC response when a rare upstream reply cannot fit the UDP transport. */
    public static byte[] fitUdp(Query query, byte[] reply) {
        if (reply.length <= query.udpLimit) return reply;
        byte[] out = error(query, reply[3] & 15);
        out[2] |= 2;
        return out;
    }

    public static byte[] ipv4Reply(Request request, byte[] dnsReply) {
        byte[] dns = fitUdp(request.query, dnsReply);
        int length = 28 + dns.length;
        byte[] out = new byte[length];
        out[0] = 0x45;
        put16(out, 2, length);
        put16(out, 4, request.packetId);
        put16(out, 6, 0x4000);
        out[8] = 64; out[9] = 17;
        System.arraycopy(request.destination, 0, out, 12, 4);
        System.arraycopy(request.source, 0, out, 16, 4);
        put16(out, 10, checksum(out, 0, 20, 0));
        put16(out, 20, 53);
        put16(out, 22, request.sourcePort);
        put16(out, 24, length - 20);
        System.arraycopy(dns, 0, out, 28, dns.length);
        // IPv4 permits zero UDP checksum, but calculate it to detect transit mistakes too.
        int sum = pseudoSum(out, length - 20);
        int value = checksum(out, 20, length - 20, sum);
        put16(out, 26, value == 0 ? 0xffff : value);
        return out;
    }

    public static boolean isTruncated(byte[] reply) { return reply.length >= 12 && (reply[2] & 2) != 0; }
    public static int rcode(byte[] reply) { return reply.length >= 12 ? reply[3] & 15 : 2; }

    private static final class Name {
        final String text;
        final int end;
        Name(String text, int end) { this.text = text; this.end = end; }
    }

    private static Name readName(byte[] data, int start, boolean allowPointers) {
        int at = start, end = -1, steps = 0, wireLength = 1;
        StringBuilder text = new StringBuilder();
        while (at < data.length && ++steps <= 128) {
            int size = data[at++] & 255;
            if ((size & 0xc0) == 0xc0) {
                if (!allowPointers || at >= data.length) return null;
                int target = ((size & 0x3f) << 8) | (data[at++] & 255);
                if (target >= at - 2) return null; // backwards pointers only: excludes cycles
                if (end < 0) end = at;
                at = target;
            } else if (size == 0) {
                return new Name(text.toString().toLowerCase(Locale.ROOT), end < 0 ? at : end);
            } else {
                if (size > 63 || at + size > data.length || (wireLength += size + 1) > 255) return null;
                if (text.length() > 0) text.append('.');
                for (int i = 0; i < size; i++) {
                    int c = data[at + i] & 255;
                    // DNS hostname labels cannot contain a literal separator or control byte.
                    if (c <= 32 || c >= 127 || c == '.' || c == '\\') return null;
                    text.append((char) c);
                }
                at += size;
            }
        }
        return null;
    }

    private static boolean validUdpChecksum(byte[] packet, int header, int udpLength) {
        return checksum(packet, header, udpLength, pseudoSum(packet, udpLength)) == 0;
    }
    private static int pseudoSum(byte[] packet, int udpLength) {
        return u16(packet, 12) + u16(packet, 14) + u16(packet, 16) + u16(packet, 18) + 17 + udpLength;
    }
    private static int checksum(byte[] data, int offset, int length, int initial) {
        long sum = initial;
        for (int i = offset; i < offset + length; i += 2) sum += ((data[i] & 255) << 8) | (i + 1 < offset + length ? data[i + 1] & 255 : 0);
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return (int) ~sum & 65535;
    }
    private static int u16(byte[] data, int at) { return ((data[at] & 255) << 8) | (data[at + 1] & 255); }
    private static void put16(byte[] data, int at, int value) { data[at] = (byte) (value >>> 8); data[at + 1] = (byte) value; }
}
