package com.safenest.app

import com.safenest.app.ProtectionAlertRules.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionAlertRulesTest {
    @Test fun stateNeedsCommitmentServiceAndConsent() {
        assertEquals(State.NOT_COMMITTED, ProtectionAlertRules.state(false, false, false))
        assertEquals(State.NOT_COMMITTED, ProtectionAlertRules.state(false, true, true))
        assertEquals(State.PROTECTED, ProtectionAlertRules.state(true, true, true))
        // WARP took the VPN slot: our service may briefly still report running.
        assertEquals(State.INTERRUPTED, ProtectionAlertRules.state(true, true, false))
        assertEquals(State.INTERRUPTED, ProtectionAlertRules.state(true, false, true))
        assertEquals(State.INTERRUPTED, ProtectionAlertRules.state(true, false, false))
    }

    @Test fun notifiesFirstThenRateLimits() {
        val gap = ProtectionAlertRules.MIN_REPEAT_MS
        assertTrue(ProtectionAlertRules.shouldNotify(State.INTERRUPTED, 0L, 1_000L))
        assertFalse(ProtectionAlertRules.shouldNotify(State.INTERRUPTED, 1_000L, 1_000L + gap - 1))
        assertTrue(ProtectionAlertRules.shouldNotify(State.INTERRUPTED, 1_000L, 1_000L + gap))
        assertTrue("clock moved back", ProtectionAlertRules.shouldNotify(State.INTERRUPTED, 5_000L, 1_000L))
    }

    @Test fun neverNotifiesWhenProtectedOrNotCommitted() {
        assertFalse(ProtectionAlertRules.shouldNotify(State.PROTECTED, 0L, 1_000L))
        assertFalse(ProtectionAlertRules.shouldNotify(State.NOT_COMMITTED, 0L, 1_000L))
    }

    @Test fun otherVpnWhenConsentIsLost() {
        assertTrue(ProtectionAlertRules.likelyOtherVpn(false))
        assertFalse(ProtectionAlertRules.likelyOtherVpn(true))
    }
}
