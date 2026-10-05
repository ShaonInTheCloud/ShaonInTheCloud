package com.safenest.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Standalone meaningful wire-format fixtures: no Android, network, or Gradle dependency. */
public final class DnsAliasInspectorRegression {
    private static int assertions;
    private DnsAliasInspectorRegression() {}

    public static void main(String[] args) {
        runAll();
        System.out.println("DNS alias regression passed: " + assertions
                + " assertions, including 10,000 malformed response/RDATA inputs.");
    }

    public static void runAll() {
        assertions = 0;
        followsReachableCanonicalNamesOnly();
        compressedAndInternationalNames();
        invalidCanonicalChains();
        followsDnameSuffixesOnly();
        honorsServiceBindingSemantics();
        malformedBindingsAreInvalid();
        rejectsUnmatchedAndTruncatedReplies();
        returnedDataIsImmutable();
        fuzzNeverThrows();
    }

    private static void followsReachableCanonicalNamesOnly() {
        DnsPacketCodec.Query q = query("www.example.com", 1);
        byte[] normal = response(q, records(rr("www.example.com", 1, new byte[]{(byte)192, 0, 2, 5})), records(), records());
        targets(q, normal, "ordinary A record has no aliases");
        byte[] chain = response(q, records(
                rr("cdn.example.net", 5, wire("blocked.example.org")),
                rr("unrelated.example.net", 5, wire("another-blocked.example.org")),
                rr("www.example.com", 5, wire("cdn.example.net")),
                rr("blocked.example.org", 1, new byte[]{(byte)192, 0, 2, 7})), records(), records());
        byte[] unchanged = chain.clone();
        targets(q, chain, "out-of-order reachable CNAME chain only", "cdn.example.net", "blocked.example.org");
        check(Arrays.equals(chain, unchanged), "inspector never mutates upstream answer");
        byte[] unrelated = response(q,
                records(rr("unused.example", 5, new byte[]{(byte)0xff})),
                records(rr("www.example.com", 5, wire("blocked-authority.example"))),
                records(rr("www.example.com", 5, wire("blocked-additional.example"))));
        targets(q, unrelated, "unreachable/authority/additional aliases do not affect policy");
        byte[] otherClass = response(q, records(rr(wire("www.example.com"), 5, 3, wire("blocked.example"))), records(), records());
        targets(q, otherClass, "other DNS classes do not redirect IN query");
        byte[] duplicate = response(q, records(rr("www.example.com", 5, wire("cdn.example")),
                rr("www.example.com", 5, wire("cdn.example"))), records(), records());
        targets(q, duplicate, "duplicate identical CNAME deduplicates", "cdn.example");
    }

    private static void compressedAndInternationalNames() {
        DnsPacketCodec.Query q = query("WWW.Example.com", 1);
        // Label 'cdn' plus pointer to 'Example.com' within the question.
        byte[] compressed = concat(new byte[]{3, 'c', 'd', 'n'}, pointer(16));
        targets(q, response(q, records(rr(pointer(12), 5, 1, compressed)), records(), records()),
                "backwards label compression", "cdn.example.com");
        targets(q, response(q, records(rr("www.example.com", 5, wire("XN--BCHER-KVA.Example"))), records(), records()),
                "international domains stay ACE and case independent", "xn--bcher-kva.example");
        String longest = repeat('a', 63) + "." + repeat('b', 63) + "." + repeat('c', 63) + "." + repeat('d', 61);
        targets(q, response(q, records(rr("www.example.com", 5, wire(longest))), records(), records()),
                "maximum 255-octet wire hostname accepted", longest);
        String tooLong = repeat('a', 63) + "." + repeat('b', 63) + "." + repeat('c', 63) + "." + repeat('d', 62);
        invalid(q, response(q, records(rr("www.example.com", 5, wire(tooLong))), records(), records()),
                "overlong expanded name rejected");
        invalid(q, response(q, records(rr("www.example.com", 5, new byte[]{2, (byte)0xc3, (byte)0xbc, 0})), records(), records()),
                "raw UTF-8 rejected instead of inconsistent Unicode matching");
    }

