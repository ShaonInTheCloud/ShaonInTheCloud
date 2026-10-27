package com.safenest.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * Restarts the local filter after a reboot or app update while a paid period is active, so protection does not
 * depend on the user having switched on Android's Always-on VPN. Starts only if VPN permission is still granted.
 */
class ProtectionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ProtectionCommitment.checkpoint(context)
        if (!ProtectionCommitment.isActive(context) || VpnService.prepare(context) != null) return
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, SafeNestVpnService::class.java).setAction(SafeNestVpnService.ACTION_START))
        } catch (_: Exception) {} // the service's own checks and the revoke alert remain the fallback
    }
}
