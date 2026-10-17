package com.safenest.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalRulesTest {
    @Test fun onlyAfterThePeriodWithEverythingReleased() {
        assertTrue(RemovalRules.canRemoveManagement(true, false, false))
        assertFalse("paid period still running", RemovalRules.canRemoveManagement(true, true, false))
        assertFalse("restrictions not yet released", RemovalRules.canRemoveManagement(true, false, true))
        assertFalse("not a managed phone", RemovalRules.canRemoveManagement(false, false, false))
    }
}
