package com.safenest.app

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

enum class RuleCategory(val key: String, val label: String) { GAMBLING("gambling", "Gambling"), ADULT("adult", "Adult content"), PERSONAL("personal", "My list") }

object RulesStore {
    private const val PREFS = "safenest_rules"
    private const val GAMBLING = "domains_gambling"
    private const val ADULT = "domains_adult"
    private const val PERSONAL = "domains_personal"

    // Small starter examples only. This is not a complete or regularly updated catalog.
    private val initialGambling = setOf("bet365.com", "stake.com", "1xbet.com", "1xbet.fi", "betway.com", "betfair.com", "williamhill.com", "unibet.com", "bwin.com", "dafabet.com", "parimatch.com")
    private val initialAdult = setOf("pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com", "spankbang.com", "chaturbate.com", "livejasmin.com", "onlyfans.com", "adult-demo.example")

    private var activePreferences: SharedPreferences? = null
    private var activeContext: Context? = null
    @Volatile private var blockedCache: Set<String>? = null
    private val changeListeners = CopyOnWriteArraySet<(Context) -> Unit>()
    private val notifications = Executors.newSingleThreadExecutor { task -> Thread(task, "SafeNest-rule-changes").apply { isDaemon = true } }
    // Hold a strong reference: SharedPreferences retains listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val context = synchronized(this) { blockedCache = null; activeContext }
        context?.let { notifyChanges(it) }
    }

    @Synchronized private fun preferences(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (activePreferences !== prefs) {
            activePreferences?.unregisterOnSharedPreferenceChangeListener(listener)
            activePreferences = prefs
            activeContext = context.applicationContext
            prefs.registerOnSharedPreferenceChangeListener(listener)
            blockedCache = null
        }
        return prefs
    }

    @Synchronized private fun local(context: Context, category: RuleCategory): Set<String> {
        val saved = preferences(context).getStringSet(key(category), null)
        val source = saved ?: when (category) {
            RuleCategory.GAMBLING -> initialGambling
            RuleCategory.ADULT -> initialAdult
            RuleCategory.PERSONAL -> emptySet()
        }
        return source.mapNotNull { normalize(it) }.toSet()
    }

    /**
     * Editable lists plus authenticated catalog rules for a category. The large bundled
     * gambling list is held separately in compact form (BundledGamblingRules) and is
     * consulted by isBlocked/isCatalogRule; it is deliberately not expanded into this set.
     */
    @Synchronized fun get(context: Context, category: RuleCategory): Set<String> =
        local(context, category) + CatalogStore.domains(context, category)

    /** User additions and the small verified starter list take priority in Chrome's finite policy budget. */
    @Synchronized fun priorityDomains(context: Context): Set<String> =
        RuleCategory.entries.flatMap { local(context, it) }.toSet()

    fun isCatalogRule(context: Context, category: RuleCategory, domain: String): Boolean =
        DomainRules.normalizeHostname(domain)?.let {
            CatalogStore.domains(context, category).contains(it) ||
                (category == RuleCategory.GAMBLING && BundledGamblingRules.contains(context, it))
        } ?: false

    /** Callbacks run on a dedicated worker, never under RulesStore/CatalogStore locks. */
    fun addChangeListener(listener: (Context) -> Unit) { changeListeners.add(listener) }
    fun removeChangeListener(listener: (Context) -> Unit) { changeListeners.remove(listener) }
    private fun notifyChanges(context: Context) {
        if (changeListeners.isEmpty()) return
        val application = context.applicationContext
        notifications.execute { changeListeners.forEach { callback -> runCatching { callback(application) } } }
    }
    internal fun catalogChanged(context: Context) {
        synchronized(this) { blockedCache = null }
        notifyChanges(context)
    }

    fun add(context: Context, category: RuleCategory, raw: String): Boolean = addAll(context, category, listOf(raw)) > 0

    /** A single synchronized read/merge/write per category avoids quadratic import work. */
    @Synchronized fun addAll(context: Context, category: RuleCategory, raw: Collection<String>): Int {
        val existing = local(context, category)
        val merged = DomainRules.merge(existing, raw)
        val added = merged.size - existing.size
        if (added > 0) {
            preferences(context).edit().putStringSet(key(category), merged).apply()
            blockedCache = null
        }
        return added
    }

    @Synchronized fun remove(context: Context, category: RuleCategory, domain: String) {
        if (ProtectionCommitment.isActive(context)) return
        val normalized = normalize(domain) ?: return
        val all = local(context, category).toMutableSet()
        if (all.remove(normalized)) {
            preferences(context).edit().putStringSet(key(category), all).apply()
            blockedCache = null
        }
    }

    fun isBlocked(context: Context, host: String): Boolean {
        val rules = synchronized(this) {
            preferences(context)
            blockedCache ?: RuleCategory.entries.flatMap { get(context, it) }.toSet().also { blockedCache = it }
        }
        val normalized = DomainRules.normalizeHostname(host) ?: return false
        return DomainRules.isBlocked(normalized, rules) || BundledGamblingRules.isBlockedNormalized(context, normalized)
    }

    fun matches(host: String, rule: String): Boolean = DomainRules.matches(host, rule)

    private fun key(category: RuleCategory) = when (category) { RuleCategory.GAMBLING -> GAMBLING; RuleCategory.ADULT -> ADULT; RuleCategory.PERSONAL -> PERSONAL }

    fun normalize(raw: String): String? = DomainRules.normalize(raw)
}