    private static void invalidCanonicalChains() {
        DnsPacketCodec.Query q = query("safe.example", 1);
        invalid(q, response(q, records(rr("safe.example", 5, wire("safe.example"))), records(), records()), "self loop");
        invalid(q, response(q, records(rr("safe.example", 5, wire("a.example")),
                rr("a.example", 5, wire("safe.example"))), records(), records()), "two-node loop");
        invalid(q, response(q, records(rr("safe.example", 5, wire("a.example")),
                rr("safe.example", 5, wire("b.example"))), records(), records()), "conflicting singleton CNAME");
        invalid(q, response(q, records(rr("safe.example", 5, new byte[]{4, 'a', 'b'})), records(), records()), "truncated RDATA name");
        invalid(q, response(q, records(rr("safe.example", 5, concat(wire("valid.example"), new byte[]{0}))), records(), records()),
                "CNAME trailing garbage");
        int dataStart = q.questionEnd + 12;
        invalid(q, response(q, records(rr(pointer(12), 5, 1, pointer(dataStart))), records(), records()), "self-referencing compression pointer");
        invalid(q, response(q, records(rr(pointer(12), 5, 1, pointer(dataStart + 1))), records(), records()), "forward compression pointer");
        invalid(q, response(q, records(rr(pointer(12), 5, 1, pointer(0))), records(), records()), "pointer into DNS header");
        invalid(q, response(q, records(rr(pointer(12), 5, 1, new byte[]{(byte)0xc0})), records(), records()), "half pointer at RDATA boundary");
        List<byte[]> longChain = new ArrayList<>();
        for (int i = 0; i < 70; i++) longChain.add(rr(i == 0 ? "safe.example" : "a" + i + ".example", 5,
                wire("a" + (i + 1) + ".example")));
        invalid(q, response(q, longChain, records(), records()), "bounded chain resource budget");
    }

    private static void followsDnameSuffixesOnly() {
        DnsPacketCodec.Query q = query("shop.old.example", 1);
        targets(q, response(q, records(rr("old.example", 39, wire("blocked.example"))), records(), records()),
                "DNAME strict subdomain replacement", "shop.blocked.example");
        targets(q, response(q, records(rr("old.example", 39, wire("new.example")),
                rr("shop.new.example", 5, wire("blocked.example"))), records(), records()),
                "DNAME then CNAME", "shop.new.example", "blocked.example");
        DnsPacketCodec.Query owner = query("old.example", 1);
        targets(owner, response(owner, records(rr("old.example", 39, wire("blocked.example"))), records(), records()),
                "DNAME does not redirect its own owner");
        DnsPacketCodec.Query boundary = query("notold.example", 1);
        targets(boundary, response(boundary, records(rr("old.example", 39, wire("blocked.example"))), records(), records()),
                "DNAME matches whole labels only");
        targets(q, response(q, records(rr("old.example", 39, wire(""))), records(), records()),
                "DNAME root substitution removes suffix", "shop");
        targets(q, response(q, records(rr("old.example", 39, wire("new.example")),
                rr("shop.old.example", 5, wire("shop.new.example"))), records(), records()),
                "synthesized CNAME follows same target once", "shop.new.example");
        invalid(q, response(q, records(rr("old.example", 39, wire("old.example"))), records(), records()), "DNAME self loop");
        invalid(q, response(q, records(rr("old.example", 39, wire("prefix.old.example"))), records(), records()), "DNAME growing loop is bounded");
        invalid(q, response(q, records(rr("old.example", 39, pointer(12))), records(), records()), "compressed DNAME target rejected by RFC6672");
        String huge = repeat('a', 63) + "." + repeat('b', 63) + "." + repeat('c', 63) + "." + repeat('d', 61);
        invalid(q, response(q, records(rr("old.example", 39, wire(huge))), records(), records()), "DNAME expansion exceeds hostname limit");
    }

