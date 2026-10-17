package com.safenest.app;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Memory-compact, read-only set of normalized hostnames for large bundled blocklists.
 *
 * Each hostname is stored as a 64-bit fingerprint in a sorted array, so half a million
 * rules take about 4 MB instead of a String HashSet of roughly 100 MB. Lookups are
 * a binary search per host label. With 64-bit fingerprints the chance that any lookup
 * collides with a listed name is negligible (about n/2^64 per query).
 *
 * File format ("SNBL" v1, big-endian):
 *   int magic 0x534E424C, int version 1, int count,
 *   32-byte SHA-256 of the fingerprint payload, then count longs in ascending order.
 * The payload digest is verified on read, so a truncated or altered asset fails loudly.
 */
public final class CompactDomainSet {
    public static final int MAGIC = 0x534E424C;
    public static final int VERSION = 1;
    private static final int HEADER_BYTES = 4 + 4 + 4 + 32;
    private static final int MAX_COUNT = 5_000_000;

    private final long[] sorted;

    private CompactDomainSet(long[] sortedUniqueFingerprints) { this.sorted = sortedUniqueFingerprints; }

    public static CompactDomainSet empty() { return new CompactDomainSet(new long[0]); }

    /**
     * Stable 64-bit fingerprint of an already-normalized hostname: FNV-1a over the ASCII
     * bytes followed by the SplitMix64 finalizer for good bit dispersion. Never change this
     * without bumping VERSION and regenerating every asset.
     */
    public static long fingerprint(String normalizedHost) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < normalizedHost.length(); i++) {
            h ^= (normalizedHost.charAt(i) & 0xff);
            h *= 0x100000001b3L;
        }
        h ^= (h >>> 30); h *= 0xbf58476d1ce4e5b9L;
        h ^= (h >>> 27); h *= 0x94d049bb133111ebL;
        h ^= (h >>> 31);
        return h;
    }

    /** Builds from raw names; entries DomainRules rejects are skipped. */
    public static CompactDomainSet fromHostnames(Iterable<String> hostnames) {
        long[] buffer = new long[1024];
        int n = 0;
        for (String raw : hostnames) {
            String host = DomainRules.normalizeHostname(raw == null ? null : raw.trim());
            if (host == null) continue;
            if (n == buffer.length) buffer = Arrays.copyOf(buffer, n * 2);
            buffer[n++] = fingerprint(host);
        }
        return new CompactDomainSet(sortUnique(Arrays.copyOf(buffer, n)));
    }

    private static long[] sortUnique(long[] values) {
        Arrays.sort(values);
        int w = 0;
        for (int r = 0; r < values.length; r++) if (w == 0 || values[r] != values[w - 1]) values[w++] = values[r];
        return Arrays.copyOf(values, w);
    }

    /** Fingerprints present here and absent from other (for layering a second asset without overlap). */
    public CompactDomainSet minus(CompactDomainSet other) {
        long[] out = new long[sorted.length];
        int w = 0;
        for (long v : sorted) if (Arrays.binarySearch(other.sorted, v) < 0) out[w++] = v;
        return new CompactDomainSet(Arrays.copyOf(out, w));
    }

    public int size() { return sorted.length; }

    public boolean containsNormalized(String normalizedHost) {
        return normalizedHost != null && Arrays.binarySearch(sorted, fingerprint(normalizedHost)) >= 0;
    }

    /** Exact name or any parent domain listed, matching DomainRules.isBlocked semantics. */
    public boolean blocksNormalized(String normalizedHost) {
        if (normalizedHost == null) return false;
        String host = normalizedHost;
        while (host.indexOf('.') >= 0) {
            if (Arrays.binarySearch(sorted, fingerprint(host)) >= 0) return true;
            host = host.substring(host.indexOf('.') + 1);
        }
        return false;
    }

    public boolean blocksHost(String hostname) { return blocksNormalized(DomainRules.normalizeHostname(hostname)); }

    public void write(OutputStream output) throws IOException {
        byte[] payload = payload(sorted);
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(sorted.length);
        out.write(sha256(payload));
        out.write(payload);
        out.flush();
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + sorted.length * 8);
        try { write(bytes); } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }

    public static CompactDomainSet read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != MAGIC) throw new IOException("Not a SafeNest compact blocklist");
        int version = in.readInt();
        if (version != VERSION) throw new IOException("Unsupported compact blocklist version " + version);
        int count = in.readInt();
        if (count < 0 || count > MAX_COUNT) throw new IOException("Invalid compact blocklist size " + count);
        byte[] expected = new byte[32];
        in.readFully(expected);
        byte[] payload = new byte[count * 8];
        in.readFully(payload);
        if (in.read() != -1) throw new IOException("Trailing data after compact blocklist payload");
        if (!MessageDigest.isEqual(expected, sha256(payload))) throw new IOException("Compact blocklist checksum mismatch");
        long[] values = new long[count];
        ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).asLongBuffer().get(values);
        for (int i = 1; i < values.length; i++) {
            if (values[i] <= values[i - 1]) throw new IOException("Compact blocklist is not strictly sorted");
        }
        return new CompactDomainSet(values);
    }

    private static byte[] payload(long[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 8).order(ByteOrder.BIG_ENDIAN);
        buffer.asLongBuffer().put(values);
        return buffer.array();
    }

    private static byte[] sha256(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException(missing); }
    }

    /** Hex SHA-256 of the serialized asset, for build manifests. */
    public static String hexSha256(byte[] data) {
        byte[] digest = sha256(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
