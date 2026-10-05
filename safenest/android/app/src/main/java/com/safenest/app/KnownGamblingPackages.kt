package com.safenest.app

import android.content.Context
import android.content.pm.PackageManager

/** Exact IDs with manifest/listing evidence. See data/known-gambling-packages.json. */
object KnownGamblingPackages {
    private val packages = setOf(
        "com.krikya.krikya", "com.application.yongbao.bj",
        "org.xbet.client1", "org.bet22.client", "org.linebet.client",
        "org.melbet.client", "com.ads.mostbet", "org.megapari.client",
        "com.luckygoal.neonpulse", "com.playcubely.fall"
    )

    fun contains(name: String): Boolean = name in packages

    fun installed(context: Context): Set<String> = packages.filterTo(mutableSetOf()) { name ->
        try { context.packageManager.getApplicationInfo(name, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    }
}
