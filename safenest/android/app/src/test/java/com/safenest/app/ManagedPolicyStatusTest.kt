package com.safenest.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A saved setup flag must never substitute for actual Android policy readbacks. */
class ManagedPolicyStatusTest {
    private val confirmed = ManagedPolicyStatus(
        isDeviceOwner = true,
        adminActive = true,
        policyConfigured = true,
        alwaysOnSafeNest = true,
        vpnConfigRestricted = true,
        uninstallBlocked = true,
        lockdownEnabled = false,
        error = null
    )

    @Test fun allRequiredReadbacksWithLockdownOffAreVerified() {
        assertTrue(confirmed.verified)
    }

    @Test fun missingDeviceOwnerIsNotVerified() {
        assertFalse(confirmed.copy(isDeviceOwner = false).verified)
    }

    @Test fun inactiveAdminIsNotVerified() {
        assertFalse(confirmed.copy(adminActive = false).verified)
    }

    @Test fun releasedSetupIsNotVerifiedEvenIfOldPoliciesRemain() {
        assertFalse(confirmed.copy(policyConfigured = false).verified)
    }

    @Test fun missingAlwaysOnPolicyIsNotVerified() {
        assertFalse(confirmed.copy(alwaysOnSafeNest = false).verified)
    }

    @Test fun editableVpnSettingsAreNotVerified() {
        assertFalse(confirmed.copy(vpnConfigRestricted = false).verified)
    }

    @Test fun missingUninstallRestrictionIsNotVerified() {
        assertFalse(confirmed.copy(uninstallBlocked = false).verified)
    }

    @Test fun lockdownOnCannotVerifyADnsOnlyConfiguration() {
        assertFalse(confirmed.copy(lockdownEnabled = true).verified)
    }

    @Test fun unknownLockdownCannotVerifyADnsOnlyConfiguration() {
        assertFalse(confirmed.copy(lockdownEnabled = null).verified)
    }

    @Test fun failedReadbackIsNotVerifiedEvenWithPositiveFlags() {
        assertFalse(confirmed.copy(error = "Android policy read failed").verified)
    }

    @Test fun uninitializedStatusIsNotVerified() {
        assertFalse(ManagedPolicyStatus().verified)
    }
}
