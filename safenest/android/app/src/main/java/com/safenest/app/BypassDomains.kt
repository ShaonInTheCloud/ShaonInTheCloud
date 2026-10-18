package com.safenest.app

import android.content.Context

/** Web proxies, unblocker sites and Tor web gateways (assets/blocked_apps/bypass_domains.txt). */
object BypassDomains {
    @Volatile private var names: Set<String>? = null

    fun names(context: Context): Set<String> {
        names?.let { return it }
        return synchronized(this) {
            names ?: try {
                context.assets.open("blocked_apps/bypass_domains.txt").bufferedReader().useLines { lines ->
                    lines.map { it.substringBefore('#').trim() }.mapNotNull(DomainRules::normalizeHostname).toSet()
                }
            } catch (_: Exception) { emptySet<String>() }.also { names = it }
        }
    }

    /** The host or one of its parent domains is a listed proxy or gateway. */
    fun isBlocked(context: Context, host: String): Boolean = DomainRules.isBlocked(host, names(context))
}
