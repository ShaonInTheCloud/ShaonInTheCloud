package com.safenest.app;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

/** Real JVM cryptography and adversarial snapshot tests; no Android test doubles. */
public final class CatalogVerifierRegression {
    private CatalogVerifierRegression() {}
    private static int checks;
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");
    private static final String HEADER = "SAFENEST-CATALOG/1\nrevision:7\nissued-at:2026-09-28T09:00:00Z\nexpires-at:2026-10-28T09:00:00Z\n";
    private static final String ROWS = "adult:adult.example\ngambling:blocked.example\ngambling:www.specific.example\ngambling:xn--bcher-kva.example\n";

    public static void main(String[] args) throws Exception { runAll(); }

    public static void runAll() throws Exception {
        checks = 0;
        KeyPair key = keyPair("secp256r1"), wrong = keyPair("secp256r1");
        String pem = CatalogVerifier.publicKeyPem(key.getPublic());
        String wrongPem = CatalogVerifier.publicKeyPem(wrong.getPublic());
        byte[] valid = sign(HEADER + ROWS, key);
        CatalogVerifier.Snapshot snapshot = CatalogVerifier.verifyUpdate(valid, pem, 6, NOW);
        check(snapshot.revision == 7 && snapshot.domainCount() == 4, "valid signed catalog");
        check(snapshot.gambling.contains("www.specific.example"), "preserve www scope");
        check(snapshot.gambling.contains("xn--bcher-kva.example"), "canonical IDN");
        check(DomainRules.isBlocked("sub.blocked.example", snapshot.gambling), "catalog protects subdomains");
        check(!DomainRules.isBlocked("notblocked.example", snapshot.gambling), "catalog suffix boundaries");
        check(snapshot.keyFingerprint.equals(CatalogVerifier.fingerprint(key.getPublic())), "pin fingerprint");
        check(!snapshot.isStale(NOW), "current catalog");
        check(snapshot.isStale(Instant.parse("2026-10-28T09:00:00Z")), "expires exactly at boundary");
        try { snapshot.gambling.add("tamper.example"); throw new AssertionError("mutable rules"); }
        catch (UnsupportedOperationException expected) { checks++; }

        rejected(() -> CatalogVerifier.verifyUpdate(valid, wrongPem, 0, NOW), "wrong trust pin");
        rejected(() -> CatalogVerifier.verifyUpdate(valid, pem, 7, NOW), "equal revision replay");
        rejected(() -> CatalogVerifier.verifyUpdate(valid, pem, 8, NOW), "older revision rollback");
        rejected(() -> CatalogVerifier.verifyUpdate(valid, pem, 6, Instant.parse("2026-10-28T09:00:00Z")), "expired new update");
        rejected(() -> CatalogVerifier.verifyUpdate(valid, pem, 6, Instant.parse("2026-09-28T08:54:59Z")), "future issued time");
        check(CatalogVerifier.verifyUpdate(valid, pem, 6, Instant.parse("2026-09-28T08:55:00Z")).revision == 7, "five minute clock allowance");
        check(CatalogVerifier.verifyStored(valid, pem).isStale(Instant.parse("2027-01-01T00:00:00Z")), "expired known-good reload");
        rejected(() -> CatalogVerifier.parsePublicKey(CatalogVerifier.publicKeyPem(keyPair("secp384r1").getPublic())), "different EC curve");
        rejected(() -> CatalogVerifier.parsePublicKey("-----BEGIN PRIVATE KEY-----\nAA==\n-----END PRIVATE KEY-----"), "private key input");
        rejected(() -> CatalogVerifier.parsePublicKey(pem + "injected"), "key trailing data");

        // Retain original signature but replace authenticated contents.
        String[] envelopeRows = new String(valid, StandardCharsets.US_ASCII).split("\n");
        byte[] tampered = (envelopeRows[0] + "\npayload:" + Base64.getEncoder().encodeToString((HEADER + ROWS.replace("blocked.example", "allowed.example")).getBytes(StandardCharsets.US_ASCII))
                + "\n" + envelopeRows[2] + "\n").getBytes(StandardCharsets.US_ASCII);
        rejected(() -> CatalogVerifier.verifyUpdate(tampered, pem, 0, NOW), "tampered payload");
        rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER + ROWS, wrong), pem, 0, NOW), "attacker signed catalog");
        rejected(() -> CatalogVerifier.verifyUpdate(HEADER.getBytes(StandardCharsets.US_ASCII), pem, 0, NOW), "unsigned payload");
        rejected(() -> CatalogVerifier.verifyUpdate(new String(valid, StandardCharsets.US_ASCII).replace("/1\n", "/2\n").getBytes(StandardCharsets.US_ASCII), pem, 0, NOW), "unknown envelope version");
        rejected(() -> CatalogVerifier.verifyUpdate((new String(valid, StandardCharsets.US_ASCII) + "key:" + wrongPem).getBytes(StandardCharsets.US_ASCII), pem, 0, NOW), "payload cannot supply key");
        rejected(() -> CatalogVerifier.verifyUpdate(new String(valid, StandardCharsets.US_ASCII).replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII), pem, 0, NOW), "noncanonical envelope newlines");

        for (String host : new String[]{"BLOCKED.example", "https://blocked.example", "blocked.example.", "*.example", "co", "127.0.0.1", "evil@example.com", "evil.example:443", "bad-.example", "-bad.example", "bad..example", "bücher.example", "example.123"}) {
            rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER + "gambling:" + host + "\n", key), pem, 0, NOW), "invalid canonical domain " + host);
        }
        for (String rows : new String[]{"", "personal:blocked.example\n", "gambling:blocked.example\nadult:adult.example\n", "adult:adult.example\nadult:adult.example\n", "adult:adult.example\n\n"}) {
            rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER + rows, key), pem, 0, NOW), "category, duplicates, order or empty records");
        }
        for (String revision : new String[]{"0", "-1", "+7", "07", "9223372036854775808", "7.0"}) {
            rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER.replace("revision:7", "revision:" + revision) + ROWS, key), pem, 0, NOW), "revision encoding");
        }
        for (String time : new String[]{"2026-09-28T09:00:00+00:00", "2026-09-28T09:00:00.000Z", "2026-13-28T09:00:00Z", "2026-09-28T09:00:60Z"}) {
            rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER.replace("2026-09-28T09:00:00Z", time) + ROWS, key), pem, 0, NOW), "noncanonical UTC timestamp");
        }
        rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER.replace("2026-10-28T09:00:00Z", "2026-09-28T09:00:00Z") + ROWS, key), pem, 0, NOW), "zero validity");
        rejected(() -> CatalogVerifier.verifyUpdate(sign(HEADER.replace("2026-10-28T09:00:00Z", "2028-09-28T09:00:00Z") + ROWS, key), pem, 0, NOW), "excessive validity");
        rejected(() -> CatalogVerifier.verifyUpdate(new byte[CatalogVerifier.MAX_ENVELOPE_BYTES + 1], pem, 0, NOW), "envelope size bound");
        check(CatalogVerifier.readBounded(new ByteArrayInputStream(new byte[25]), 25).length == 25, "exact input limit");
        try { CatalogVerifier.readBounded(new ByteArrayInputStream(new byte[26]), 25); throw new AssertionError("unbounded input"); }
        catch (IOException expected) { checks++; }
        byte[] rotated = sign((HEADER + ROWS).replace("revision:7", "revision:8"), wrong);
        check(CatalogVerifier.verifyUpdate(rotated, wrongPem, 7, NOW).revision == 8, "explicit new pin with increasing revision");
        System.out.println("CatalogVerifier regression: " + checks + " checks passed (real P-256 signatures)");
    }

    private interface Check { void run() throws Exception; }
    private static void rejected(Check action, String message) throws Exception {
        try { action.run(); throw new AssertionError("Accepted " + message); }
        catch (GeneralSecurityException expected) { checks++; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static KeyPair keyPair(String curve) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }
    private static byte[] sign(String text, KeyPair key) throws GeneralSecurityException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key.getPrivate()); signer.update(payload);
        String document = CatalogVerifier.ENVELOPE_HEADER + "payload:" + Base64.getEncoder().encodeToString(payload)
                + "\nsignature:" + Base64.getEncoder().encodeToString(signer.sign()) + "\n";
        return document.getBytes(StandardCharsets.US_ASCII);
    }
}
