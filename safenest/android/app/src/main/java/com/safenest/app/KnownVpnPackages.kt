package com.safenest.app

import android.content.Context
import android.content.pm.PackageManager

/** Exact package identifiers checked against the publishers' Google Play listings.
 * This is an additional signal. VPN-service discovery finds other visible VPN apps.
 * A package label or substring alone is never sufficient to block an app.
 */
object KnownVpnPackages {
    val ids = setOf(
        "com.nordvpn.android",
        "com.surfshark.vpnclient.android",
        "com.expressvpn.vpn",
        "ch.protonvpn.android",
        "com.psiphon3",
        "com.psiphon3.subscription",
        "free.vpn.unblock.proxy.turbovpn",
        "com.fast.free.unblock.thunder.vpn",
        "com.jrzheng.supervpnfree",
        "com.windscribe.vpn",
        "com.tunnelbear.android",
        "com.cloudflare.onedotonedotonedotone",
        "hotspotshield.android.vpn",
        "com.privateinternetaccess.android",
        "de.mobileconcepts.cyberghost"
    )

    fun installed(context: Context): Set<String> = ids.filterTo(mutableSetOf()) { name ->
        try { context.packageManager.getApplicationInfo(name, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    }
}
