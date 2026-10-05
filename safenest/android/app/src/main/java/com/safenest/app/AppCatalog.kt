package com.safenest.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppChoice(val packageName: String, val label: String, val isVpn: Boolean)

/** Only visible launchable apps; no QUERY_ALL_PACKAGES or external inventory. */
object AppCatalog {
    fun load(context: Context): List<AppChoice> {
        val packages = context.packageManager
        val vpnPackages = ManagedProtection.findVpnPackages(context).toSet()
        return packages.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL
        ).mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (!GuardPreferences.canBlockPackage(context, packageName)) return@mapNotNull null
            AppChoice(packageName, info.loadLabel(packages).toString(), packageName in vpnPackages)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}
