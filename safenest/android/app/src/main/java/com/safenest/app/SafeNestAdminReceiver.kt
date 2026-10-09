package com.safenest.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Device Administrator receiver. While a paid protection period is active, Android
 * shows [onDisableRequested]'s text before it lets the user deactivate the admin,
 * which is the step that precedes uninstall. This is friction, not a lock: Android
 * still lets the user confirm. Device Owner policies are handled in ManagedProtection.
 */
class SafeNestAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (!ProtectionCommitment.isActive(context)) return "SafeNest protection is not active. Deactivating only removes the extra uninstall step."
        val ends = Instant.ofEpochMilli(ProtectionCommitment.endsAt(context)).atZone(ZoneId.systemDefault())
        val until = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").format(ends)
        return "You chose SafeNest protection until $until. Deactivating the administrator weakens that commitment before it ends. " +
            "If this is a craving, close this screen and wait: it passes. Protection ends by itself at the date above."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // The guard keeps running on the VPN and Accessibility services; nothing to tear down here.
        super.onDisabled(context, intent)
    }

    /**
     * Delivered after QR/NFC device-owner enrollment (direct build). Opens SafeNest so the
     * customer can sign in; no restriction is applied until they choose Strong lock during a paid period.
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
