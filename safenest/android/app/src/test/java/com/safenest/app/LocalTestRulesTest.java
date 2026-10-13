package com.safenest.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalTestRulesTest {
    @Test public void onlyTestDebugBuildsGetLocalAccess() {
        assertTrue(LocalTestRules.enabled(true, true));
        assertFalse(LocalTestRules.enabled(false, true));
        assertFalse(LocalTestRules.enabled(true, false));
        assertFalse(LocalTestRules.enabled(false, false));
        // Only the two debug test builds skip the paid check; play and direct never do.
        boolean testFlavor = BuildConfig.FLAVOR.equals("lab") || BuildConfig.FLAVOR.equals("ownerlab");
        assertEquals(BuildConfig.DEBUG && testFlavor, LocalTestSession.INSTANCE.getEnabled());
    }
    @Test public void finiteSessionExpiresWithoutWallClock() {
        assertTrue(LocalTestRules.active(1000, 500, 4, 4));
        assertFalse(LocalTestRules.active(1000, 1000, 4, 4));
        assertFalse(LocalTestRules.active(1000, 1001, 4, 4));
        assertFalse(LocalTestRules.active(0, 500, 4, 4));
    }
    @Test public void rebootOrUncertainBootEndsTest() {
        assertFalse(LocalTestRules.active(1000, 500, 4, 5));
        assertFalse(LocalTestRules.active(1000, 500, -1, -1));
        assertFalse(LocalTestRules.active(1000, -1, 4, 4));
    }
    @Test public void sessionCannotExceedOneHour() {
        assertTrue(LocalTestRules.active(500 + LocalTestRules.DURATION_MS, 500, 4, 4));
        assertFalse(LocalTestRules.active(501 + LocalTestRules.DURATION_MS, 500, 4, 4));
    }
}