    private static void honorsServiceBindingSemantics() {
        DnsPacketCodec.Query q = query("safe.example", 65);
        targets(q, response(q, records(rr("safe.example", 65, binding(0, "blocked.example"))), records(), records()),
                "HTTPS AliasMode exposes endpoint", "blocked.example");
        targets(q, response(q, records(rr("safe.example", 65, binding(0, "alias.example")),
                rr("alias.example", 5, wire("cdn.example")), rr("cdn.example", 65, binding(1, "endpoint.example"))), records(), records()),
                "HTTPS alias CNAME service endpoint chain", "alias.example", "cdn.example", "endpoint.example");
        targets(q, response(q, records(rr("safe.example", 65, binding(1, "cdn.example")),
                rr("safe.example", 65, binding(2, "fallback.example"))), records(), records()),
                "all candidate service endpoints checked", "cdn.example", "fallback.example");
        targets(q, response(q, records(rr("safe.example", 65, binding(1, "ignored.example")),
                rr("safe.example", 65, binding(0, "alias.example"))), records(), records()),
                "AliasMode suppresses ServiceMode within same RRset", "alias.example");
        targets(q, response(q, records(rr("safe.example", 65, binding(0, ""))), records(), records()),
                "AliasMode root means unavailable, not query-root loop");
        targets(q, response(q, records(rr("safe.example", 65, binding(1, ""))), records(), records()),
                "ServiceMode root uses owner without alias-loop false positive", "safe.example");
        targets(q, response(q, records(rr("safe.example", 65, binding(1, "endpoint.example")),
                rr("endpoint.example", 65, binding(0, "unrelated-service.example"))), records(), records()),
                "service endpoint A/AAAA lookup does not chain to its service bindings", "endpoint.example");
        targets(q, response(q, records(rr("safe.example", 64, binding(0, "wrong-type.example"))), records(), records()),
                "SVCB cannot alias an HTTPS query");
        DnsPacketCodec.Query a = query("safe.example", 1);
        targets(a, response(a, records(rr("safe.example", 65, binding(0, "irrelevant.example"))), records(), records()),
                "HTTPS binding does not redirect A query");
        DnsPacketCodec.Query svcb = query("_foo.safe.example", 64);
        targets(svcb, response(svcb, records(rr("_foo.safe.example", 64, binding(0, "endpoint.example"))), records(), records()),
                "SVCB service-prefix name supported", "endpoint.example");
        byte[] echBytes = concat(binding(1, "cdn.example"), new byte[]{0, 5, 0, 4, 'b', 'e', 't', 's'});
        targets(q, response(q, records(rr("safe.example", 65, echBytes)), records(), records()),
                "parameter bytes never become keyword/domain targets", "cdn.example");
        invalid(q, response(q, records(rr("safe.example", 65, binding(0, "safe.example"))), records(), records()), "HTTPS alias loop");
    }

    private static void malformedBindingsAreInvalid() {
        DnsPacketCodec.Query q = query("safe.example", 65);
        invalid(q, response(q, records(rr("safe.example", 65, new byte[]{0, 1})), records(), records()), "SVCB target missing");
        invalid(q, response(q, records(rr("safe.example", 65, concat(new byte[]{0, 0}, pointer(12)))), records(), records()),
                "compressed HTTPS target rejected by RFC9460");
        invalid(q, response(q, records(rr("safe.example", 65, concat(binding(1, "cdn.example"), new byte[]{0, 1, 0}))), records(), records()),
                "truncated parameter header");
        invalid(q, response(q, records(rr("safe.example", 65, concat(binding(1, "cdn.example"), new byte[]{0, 1, 0, 9, 1}))), records(), records()),
                "truncated parameter value");
        invalid(q, response(q, records(rr("safe.example", 65, concat(binding(1, "cdn.example"), new byte[]{0, 3, 0, 0, 0, 2, 0, 0}))), records(), records()),
                "unordered SVCB parameter keys");
        invalid(q, response(q, records(rr("safe.example", 65, concat(binding(1, "cdn.example"), new byte[]{0, 3, 0, 0, 0, 3, 0, 0}))), records(), records()),
                "duplicate SVCB parameter keys");
    }

    private static void rejectsUnmatchedAndTruncatedReplies() {
        DnsPacketCodec.Query q = query("safe.example", 1);
        byte[] reply = response(q, records(rr("safe.example", 5, wire("cdn.example"))), records(), records());
        byte[] wrong = reply.clone(); wrong[0] ^= 1;
        invalid(q, wrong, "upstream transaction mismatch");
        byte[] truncated = reply.clone(); truncated[2] |= 2;
        invalid(q, truncated, "truncated answer must be retried before complete inspection");
        invalid(q, Arrays.copyOf(reply, reply.length - 1), "truncated message");
        invalid(q, null, "null response");
        check(!DnsAliasInspector.inspect(null, reply).valid, "null query is invalid without exception");
        targets(q, DnsPacketCodec.error(q, 3), "NXDOMAIN without aliases is valid");
    }

