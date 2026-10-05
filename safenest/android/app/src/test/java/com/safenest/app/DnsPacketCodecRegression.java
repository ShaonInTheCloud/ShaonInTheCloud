package com.safenest.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/** Runs without Android or JUnit: java com.safenest.app.DnsPacketCodecRegression. */
public final class DnsPacketCodecRegression {
    private static int assertions;
    private static final byte[] CLIENT = {10, 91, 0, 2};
    private static final byte[] DNS = {10, 91, 0, 53};
    private DnsPacketCodecRegression() {}

    public static void main(String[] args) {
        runAll();
        System.out.println("DNS codec regression passed: " + assertions + " assertions including 20,000 malformed-packet fuzz inputs.");
    }

    public static void runAll() {
        assertions = 0;
        parsesQuestionAndEdns();
        errorResponsesDiscardOpt();
        synthesizesCorrectRecords();
        matchesOnlyTheRequestedTransaction();
        enforcesIpv4AndUdpBounds();
        createsChecksummedReply();
        capsUdpAtClientLimit();
        malformedInputCannotThrow();
    }

    private static void parsesQuestionAndEdns() {
        DnsPacketCodec.Query a = parse(question("WWW.Example.com", 1, false));
        equal("www.example.com", a.hostname, "ASCII names normalize independently of locale");
        equal(1, a.type, "A type");
        equal(1, a.dnsClass, "IN class");
        equal(512, a.udpLimit, "non-EDNS limit");
        DnsPacketCodec.Query aaaa = parse(question("example.org", 28, true));
        equal(1232, aaaa.udpLimit, "large EDNS payload is limited to 1232");
        equal(28, aaaa.type, "AAAA type");
        byte[] tinyOpt = question("example.org", 1, true);
        put16(tinyOpt, tinyOpt.length - 8, 128);
        equal(512, parse(tinyOpt).udpLimit, "EDNS cannot reduce below 512");
        byte[] copied = question("example.org", 1, false);
        DnsPacketCodec.Query immutable = parse(copied);
        copied[0] = 0;
        equal(0x12, immutable.dns[0] & 255, "query owns its buffer");
    }

    private static void errorResponsesDiscardOpt() {
        DnsPacketCodec.Query q = parse(question("example.org", 1, true));
        byte[] nxdomain = DnsPacketCodec.error(q, 3);
        equal(q.questionEnd, nxdomain.length, "NXDOMAIN has no trailing uncounted OPT bytes");
        equal(0, u16(nxdomain, 10), "NXDOMAIN additional count");
        equal(0, u16(nxdomain, 6), "NXDOMAIN answer count");
        equal(3, DnsPacketCodec.rcode(nxdomain), "NXDOMAIN code");
        check((nxdomain[2] & 0x81) == 0x81, "response and RD flags");
        check((nxdomain[3] & 0x20) == 0, "no forged DNSSEC AD flag");
        check(DnsPacketCodec.matchesResponse(q, nxdomain), "well-formed NXDOMAIN response");
        byte[] failure = DnsPacketCodec.error(q, 2);
        equal(q.questionEnd, failure.length, "SERVFAIL strips OPT too");
        equal(2, DnsPacketCodec.rcode(failure), "SERVFAIL code");
        check(DnsPacketCodec.matchesResponse(q, failure), "well-formed SERVFAIL response");
    }

