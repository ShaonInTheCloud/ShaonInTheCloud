package com.safenest.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Canonical, signed domain snapshots. No Android, JSON parser or network dependency. */
public final class CatalogVerifier {
    private CatalogVerifier() {}
    public static final int MAX_PAYLOAD_BYTES = 6 * 1024 * 1024;
    public static final int MAX_ENVELOPE_BYTES = 8 * 1024 * 1024 + 1024;
    public static final int MAX_DOMAINS = 100_000;
    public static final long MAX_VALIDITY_SECONDS = 366L * 24 * 60 * 60;
    public static final String ENVELOPE_HEADER = "SAFENEST-SIGNED-CATALOG/1\n";
    public static final String PAYLOAD_HEADER = "SAFENEST-CATALOG/1";

    public static final class Snapshot {
        public final long revision;
        public final Instant issuedAt;
        public final Instant expiresAt;
        public final Set<String> gambling;
        public final Set<String> adult;
        public final String keyFingerprint;
        private Snapshot(long revision, Instant issuedAt, Instant expiresAt,
                         Set<String> gambling, Set<String> adult, String keyFingerprint) {
            this.revision = revision;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.gambling = Collections.unmodifiableSet(gambling);
            this.adult = Collections.unmodifiableSet(adult);
            this.keyFingerprint = keyFingerprint;
        }
        public boolean isStale(Instant now) {
            return !now.isBefore(expiresAt) || now.isBefore(issuedAt.minusSeconds(300));
        }
        public int domainCount() { return gambling.size() + adult.size(); }
    }

    /** Verify the configured key first; payload cannot choose its own key or signature algorithm. */
    public static PublicKey parsePublicKey(String pem) throws GeneralSecurityException {
        if (pem == null || pem.length() > 4096) throw new GeneralSecurityException("Public key is missing or too large");
        String text = pem.trim();
        String begin = "-----BEGIN PUBLIC KEY-----", end = "-----END PUBLIC KEY-----";
        if (!text.startsWith(begin) || !text.endsWith(end))
            throw new GeneralSecurityException("Use a P-256 SPKI public key PEM, never a private key");
        String body = text.substring(begin.length(), text.length() - end.length());
        if (!body.matches("[A-Za-z0-9+/=\\r\\n\\t ]+")) throw new GeneralSecurityException("Malformed public key PEM");
        byte[] encoded;
        try { encoded = Base64.getDecoder().decode(body.replaceAll("[\\r\\n\\t ]", "")); }
        catch (IllegalArgumentException invalid) { throw new GeneralSecurityException("Malformed public key PEM", invalid); }
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
        if (!(key instanceof ECPublicKey)) throw new GeneralSecurityException("P-256 public key required");
        ECParameterSpec actual = ((ECPublicKey) key).getParams();
        AlgorithmParameters algorithm = AlgorithmParameters.getInstance("EC");
        algorithm.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec required = algorithm.getParameterSpec(ECParameterSpec.class);
        if (!actual.getCurve().equals(required.getCurve()) || !actual.getGenerator().equals(required.getGenerator())
                || !actual.getOrder().equals(required.getOrder()) || actual.getCofactor() != required.getCofactor())
            throw new GeneralSecurityException("Only the P-256 curve is supported");
        return key;
    }

