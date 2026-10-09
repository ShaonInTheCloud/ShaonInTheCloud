package com.safenest.app

import android.content.Context
import java.io.FileNotFoundException

/**
 * Bundled gambling hostnames, compiled by tools/blocklist/build.sh into compact
 * fingerprint assets (see CompactDomainSet). Exact hostnames and their subdomains only.
 *
 * - gambling_core.snbl: SafeNest's own Bangladesh research plus MIT / Unlicense feeds (required).
 * - gambling_hagezi_gpl3.snbl: HaGeZi-derived GPL-3.0 data, licence in third_party_hagezi_gpl3.txt.
 *   Optional: a build that omits it still works with the core list.
 */
object BundledGamblingRules {
    const val CORE_ASSET = "gambling_core.snbl"
    const val GPL_ASSET = "gambling_hagezi_gpl3.snbl"
    const val PRIORITY_ASSET = "gambling_bangladesh_priority.txt"

    @Volatile private var sets: List<CompactDomainSet>? = null
    @Volatile private var priority: Set<String>? = null

    /** Loads once (a few MB, well under a second); a damaged core asset throws so the app never runs with it silently missing. */
    fun load(context: Context): List<CompactDomainSet> {
        sets?.let { return it }
        return synchronized(this) {
            sets ?: listOfNotNull(read(context, CORE_ASSET, required = true), read(context, GPL_ASSET, required = false))
                .also { sets = it }
        }
    }

    private fun read(context: Context, asset: String, required: Boolean): CompactDomainSet? = try {
        context.assets.open(asset).buffered(1 shl 16).use { CompactDomainSet.read(it) }
    } catch (missing: FileNotFoundException) {
        if (required) throw IllegalStateException("Bundled blocklist $asset is missing", missing) else null
    }

    fun count(context: Context): Int = load(context).sumOf { it.size() }

    /** Exact listed hostname (already normalized). */
    fun contains(context: Context, normalizedHost: String): Boolean = load(context).any { it.containsNormalized(normalizedHost) }

    /** The host or any parent domain is listed. */
    fun isBlocked(context: Context, hostname: String): Boolean {
        val host = DomainRules.normalizeHostname(hostname) ?: return false
        return load(context).any { it.blocksNormalized(host) }
    }

    /** Bangladesh-researched names, readable as text, used where a capped plain list is needed (Chrome policy). */
    fun priorityDomains(context: Context): Set<String> {
        priority?.let { return it }
        return synchronized(this) {
            priority ?: context.assets.open(PRIORITY_ASSET).bufferedReader().use { reader ->
                reader.lineSequence().filterNot { it.isBlank() || it.startsWith("#") }
                    .mapNotNull { DomainRules.normalizeHostname(it.trim()) }.toSet()
            }.also { priority = it }
        }
    }
}