    private static void returnedDataIsImmutable() {
        DnsPacketCodec.Query q = query("safe.example", 1);
        DnsAliasInspector.Result result = DnsAliasInspector.inspect(q,
                response(q, records(rr("safe.example", 5, wire("cdn.example"))), records(), records()));
        boolean refused = false;
        try { result.targets.add("injected.example"); } catch (UnsupportedOperationException expected) { refused = true; }
        check(refused, "caller cannot mutate immutable alias result");
    }

    private static void fuzzNeverThrows() {
        DnsPacketCodec.Query q = query("safe.example", 1);
        Random random = new Random(94606672L);
        for (int i = 0; i < 5000; i++) {
            byte[] data = new byte[random.nextInt(256)]; random.nextBytes(data);
            DnsAliasInspector.Result result = DnsAliasInspector.inspect(q,
                    response(q, records(rr("safe.example", 5, data)), records(), records()));
            check(result.valid || result.targets.isEmpty(), "malformed alias has no partial policy result");
        }
        byte[] good = response(q, records(rr("safe.example", 5, wire("cdn.example"))), records(), records());
        for (int i = 0; i < 5000; i++) {
            byte[] data = Arrays.copyOf(good, random.nextInt(good.length + 32));
            for (int j = 0; j < 5 && data.length > 0; j++) data[random.nextInt(data.length)] = (byte)random.nextInt(256);
            DnsAliasInspector.Result result = DnsAliasInspector.inspect(q, data);
            check(result.valid || result.targets.isEmpty(), "mutated reply has no partial policy result");
        }
    }

    private static void targets(DnsPacketCodec.Query q, byte[] data, String note, String... expected) {
        DnsAliasInspector.Result result = DnsAliasInspector.inspect(q, data);
        check(result.valid, note + " valid");
        check(result.targets.equals(Arrays.asList(expected)), note + " targets: " + result.targets);
    }
    private static void invalid(DnsPacketCodec.Query q, byte[] data, String note) {
        DnsAliasInspector.Result result = DnsAliasInspector.inspect(q, data);
        check(!result.valid && result.targets.isEmpty(), note);
    }
    private static void check(boolean value, String note) {
        assertions++;
        if (!value) throw new AssertionError(note);
    }
    private static DnsPacketCodec.Query query(String host, int type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, new byte[]{0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0});
        write(out, wire(host)); put16(out, type); put16(out, 1);
        DnsPacketCodec.Query q = DnsPacketCodec.parseQuery(out.toByteArray());
        if (q == null) throw new AssertionError("test query invalid");
        return q;
    }
    private static byte[] response(DnsPacketCodec.Query q, List<byte[]> answers, List<byte[]> authority, List<byte[]> additional) {
        byte[] header = DnsPacketCodec.error(q, 0);
        header[6] = (byte)(answers.size() >>> 8); header[7] = (byte)answers.size();
        header[8] = (byte)(authority.size() >>> 8); header[9] = (byte)authority.size();
        header[10] = (byte)(additional.size() >>> 8); header[11] = (byte)additional.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); write(out, header);
        for (byte[] rr : answers) write(out, rr);
        for (byte[] rr : authority) write(out, rr);
        for (byte[] rr : additional) write(out, rr);
        return out.toByteArray();
    }
    private static List<byte[]> records(byte[]... values) { return Arrays.asList(values); }
    private static byte[] rr(String owner, int type, byte[] data) { return rr(wire(owner), type, 1, data); }
    private static byte[] rr(byte[] owner, int type, int dnsClass, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); write(out, owner);
        put16(out, type); put16(out, dnsClass); put16(out, 0); put16(out, 60);
        put16(out, data.length); write(out, data); return out.toByteArray();
    }
    private static byte[] binding(int priority, String target) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); put16(out, priority); write(out, wire(target)); return out.toByteArray();
    }
    private static byte[] wire(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!text.isEmpty()) for (String label : text.split("\\.")) {
            byte[] data = label.getBytes(StandardCharsets.US_ASCII); out.write(data.length); write(out, data);
        }
        out.write(0); return out.toByteArray();
    }
    private static byte[] pointer(int at) { return new byte[]{(byte)(0xc0 | (at >>> 8)), (byte)at}; }
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, out, a.length, b.length); return out;
    }
    private static String repeat(char ch, int count) { char[] out = new char[count]; Arrays.fill(out, ch); return new String(out); }
    private static void put16(ByteArrayOutputStream out, int value) { out.write(value >>> 8); out.write(value); }
    private static void write(ByteArrayOutputStream out, byte[] data) { out.write(data, 0, data.length); }
}
