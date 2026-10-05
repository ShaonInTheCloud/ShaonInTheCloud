package com.safenest.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings

/** Separate app ID and preference file; never creates or caches a paid entitlement. */
object LocalTestSession {
    val enabled: Boolean = LocalTestRules.enabled(BuildConfig.DEBUG, BuildConfig.LOCAL_TEST_BUILD)
    private fun prefs(c: Context) = c.getSharedPreferences("safenest_local_test", Context.MODE_PRIVATE)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun isActive(c: Context): Boolean = enabled && LocalTestRules.active(
        prefs(c).getLong("elapsed_end", 0), SystemClock.elapsedRealtime(),
        prefs(c).getInt("boot", -1), boot(c))
    fun endsAt(c: Context): Long = if (enabled) prefs(c).getLong("display_end", 0) else 0
    fun hasSession(c: Context): Boolean = enabled && prefs(c).getLong("elapsed_end", 0) > 0

    @Synchronized fun begin(c: Context): Boolean {
        if (!enabled || boot(c) < 0) return false
        if (isActive(c)) return true
        return prefs(c).edit().clear()
            .putInt("boot", boot(c))
            .putLong("elapsed_end", SystemClock.elapsedRealtime() + LocalTestRules.DURATION_MS)
            .putLong("display_end", System.currentTimeMillis() + LocalTestRules.DURATION_MS).commit()
    }
    @Synchronized fun stop(c: Context) {
        if (!enabled) return
        prefs(c).edit().clear().commit()
        GuardPreferences.clearForExpiry(c)
        c.stopService(Intent(c, SafeNestVpnService::class.java))
    }
}
