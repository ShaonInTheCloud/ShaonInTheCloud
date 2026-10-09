package com.safenest.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Receives device-owner policy lifecycle events. */
class SafeNestAdminReceiver : DeviceAdminReceiver() {
    /**
     * Delivered after QR/NFC enrollment (direct build). Opens SafeNest so the customer can
     * sign in; no restriction is applied until they choose Strong lock during a paid period.
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
