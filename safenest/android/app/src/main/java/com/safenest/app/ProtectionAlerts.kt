package com.safenest.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.app.Notification

/**
 * Warns the user, loudly and quickly, when protection stops during a paid period:
 * another VPN (1.1.1.1/WARP, Proton, ...) replaced SafeNest, Android revoked it, or the
 * service died. Tapping the alert opens SafeNest and asks to turn protection back on.
 *
 * A consumer app cannot stop Android from switching VPNs; Strong lock (direct build,
 * managed phone) is what prevents it. This is the best response available everywhere else.
 */
object ProtectionAlerts {
    const val ACTION_RESTORE = "com.safenest.app.RESTORE_PROTECTION"
    private const val CHANNEL = "safenest_alerts"
    private const val NOTIFICATION_ID = 4107
    private const val WATCH_JOB_ID = 4108
    private const val PREFS = "safenest_alerts"
    private const val WATCH_INTERVAL_MS = 15 * 60 * 1000L

    data class Interruption(val at: Long, val otherVpn: Boolean)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun language(c: Context) = c.getSharedPreferences("safenest_app", Context.MODE_PRIVATE).getString("language", "en") ?: "en"
    private fun holdsConsent(c: Context) = try { VpnService.prepare(c) == null } catch (_: Exception) { false }

    fun currentState(c: Context): ProtectionAlertRules.State = ProtectionAlertRules.state(
        ProtectionCommitment.isActive(c), SafeNestVpnService.isRunning.get(), holdsConsent(c))

    /** Called when the VPN service is revoked or dies unexpectedly, and by the watch job. */
    fun check(c: Context) {
        val app = c.applicationContext
        val state = currentState(app)
        if (state == ProtectionAlertRules.State.NOT_COMMITTED) { clear(app); cancelWatch(app); return }
        if (state == ProtectionAlertRules.State.PROTECTED) { clear(app); return }
        val p = prefs(app)
        val now = System.currentTimeMillis()
        val otherVpn = ProtectionAlertRules.likelyOtherVpn(holdsConsent(app))
        if (p.getLong("interrupted_at", 0L) == 0L) p.edit().putLong("interrupted_at", now).putBoolean("other_vpn", otherVpn).apply()
        if (!ProtectionAlertRules.shouldNotify(state, p.getLong("notified_at", 0L), now)) return
        if (post(app, otherVpn)) p.edit().putLong("notified_at", now).apply()
    }

    /** Protection is running again: remove the alert and keep watching for the rest of the period. */
    fun resumed(c: Context) {
        val app = c.applicationContext
        clear(app)
        scheduleWatch(app)
    }

    fun lastInterruption(c: Context): Interruption? {
        val p = prefs(c)
        val at = p.getLong("interrupted_at", 0L)
        return if (at > 0) Interruption(at, p.getBoolean("other_vpn", false)) else null
    }

    fun clear(c: Context) {
        prefs(c).edit().remove("interrupted_at").remove("other_vpn").remove("notified_at").apply()
        c.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Installed VPN apps SafeNest recognises, by their visible names, for the alert text. */
    private fun knownVpnNames(c: Context): List<String> = KnownVpnPackages.installed(c).mapNotNull { pkg ->
        try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() }
        catch (_: PackageManager.NameNotFoundException) { null }
    }.sorted().take(3)

    private fun post(c: Context, otherVpn: Boolean): Boolean {
        val manager = c.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Protection alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tells you immediately if SafeNest protection is turned off during your period."
        })
        val bn = language(c) == "bn"
        val title = when {
            otherVpn && bn -> "অন্য একটি VPN SafeNest বন্ধ করেছে"
            otherVpn -> "Another VPN turned SafeNest off"
            bn -> "SafeNest সুরক্ষা বন্ধ আছে"
            else -> "SafeNest protection is off"
        }
        val names = if (otherVpn) knownVpnNames(c) else emptyList()
        val detected = if (names.isEmpty()) "" else if (bn) " শনাক্ত: ${names.joinToString()}।" else " Detected: ${names.joinToString()}."
        val body = (if (bn) "এখন জুয়ার সাইট ব্লক হচ্ছে না। আবার চালু করতে ট্যাপ করুন।" else "Gambling sites aren't blocked right now. Tap to turn protection back on.") + detected
        val open = PendingIntent.getActivity(c, 1,
            Intent(c, MainActivity::class.java).setAction(ACTION_RESTORE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try { manager.notify(NOTIFICATION_ID, notification); true } catch (_: SecurityException) { false }
    }

    fun scheduleWatch(c: Context) {
        val scheduler = c.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(WATCH_JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(WATCH_JOB_ID, ComponentName(c, ProtectionWatchJob::class.java))
            .setPeriodic(WATCH_INTERVAL_MS).setPersisted(true).build())
    }

    fun cancelWatch(c: Context) { c.getSystemService(JobScheduler::class.java)?.cancel(WATCH_JOB_ID) }
}

/** Periodic check during a paid period (survives reboot): alerts if protection is not running. */
class ProtectionWatchJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        try { ProtectionAlerts.check(this) } catch (_: Exception) { }
        return false
    }
    override fun onStopJob(params: JobParameters) = false
}
