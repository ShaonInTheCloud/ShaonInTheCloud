package com.safenest.app

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean

data class PaidWindow(val id: String, val userId: String, val plan: String,
                      val starts: Long, val ends: Long, val serverNow: Long)

/** Private cached HTTPS verification, followed by a separately consented, finite commitment.
 * Rooted devices/private-data edits are outside this trust model. No password or JWT is saved.
 */
object ProtectionCommitment {
    private const val PREFS = "safenest_paid_commitment"
    private const val JOB_ID = 2403
    private val cleanup = AtomicBoolean(false)
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)

    @Synchronized fun cacheVerified(c: Context, window: PaidWindow) {
        check(!LocalTestSession.enabled) { "The test app uses local test sessions, not paid access." }
        require(CommitmentRules.validWindow(window.starts, window.ends, window.serverNow)) { "No active paid period." }
        // An active commitment never changes account or extends itself without fresh consent.
        if (isActive(c)) return
        check(prefs(c).edit().clear().putString("id", window.id).putString("user", window.userId)
            .putString("plan", window.plan).putLong("starts", window.starts).putLong("ends", window.ends)
            .putLong("server_anchor", window.serverNow).putLong("elapsed_anchor", SystemClock.elapsedRealtime())
            .putLong("high_water", window.serverNow).putInt("boot", boot(c)).putBoolean("committed", false).commit())
    }
    private fun now(c: Context): Long {
        val p = prefs(c)
        val sameBoot = p.getInt("boot", -2) == boot(c)
        // A reset clock across a reboot cannot establish an indefinitely extended lock.
        // Require a new online verification rather than trapping a device with an uncertain clock.
        if (!sameBoot && System.currentTimeMillis() + 300_000 < p.getLong("high_water", 0)) return endsAt(c)
        return CommitmentRules.effectiveNow(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            p.getLong("server_anchor", 0), p.getLong("elapsed_anchor", 0), p.getLong("high_water", 0),
            sameBoot)
    }
    fun isActive(c: Context) = if (LocalTestSession.enabled) LocalTestSession.isActive(c)
        else CommitmentRules.active(prefs(c).getBoolean("committed", false), endsAt(c), now(c))
    fun endsAt(c: Context) = if (LocalTestSession.enabled) LocalTestSession.endsAt(c) else prefs(c).getLong("ends", 0)
    fun entitlementId(c: Context) = prefs(c).getString("id", null)
    /** Support can revoke only on the server. A successful recheck must match both account and period.
     * Wrong credentials, another account and network failures never release a commitment.
     */
    @Synchronized fun reconcile(c: Context, result: AccessCheck): Boolean {
        check(!LocalTestSession.enabled) { "Local test sessions have no server entitlement." }
        val p = prefs(c)
        check(p.getBoolean("committed", false) && result.checkedId == p.getString("id", null) &&
            result.userId == p.getString("user", null)) { "Sign in with the account used to start this protection period." }
        if (result.window != null) return false // Renewals never silently extend the consented term.
        check(result.serverNow > 0)
        p.edit().putLong("ends", minOf(endsAt(c), result.serverNow))
            .putLong("server_anchor", result.serverNow).putLong("elapsed_anchor", SystemClock.elapsedRealtime())
            .putLong("high_water", result.serverNow).putInt("boot", boot(c)).commit().also { check(it) }
        releaseExpired(c)
        return true
    }
    fun hasVerifiedAccess(c: Context): Boolean {
        if (LocalTestSession.enabled) return true // Local QA access only; no server entitlement is issued.
        if (isActive(c)) return true
        val p = prefs(c)
        return p.getString("id", null) != null && p.getInt("boot", -2) == boot(c) &&
            SystemClock.elapsedRealtime() - p.getLong("elapsed_anchor", -1) in 0..300_000L && now(c) < endsAt(c)
    }
    @Synchronized fun begin(c: Context): Boolean {
        if (LocalTestSession.enabled) return LocalTestSession.begin(c)
        if (isActive(c)) return true
        if (!hasVerifiedAccess(c) || !GuardPreferences.isAccessibilityEnabled(c) || !GuardPreferences.isSelected(c)) return false
        check(prefs(c).edit().putBoolean("committed", true).commit())
        GuardPreferences.commitVpnBlocking(c)
        schedule(c)
        return true
    }
    /** Re-anchor periodically; clock rollback cannot extend a commitment within this boot. */
    @Synchronized fun checkpoint(c: Context) {
        if (LocalTestSession.enabled) {
            if (LocalTestSession.hasSession(c) && !LocalTestSession.isActive(c)) expireAsync(c)
            return
        }
        val p = prefs(c)
        if (!p.getBoolean("committed", false)) return
        val current = now(c)
        val edit = p.edit().putLong("high_water", current)
        if (p.getInt("boot", -2) != boot(c)) {
            edit.putLong("server_anchor", current).putLong("elapsed_anchor", SystemClock.elapsedRealtime())
                .putInt("boot", boot(c))
        }
        edit.apply()
        if (current >= endsAt(c)) expireAsync(c)
    }
    fun expireAsync(c: Context) {
        val app = c.applicationContext
        if (isActive(app) || !cleanup.compareAndSet(false, true)) return
        Thread({
            try { releaseExpired(app) } finally { cleanup.set(false) }
        }, "SafeNest-term-expiry").start()
    }
    @Synchronized internal fun releaseExpired(c: Context) {
        if (isActive(c)) return
        if (LocalTestSession.enabled) { LocalTestSession.stop(c); return }
        // Also removes old, unbilled prototype policies during upgrade to paid mode.
        GuardPreferences.clearForExpiry(c)
        val remaining = if (ManagedProtection.isConfigured(c)) ManagedProtection.release(c).managed else false
        c.stopService(Intent(c, SafeNestVpnService::class.java))
        if (!ManagedProtection.isDeviceOwner(c) && GuardPreferences.isAdminActive(c)) {
            c.getSystemService(DevicePolicyManager::class.java)?.removeActiveAdmin(
                ComponentName(c, SafeNestAdminReceiver::class.java))
        }
        if (!remaining) prefs(c).edit().putBoolean("committed", false).commit()
        else schedule(c, 60_000)
    }
    fun schedule(c: Context, retry: Long? = null) {
        if (LocalTestSession.enabled) return
        val delay = retry ?: (endsAt(c) - now(c)).coerceAtLeast(1000)
        val scheduler = c.getSystemService(JobScheduler::class.java) ?: return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(c, CommitmentExpiryJob::class.java))
            .setMinimumLatency(delay).setOverrideDeadline(delay + 60_000).setPersisted(true).build())
    }
}

class CommitmentExpiryJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            var retry = false
            try {
                if (ProtectionCommitment.isActive(this)) ProtectionCommitment.schedule(this)
                else ProtectionCommitment.releaseExpired(this)
            } catch (_: Exception) { retry = true }
            finally { jobFinished(params, retry) }
        }.start()
        return true
    }
    override fun onStopJob(params: JobParameters) = true
}
