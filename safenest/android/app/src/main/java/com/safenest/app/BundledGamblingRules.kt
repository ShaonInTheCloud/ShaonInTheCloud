package com.safenest.app

import android.content.Context
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Bundled gambling hostnames, compiled by tools/blocklist/build.sh into compact
 * fingerprint assets (see CompactDomainSet). Exact hostnames and their subdomains only.
 *
 * - gambling_core.snbl: SafeNest's own Bangladesh research plus MIT / Unlicense feeds (required).
 * - gambling_hagezi_gpl3.snbl: HaGeZi-derived GPL-3.0 data, licence in third_party_hagezi_gpl3.txt.
 *   Optional: if it is missing or damaged the core list still protects.
 *
 * Loaded once on a background thread at app start (SafeNestApplication) so the DNS reader
 * never pays the first-load cost in normal operation.
 */
object BundledGamblingRules {
    const val CORE_ASSET = "gambling_core.snbl"
    const val GPL_ASSET = "gambling_hagezi_gpl3.snbl"
    const val PRIORITY_ASSET = "gambling_bangladesh_priority.txt"
    private const val TAG = "SafeNestBlocklist"

    @Volatile private var sets: List<CompactDomainSet>? = null
    @Volatile private var coreFailure: IllegalStateException? = null
    @Volatile private var priority: Set<String>? = null

    /**
     * A damaged or missing core asset throws, and the failure is remembered so lookups do not
     * re-read megabytes per query; callers fail closed (the DNS filter refuses lookups).
     */
    fun load(context: Context): List<CompactDomainSet> {
        sets?.let { return it }
        coreFailure?.let { throw it }
        return synchronized(this) {
            sets ?: run {
                coreFailure?.let { throw it }
                val core = try {
                    context.assets.open(CORE_ASSET).buffered(1 shl 16).use { CompactDomainSet.read(it) }
                } catch (error: IOException) {
                    throw IllegalStateException("Bundled blocklist $CORE_ASSET could not be read", error).also { coreFailure = it }
                }
                val gpl = try {
                    context.assets.open(GPL_ASSET).buffered(1 shl 16).use { CompactDomainSet.read(it) }
                } catch (_: FileNotFoundException) { null }
                catch (error: Throwable) {
                    Log.w(TAG, "Optional $GPL_ASSET skipped: ${error.javaClass.simpleName}")
                    null
                }
                listOfNotNull(core, gpl).also { sets = it }
            }
        }
    }

    fun count(context: Context): Int = load(context).sumOf { it.size() }

    /** Exact listed hostname (already normalized). */
    fun contains(context: Context, normalizedHost: String): Boolean = load(context).any { it.containsNormalized(normalizedHost) }

    /** The already-normalized host or any parent domain is listed. */
    fun isBlockedNormalized(context: Context, normalizedHost: String): Boolean =
        load(context).any { it.blocksNormalized(normalizedHost) }

    fun isBlocked(context: Context, hostname: String): Boolean =
        DomainRules.normalizeHostname(hostname)?.let { isBlockedNormalized(context, it) } ?: false

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