    public static String fingerprint(PublicKey key) throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        StringBuilder out = new StringBuilder();
        for (byte b : digest) { out.append(Character.forDigit((b >>> 4) & 15, 16)); out.append(Character.forDigit(b & 15, 16)); }
        return out.toString();
    }

    public static String publicKeyPem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    public static Snapshot verifyUpdate(byte[] envelope, String pinnedKeyPem, long previousRevision, Instant now)
            throws GeneralSecurityException {
        Snapshot snapshot = verifyStored(envelope, pinnedKeyPem);
        if (snapshot.revision <= previousRevision) throw new GeneralSecurityException("Catalog revision must increase; replay or rollback rejected");
        if (snapshot.isStale(now)) throw new GeneralSecurityException("Catalog expired or issued in the future; previous verified rules retained");
        return snapshot;
    }

    /** For reloading a previously accepted local snapshot only. Expired good rules remain active and are flagged stale. */
    public static Snapshot verifyStored(byte[] envelope, String previouslyPinnedKeyPem) throws GeneralSecurityException {
        if (envelope == null || envelope.length > MAX_ENVELOPE_BYTES)
            throw new GeneralSecurityException("Signed catalog exceeds the size limit");
        String document = strictAscii(envelope);
        if (!document.startsWith(ENVELOPE_HEADER)) throw new GeneralSecurityException("Unsupported signed catalog format");
        String[] rows = document.split("\n", -1);
        if (rows.length != 4 || !rows[1].startsWith("payload:") || !rows[2].startsWith("signature:") || !rows[3].isEmpty())
            throw new GeneralSecurityException("Malformed signed catalog envelope");
        byte[] payload = canonicalBase64(rows[1].substring(8), MAX_PAYLOAD_BYTES);
        byte[] signature = canonicalBase64(rows[2].substring(10), 80);
        PublicKey key = parsePublicKey(previouslyPinnedKeyPem);
        Signature checker = Signature.getInstance("SHA256withECDSA");
        checker.initVerify(key);
        checker.update(payload);
        if (!checker.verify(signature)) throw new GeneralSecurityException("Catalog signature does not match the configured public key");
        // No rule parsing occurs before signature authentication succeeds.
        return parsePayload(payload, fingerprint(key));
    }

    private static byte[] canonicalBase64(String text, int max) throws GeneralSecurityException {
        if (text.length() > ((max + 2L) / 3) * 4) throw new GeneralSecurityException("Encoded field exceeds its size limit");
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            if (bytes.length > max || !Base64.getEncoder().encodeToString(bytes).equals(text))
                throw new GeneralSecurityException("Noncanonical Base64 field");
            return bytes;
        } catch (IllegalArgumentException invalid) { throw new GeneralSecurityException("Malformed Base64 field", invalid); }
    }

    private static String strictAscii(byte[] bytes) throws GeneralSecurityException {
        for (byte b : bytes) if (b != '\n' && (b < 32 || b > 126))
            throw new GeneralSecurityException("Catalog must use canonical ASCII with LF newlines");
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static Snapshot parsePayload(byte[] payload, String fingerprint) throws GeneralSecurityException {
        String[] lines = strictAscii(payload).split("\n", -1);
        if (lines.length < 6 || lines.length > MAX_DOMAINS + 5 || !lines[0].equals(PAYLOAD_HEADER)
                || !lines[1].startsWith("revision:") || !lines[2].startsWith("issued-at:")
                || !lines[3].startsWith("expires-at:") || !lines[lines.length - 1].isEmpty())
            throw new GeneralSecurityException("Malformed catalog metadata or record limit exceeded");
        long revision;
        String revisionText = lines[1].substring(9);
        try {
            revision = Long.parseLong(revisionText);
            if (revision <= 0 || !Long.toString(revision).equals(revisionText)) throw new NumberFormatException();
        } catch (NumberFormatException invalid) { throw new GeneralSecurityException("Positive canonical revision required", invalid); }
        Instant issuedAt = parseTime(lines[2].substring(10)), expiresAt = parseTime(lines[3].substring(11));
        long duration = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
        if (duration <= 0 || duration > MAX_VALIDITY_SECONDS)
            throw new GeneralSecurityException("Catalog validity must be between one second and 366 days");
        Set<String> gambling = new LinkedHashSet<>(), adult = new LinkedHashSet<>();
        String previous = "";
        for (int i = 4; i < lines.length - 1; i++) {
            String row = lines[i];
            if (row.compareTo(previous) <= 0) throw new GeneralSecurityException("Catalog records must be unique and lexically sorted");
            previous = row;
            Set<String> target;
            String host;
            if (row.startsWith("adult:")) { target = adult; host = row.substring(6); }
            else if (row.startsWith("gambling:")) { target = gambling; host = row.substring(9); }
            else throw new GeneralSecurityException("Unsupported catalog category");
            if (!host.equals(DomainRules.normalizeHostname(host)))
                throw new GeneralSecurityException("Catalog contains a noncanonical or invalid hostname");
            target.add(host);
        }
        if (gambling.isEmpty() && adult.isEmpty()) throw new GeneralSecurityException("Empty catalog snapshots are not accepted");
        return new Snapshot(revision, issuedAt, expiresAt, gambling, adult, fingerprint);
    }

    private static Instant parseTime(String value) throws GeneralSecurityException {
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")) throw new DateTimeParseException("UTC seconds required", value, 0);
            Instant instant = Instant.parse(value);
            if (!instant.toString().equals(value)) throw new DateTimeParseException("Canonical UTC time required", value, 0);
            return instant;
        } catch (DateTimeParseException invalid) { throw new GeneralSecurityException("Invalid UTC timestamp", invalid); }
    }

    /** Caller owns the stream. Reads one extra byte at the limit, rejecting oversized/chunked downloads. */
    public static byte[] readBounded(InputStream stream, int max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(max, 8192));
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer, 0, Math.min(buffer.length, max - output.size() + 1))) != -1) {
            if (count == 0) continue;
            if (count > max - output.size()) throw new IOException("Catalog input exceeds the size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
