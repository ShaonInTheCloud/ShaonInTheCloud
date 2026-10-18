package com.safenest.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Gambling apps and VPN browsers SafeNest blocks during protection. The lists are plain text assets in
 * assets/blocked_apps so the team can update them like the domain list; matching rules live in AppBlockRules.
 * Decisions are cached per package ID (an app's ID and name do not change while it is installed).
 */
object BlockedAppCatalog {
    private const val DIR = "blocked_apps"
    private data class Lists(val packages: Set<String>, val brands: Set<String>, val vpnBrowsers: Set<String>, val vpnBrowserNames: Set<String>)

    @Volatile private var lists: Lists? = null
    private val decisions = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 512
    }

    private fun lines(context: Context, file: String): List<String> = try {
        context.assets.open("$DIR/$file").bufferedReader().use { it.readLines() }
    } catch (_: Exception) { emptyList() }

    private fun lists(context: Context): Lists {
        lists?.let { return it }
        return synchronized(this) {
            lists ?: run {
                val browserRows = lines(context, "vpn_browsers.txt").map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
                Lists(
                    packages = AppBlockRules.parseList(lines(context, "gambling_packages.txt"), false),
                    brands = AppBlockRules.parseList(lines(context, "gambling_app_brands.txt"), true),
                    vpnBrowsers = browserRows.map { it.substringBefore('|').trim().lowercase() }.filter { it.contains('.') }.toSet(),
                    vpnBrowserNames = browserRows.mapNotNull { it.substringAfter('|', "").trim().lowercase().takeIf(String::isNotEmpty) }.toSet(),
                ).also { lists = it }
            }
        }
    }

    /** A website whose name is a known gambling brand plus a short tail (babu88a.com, jeetbuzz-bd.live). */
    fun isMirrorSite(context: Context, host: String): Boolean = MirrorRules.isMirror(host, lists(context).brands)

    private fun label(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().takeIf { it.length <= 80 }
    } catch (_: PackageManager.NameNotFoundException) { null } catch (_: Exception) { null }

    /** "" when the app is fine; otherwise an AppBlockRules reason. Never matches SafeNest or essential apps. */
    fun reason(context: Context, pkg: String): String {
        if (pkg == context.packageName || !GuardRules.isPackageName(pkg)) return ""
        synchronized(decisions) { decisions[pkg]?.let { return it } }
        val l = lists(context)
        val decided = AppBlockRules.reason(pkg, label(context, pkg), l.packages, l.brands, l.vpnBrowsers) { domain ->
            BundledGamblingRules.contains(context, domain) || RulesStore.isCatalogRule(context, RuleCategory.GAMBLING, domain)
        }.let { if (it.isNotEmpty() && GuardPreferences.isEssentialPackage(context, pkg)) "" else it }
        synchronized(decisions) { decisions[pkg] = decided }
        return decided
    }

    fun isGamblingApp(context: Context, pkg: String): Boolean = reason(context, pkg).let { it.isNotEmpty() && it != AppBlockRules.VPN_BROWSER }
    fun isVpnBrowser(context: Context, pkg: String): Boolean = reason(context, pkg) == AppBlockRules.VPN_BROWSER

    fun blocksInstall(context: Context, titles: Collection<String>, actions: Collection<String>): Boolean {
        val l = lists(context)
        return AppBlockRules.blocksInstall(titles, actions, l.brands, l.vpnBrowserNames)
    }

    /** Installed launcher apps that match, for the app's blocked-apps screen. */
    fun installedMatches(context: Context): Set<String> = try {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.distinct().filter { reason(context, it).isNotEmpty() }.toSet()
    } catch (_: Exception) { emptySet() }
}
