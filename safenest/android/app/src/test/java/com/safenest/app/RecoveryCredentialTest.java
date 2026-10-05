package com.safenest.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryCredentialTest {
    // Independent vector generated with Python hashlib.pbkdf2_hmac, not this implementation.
    private static final String VECTOR = "pbkdf2-sha256$1$600000$AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8$qDwkAESnzVXvtzxkw-wKXG2EHDB4yOEl08r9GRiNQr0";

    @Test public void matchesIndependentPbkdf2Vector() throws Exception {
        assertTrue(RecoveryCredential.verify(VECTOR, "Correct horse battery!"));
        assertFalse(RecoveryCredential.verify(VECTOR, "Correct horse battery?"));
        assertFalse(RecoveryCredential.verify(VECTOR, "Correct horse battery! "));
    }

    @Test public void saltsAreUniqueAndRecordsContainNoPlaintext() throws Exception {
        String code = "An unrelated recovery phrase!";
        String first = RecoveryCredential.create(code);
        String second = RecoveryCredential.create(code);
        assertNotEquals(first, second);
        assertFalse(first.contains(code));
        assertTrue(RecoveryCredential.verify(first, code));
        assertTrue(RecoveryCredential.isWellFormed(second));
    }

    @Test public void rejectsCorruptOrAttackerChosenParameters() throws Exception {
        for (String invalid : new String[] { null, "", "x", VECTOR.replace("600000", "1"),
                VECTOR.replace("600000", "2147483647"), VECTOR + "$extra", VECTOR.substring(0, VECTOR.length() - 1),
                VECTOR.replace("Hh8$", "Hh9$"), VECTOR.replace("sha256", "sha1") }) {
            assertFalse(RecoveryCredential.isWellFormed(invalid));
            try { RecoveryCredential.verify(invalid, "Correct horse battery!"); fail("Corruption must not verify"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void boundsInputsAndCountsCharactersWithoutTrimming() {
        assertFalse(RecoveryCredential.validCodeLength(null));
        assertFalse(RecoveryCredential.validCodeLength("short"));
        assertFalse(RecoveryCredential.validCodeLength("            "));
        assertFalse(RecoveryCredential.validCodeLength("12345678901\n"));
        assertFalse(RecoveryCredential.validCodeLength("12345678901\ud800"));
        assertTrue(RecoveryCredential.validCodeLength("123456789012"));
        assertTrue(RecoveryCredential.validCodeLength(repeat("a", 128)));
        assertFalse(RecoveryCredential.validCodeLength(repeat("a", 129)));
        assertTrue(RecoveryCredential.validCodeLength(repeat("\ud83d\udd10", 12)));
        assertFalse(RecoveryCredential.validCodeLength(repeat("\ud83d\udd10", 11)));
    }

    @Test public void rateLimitCapsAndDoesNotPermanentlyLockAfterClockRollback() {
        assertEquals(0, RecoveryCredential.cooldownSeconds(2));
        assertEquals(1, RecoveryCredential.cooldownSeconds(3));
        assertEquals(16, RecoveryCredential.cooldownSeconds(7));
        assertEquals(30, RecoveryCredential.cooldownSeconds(8));
        assertEquals(30, RecoveryCredential.cooldownSeconds(Integer.MAX_VALUE));
        assertEquals(0, RecoveryCredential.remainingCooldownMillis(100L, 101L));
        assertEquals(2500L, RecoveryCredential.remainingCooldownMillis(3500L, 1000L));
        assertEquals(30000L, RecoveryCredential.remainingCooldownMillis(Long.MAX_VALUE, 0L));
        assertEquals(30000L, RecoveryCredential.remainingCooldownMillis(Long.MAX_VALUE, Long.MIN_VALUE));
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
