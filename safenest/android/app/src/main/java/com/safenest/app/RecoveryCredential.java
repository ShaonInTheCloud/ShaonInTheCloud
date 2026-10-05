package com.safenest.app;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Portable recovery-code hashing. Records contain parameters, a random salt and a hash only. */
public final class RecoveryCredential {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    public static final int ITERATIONS = 600_000;
    public static final int MAX_COOLDOWN_SECONDS = 30;
    private static final int BYTES = 32;
    private static final String PREFIX = "pbkdf2-sha256$1$600000$";

    private RecoveryCredential() { }

    public static boolean validCodeLength(String code) {
        if (code == null || code.length() > MAX_LENGTH * 2) return false;
        int count = code.codePointCount(0, code.length());
        if (count < MIN_LENGTH || count > MAX_LENGTH) return false;
        boolean nonWhitespace = false;
        for (int i = 0; i < code.length();) {
            int point = code.codePointAt(i);
            if (Character.isISOControl(point)) return false;
            if (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE) return false;
            nonWhitespace |= !Character.isWhitespace(point);
            i += Character.charCount(point);
        }
        return nonWhitespace;
    }

    public static String create(String code) throws GeneralSecurityException {
        if (!validCodeLength(code)) throw new IllegalArgumentException("Use 12–128 characters, including a non-space character, without control characters.");
        byte[] salt = new byte[BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(code, salt);
        try {
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                + "$" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } finally { Arrays.fill(hash, (byte) 0); Arrays.fill(salt, (byte) 0); }
    }

    /** Invalid stored records throw; callers must not treat corruption as an unset credential. */
    public static boolean verify(String record, String candidate) throws GeneralSecurityException {
        byte[][] parts = decode(record);
        byte[] actual = null;
        try {
            if (!validCodeLength(candidate)) return false;
            actual = derive(candidate, parts[0]);
            return MessageDigest.isEqual(parts[1], actual);
        } finally {
            Arrays.fill(parts[0], (byte) 0);
            Arrays.fill(parts[1], (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    public static boolean isWellFormed(String record) {
        try {
            byte[][] parts = decode(record);
            Arrays.fill(parts[0], (byte) 0);
            Arrays.fill(parts[1], (byte) 0);
            return true;
        } catch (IllegalArgumentException error) { return false; }
    }

    private static byte[][] decode(String record) {
        if (record == null || record.length() > 160 || !record.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid recovery credential record.");
        }
        String[] parts = record.substring(PREFIX.length()).split("\\$", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_-]{43}") || !parts[1].matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invalid recovery credential fields.");
        }
        byte[] salt = Base64.getUrlDecoder().decode(parts[0]);
        byte[] hash = Base64.getUrlDecoder().decode(parts[1]);
        if (salt.length != BYTES || hash.length != BYTES
            || !Base64.getUrlEncoder().withoutPadding().encodeToString(salt).equals(parts[0])
            || !Base64.getUrlEncoder().withoutPadding().encodeToString(hash).equals(parts[1])) {
            throw new IllegalArgumentException("Invalid recovery credential encoding.");
        }
        return new byte[][] { salt, hash };
    }

    private static byte[] derive(String code, byte[] salt) throws GeneralSecurityException {
        char[] chars = code.toCharArray();
        PBEKeySpec key = new PBEKeySpec(chars, salt, ITERATIONS, BYTES * 8);
        Arrays.fill(chars, '\0');
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded(); }
        finally { key.clearPassword(); }
    }

    /** First two failures have no delay; repeated failures are bounded at 30 seconds. */
    public static int cooldownSeconds(int failures) {
        if (failures <= 2) return 0;
        if (failures >= 8) return MAX_COOLDOWN_SECONDS;
        return 1 << (failures - 3);
    }

    /** Clock rollback/corrupt timestamps cannot produce an unbounded lockout. */
    public static long remainingCooldownMillis(long until, long now) {
        if (until <= now) return 0;
        try { return Math.min(MAX_COOLDOWN_SECONDS * 1000L, Math.subtractExact(until, now)); }
        catch (ArithmeticException error) { return MAX_COOLDOWN_SECONDS * 1000L; }
    }
}
