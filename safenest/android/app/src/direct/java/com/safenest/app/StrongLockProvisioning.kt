package com.safenest.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Strong lock enrollment (direct build only).
 *
 * A customer factory-resets the phone, taps the welcome screen six times and scans the
 * SafeNest provisioning QR (tools/provisioning/make_qr.py). Android downloads the direct
 * APK, verifies its signing certificate, and makes SafeNest the device owner.
 *
 * Enrollment itself applies NO restrictions. The customer then signs in, starts a verified
 * paid period, and chooses Strong lock in Setup with a recovery passphrase; ManagedProtection
 * refuses to apply without an active period and releases everything when it ends.
 */
object StrongLockProvisioning {
    private const val PREFS = "safenest_strong_lock"
    fun markEnrolled(context: Context, via: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("enrolled_via", via).putLong("enrolled_at", System.currentTimeMillis()).apply()
    }
    fun enrolledVia(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("enrolled_via", null)
}

/** Answers Android 10+ setup: SafeNest supports only full device management. */
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { setResult(RESULT_CANCELED); finish(); return }
        val fullyManaged = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val allowed = intent.getIntegerArrayListExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES)
            if (allowed != null && fullyManaged !in allowed) { setResult(RESULT_CANCELED); finish(); return }
        }
        setResult(RESULT_OK, Intent().putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, fullyManaged))
        finish()
    }
}

/** Final setup step. Records enrollment only; Strong lock is chosen later inside the app. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StrongLockProvisioning.markEnrolled(this, "qr")
        setResult(RESULT_OK)
        finish()
    }
}
