package com.safenest.app

import android.content.Context

data class ManagedRecoveryResult(val success: Boolean, val message: String, val retryAfterSeconds: Int = 0)

/** Device-local recovery authentication. Call set/verify on Dispatchers.IO, never log the code. */
object ManagedRecovery {
    private const val PREFS = "safenest_managed_recovery"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // A missing/corrupt value with a stored marker is not a legacy unconfigured installation.
    fun configured(context: Context): Boolean = prefs(context).let { it.contains("configured") || it.contains("credential") }

    @Synchronized fun set(context: Context, code: String): ManagedRecoveryResult {
        if (configured(context)) return ManagedRecoveryResult(false, "A recovery code is already configured. It cannot be replaced here.")
        if (!RecoveryCredential.validCodeLength(code)) return ManagedRecoveryResult(false, "Use 12–128 characters, including a non-space character, without control characters.")
        return try {
            val record = RecoveryCredential.create(code)
            val saved = prefs(context).edit().putBoolean("configured", true).putString("credential", record)
                .putInt("failures", 0).putLong("retry_at", 0L).commit()
            if (saved) ManagedRecoveryResult(true, "Recovery code saved on this device.")
            else ManagedRecoveryResult(false, "Could not save the recovery code. Do not enable managed rules yet.")
        } catch (_: Exception) {
            ManagedRecoveryResult(false, "Could not create the recovery credential. Managed rules have not been authorized.")
        }
    }

    @Synchronized fun verify(context: Context, code: String): ManagedRecoveryResult {
        if (!configured(context)) return ManagedRecoveryResult(false, "No recovery code is configured on this device.")
        val state = prefs(context)
        return try {
            val record = state.getString("credential", null)
            if (!RecoveryCredential.isWellFormed(record)) return damaged()
            val now = System.currentTimeMillis()
            val until = state.getLong("retry_at", 0L)
            val wait = RecoveryCredential.remainingCooldownMillis(until, now)
            if (wait > 0L) {
                // Clamp a restored or rolled-back clock once, then let the bounded interval expire.
                val clampedUntil = if (now > Long.MAX_VALUE - wait) Long.MAX_VALUE else now + wait
                if (until > clampedUntil && !state.edit().putLong("retry_at", clampedUntil).commit()) {
                    return ManagedRecoveryResult(false, "Could not update recovery attempt limits. Retry after reopening SafeNest.")
                }
                val seconds = ((wait + 999L) / 1000L).toInt()
                return ManagedRecoveryResult(false, "Please wait $seconds seconds before trying the recovery code again.", seconds)
            }
            if (RecoveryCredential.verify(record, code)) {
                if (!state.edit().putInt("failures", 0).putLong("retry_at", 0L).commit()) {
                    return ManagedRecoveryResult(false, "Could not save recovery authentication. Try again.")
                }
                ManagedRecoveryResult(true, "Recovery code verified.")
            } else {
                val failures = (state.getInt("failures", 0).coerceIn(0, 31) + 1).coerceAtMost(32)
                val delay = RecoveryCredential.cooldownSeconds(failures)
                if (!state.edit().putInt("failures", failures).putLong("retry_at", now + delay * 1000L).commit()) {
                    return ManagedRecoveryResult(false, "Could not save recovery attempt limits. Retry after reopening SafeNest.")
                }
                ManagedRecoveryResult(false, "The recovery code did not match.", delay)
            }
        } catch (_: IllegalArgumentException) { damaged() }
        catch (_: ClassCastException) { damaged() }
        catch (_: Exception) { ManagedRecoveryResult(false, "Recovery verification is unavailable. Use the documented external recovery process if necessary.") }
    }

    /** Call only after successful authorized policy release (or documented external recovery). */
    @Synchronized fun clear(context: Context): Boolean = prefs(context).edit().clear().commit()

    private fun damaged() = ManagedRecoveryResult(false,
        "The saved recovery credential is damaged. Managed rules remain protected. Use the documented external ADB recovery process.")
}