    private static void synthesizesCorrectRecords() {
        DnsPacketCodec.Query q = parse(question("example.com", 1, true));
        byte[] address = {(byte) 192, 0, 2, 23};
        byte[] answer = DnsPacketCodec.addresses(q, Collections.singletonList(address));
        check(DnsPacketCodec.matchesResponse(q, answer), "A response structure");
        equal(1, u16(answer, 6), "A answer count");
        equal(0xc00c, u16(answer, q.questionEnd), "answer name pointer");
        equal(1, u16(answer, q.questionEnd + 2), "answer type");
        equal(60, u16(answer, q.questionEnd + 8), "TTL 60 seconds");
        equal(4, u16(answer, q.questionEnd + 10), "A RDLENGTH");
        check(Arrays.equals(address, Arrays.copyOfRange(answer, answer.length - 4, answer.length)), "A bytes");
        DnsPacketCodec.Query v6 = parse(question("example.com", 28, true));
        byte[] address6 = new byte[16]; address6[0] = 0x20; address6[1] = 1; address6[15] = 7;
        byte[] answer6 = DnsPacketCodec.addresses(v6, Collections.singletonList(address6));
        equal(16, u16(answer6, v6.questionEnd + 10), "AAAA RDLENGTH");
        check(DnsPacketCodec.matchesResponse(v6, answer6), "AAAA response structure");
        byte[] nodata = DnsPacketCodec.addresses(v6, Collections.emptyList());
        equal(0, DnsPacketCodec.rcode(nodata), "empty address family is NODATA, not NXDOMAIN");
        equal(0, u16(nodata, 6), "NODATA answer count");
        boolean rejected = false;
        try { DnsPacketCodec.addresses(q, Collections.singletonList(address6)); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid family refused");
    }

    private static void matchesOnlyTheRequestedTransaction() {
        DnsPacketCodec.Query q = parse(question("example.com", 1, false));
        byte[] good = DnsPacketCodec.addresses(q, Collections.singletonList(new byte[]{(byte)192, 0, 2, 1}));
        check(DnsPacketCodec.matchesResponse(q, good), "valid reply accepted");
        byte[] wrongId = good.clone(); wrongId[0] ^= 1;
        check(!DnsPacketCodec.matchesResponse(q, wrongId), "wrong ID rejected");
        byte[] wrongHost = good.clone(); wrongHost[13] = 'z';
        check(!DnsPacketCodec.matchesResponse(q, wrongHost), "same ID different hostname rejected");
        byte[] wrongType = good.clone(); put16(wrongType, q.questionEnd - 4, 28);
        check(!DnsPacketCodec.matchesResponse(q, wrongType), "wrong type rejected");
        check(!DnsPacketCodec.matchesResponse(q, q.dns), "query masquerading as reply rejected");
        check(!DnsPacketCodec.matchesResponse(q, Arrays.copyOf(good, good.length - 1)), "truncated RDATA rejected");
        byte[] pointerCycle = good.clone(); put16(pointerCycle, q.questionEnd, 0xc000 | q.questionEnd);
        check(!DnsPacketCodec.matchesResponse(q, pointerCycle), "compression loop rejected");
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        check(!DnsPacketCodec.matchesResponse(q, trailing), "uncounted trailing bytes rejected");
    }

    private static void enforcesIpv4AndUdpBounds() {
        byte[] packet = ipv4(question("example.com", 1, true));
        check(DnsPacketCodec.parseRequest(packet, DNS) != null, "valid DNS packet accepted");
        byte[] extra = Arrays.copyOf(packet, packet.length + 8);
        check(DnsPacketCodec.parseRequest(extra, DNS) != null, "IP total length excludes read buffer padding");
        byte[] shortIp = packet.clone(); put16(shortIp, 2, packet.length + 1); fixHeader(shortIp);
        check(DnsPacketCodec.parseRequest(shortIp, DNS) == null, "oversized IP total length rejected");
        byte[] badUdp = packet.clone(); put16(badUdp, 24, packet.length - 21);
        check(DnsPacketCodec.parseRequest(badUdp, DNS) == null, "UDP length mismatch rejected");
        byte[] fragment = packet.clone(); put16(fragment, 6, 0x2000); fixHeader(fragment);
        check(DnsPacketCodec.parseRequest(fragment, DNS) == null, "first fragment rejected");
        byte[] fragment2 = packet.clone(); put16(fragment2, 6, 1); fixHeader(fragment2);
        check(DnsPacketCodec.parseRequest(fragment2, DNS) == null, "later fragment rejected");
        byte[] wrongTarget = packet.clone(); wrongTarget[19] = 54; fixHeader(wrongTarget);
        check(DnsPacketCodec.parseRequest(wrongTarget, DNS) == null, "only local DNS destination accepted");
        byte[] badPort = packet.clone(); put16(badPort, 22, 443);
        check(DnsPacketCodec.parseRequest(badPort, DNS) == null, "HTTPS UDP port ignored");
        byte[] badHeader = packet.clone(); badHeader[10] ^= 1;
        check(DnsPacketCodec.parseRequest(badHeader, DNS) == null, "bad IP checksum rejected");
        byte[] badUdpChecksum = packet.clone(); put16(badUdpChecksum, 26, 1);
        check(DnsPacketCodec.parseRequest(badUdpChecksum, DNS) == null, "bad nonzero UDP checksum rejected");
    }

    private static void createsChecksummedReply() {
        DnsPacketCodec.Request req = DnsPacketCodec.parseRequest(ipv4(question("example.com", 1, false)), DNS);
        byte[] reply = DnsPacketCodec.ipv4Reply(req, DnsPacketCodec.error(req.query, 3));
        equal(reply.length, u16(reply, 2), "IP total length");
        equal(reply.length - 20, u16(reply, 24), "UDP length");
        equal(53, u16(reply, 20), "UDP source port");
        equal(45678, u16(reply, 22), "UDP reply destination port");
        check(Arrays.equals(DNS, Arrays.copyOfRange(reply, 12, 16)), "source IP swapped");
        check(Arrays.equals(CLIENT, Arrays.copyOfRange(reply, 16, 20)), "destination IP swapped");
        equal(0, checksum(reply, 0, 20, 0), "IP checksum independently verified");
        int pseudo = u16(reply, 12) + u16(reply, 14) + u16(reply, 16) + u16(reply, 18) + 17 + reply.length - 20;
        equal(0, checksum(reply, 20, reply.length - 20, pseudo), "UDP pseudo-header checksum independently verified");
    }

    private static void capsUdpAtClientLimit() {
        DnsPacketCodec.Query q = parse(question("example.com", 1, false));
        byte[] big = new byte[2000]; big[3] = (byte)0x80;
        byte[] fitted = DnsPacketCodec.fitUdp(q, big);
        check(fitted.length <= 512, "UDP respects non-EDNS budget");
        check(DnsPacketCodec.isTruncated(fitted), "oversized response has TC flag");
        check(DnsPacketCodec.matchesResponse(q, fitted), "TC response has consistent record counts");
        byte[] many = DnsPacketCodec.addresses(q, Collections.nCopies(100, new byte[]{(byte)192, 0, 2, 1}));
        check(many.length <= 512, "synthesized address RRset fits UDP budget");
        check(DnsPacketCodec.matchesResponse(q, many), "bounded address RRset is well formed");
    }

    private static void malformedInputCannotThrow() {
        byte[] noZero = question("example.com", 1, false);
        noZero[noZero.length - 5] = 3;
        check(DnsPacketCodec.parseQuery(noZero) == null, "unterminated QNAME rejected");
        byte[] duplicate = question("example.com", 1, false); put16(duplicate, 4, 2);
        check(DnsPacketCodec.parseQuery(duplicate) == null, "multiple question ambiguity rejected");
        byte[] trailing = Arrays.copyOf(question("example.com", 1, false), 45);
        check(DnsPacketCodec.parseQuery(trailing) == null, "unadvertised EDNS bytes rejected");
        byte[] compressed = question("example.com", 1, false); compressed[12] = (byte)0xc0; compressed[13] = 12;
        check(DnsPacketCodec.parseQuery(compressed) == null, "self-referencing query rejected");
        Random random = new Random(2401);
        DnsPacketCodec.Query q = parse(question("example.com", 1, false));
        for (int i = 0; i < 10000; i++) {
            byte[] junk = new byte[random.nextInt(512)]; random.nextBytes(junk);
            DnsPacketCodec.parseQuery(junk);
            DnsPacketCodec.parseRequest(junk, DNS);
            DnsPacketCodec.matchesResponse(q, junk);
            assertions++;
        }
        byte[] seed = ipv4(question("example.com", 1, true));
        for (int i = 0; i < 10000; i++) {
            byte[] mutation = seed.clone();
            mutation[random.nextInt(mutation.length)] = (byte)random.nextInt(256);
            DnsPacketCodec.parseRequest(mutation, DNS);
            DnsPacketCodec.parseQuery(Arrays.copyOfRange(mutation, 28, mutation.length));
            assertions++;
        }
    }

    private static DnsPacketCodec.Query parse(byte[] bytes) {
        DnsPacketCodec.Query q = DnsPacketCodec.parseQuery(bytes);
        check(q != null, "test fixture must parse"); return q;
    }
    private static byte[] question(String host, int type, boolean edns) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = new byte[12]; header[0] = 0x12; header[1] = 0x34; header[2] = 1; header[5] = 1; header[11] = (byte)(edns ? 1 : 0);
        out.write(header, 0, header.length);
        for (String label : host.split("\\.")) { byte[] chars = label.getBytes(StandardCharsets.US_ASCII); out.write(chars.length); out.write(chars, 0, chars.length); }
        out.write(0); out.write(type >>> 8); out.write(type); out.write(0); out.write(1);
        if (edns) { byte[] opt = {0, 0, 41, 16, 0, 0, 0, (byte)0x80, 0, 0, 0}; out.write(opt, 0, opt.length); }
        return out.toByteArray();
    }
    private static byte[] ipv4(byte[] dns) {
        byte[] out = new byte[28 + dns.length]; out[0] = 0x45; put16(out, 2, out.length); put16(out, 4, 15); out[8] = 64; out[9] = 17;
        System.arraycopy(CLIENT, 0, out, 12, 4); System.arraycopy(DNS, 0, out, 16, 4);
        put16(out, 20, 45678); put16(out, 22, 53); put16(out, 24, 8 + dns.length); System.arraycopy(dns, 0, out, 28, dns.length);
        fixHeader(out); return out;
    }
    private static void fixHeader(byte[] packet) { put16(packet, 10, 0); put16(packet, 10, checksum(packet, 0, 20, 0)); }
    private static int checksum(byte[] packet, int at, int count, int seed) {
        int sum = seed;
        for (int i = 0; i < count; i += 2) sum += (packet[at + i] & 255) * 256 + (i + 1 < count ? packet[at + i + 1] & 255 : 0);
        while (sum > 65535) sum = (sum & 65535) + (sum >>> 16);
        return (~sum) & 65535;
    }
    private static int u16(byte[] bytes, int at) { return (bytes[at] & 255) * 256 + (bytes[at + 1] & 255); }
    private static void put16(byte[] bytes, int at, int number) { bytes[at] = (byte)(number >>> 8); bytes[at + 1] = (byte)number; }
    private static void equal(Object want, Object got, String why) { check(want.equals(got), why + ": expected " + want + ", got " + got); }
    private static void check(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
}
