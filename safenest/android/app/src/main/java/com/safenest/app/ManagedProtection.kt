package com.safenest.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import org.json.JSONArray

data class ManagedProtectionResult(
    val managed: Boolean,
    val suspendedVpnApps: List<String> = emptyList(),
    val notSuspendedVpnApps: List<String> = emptyList(),
    val message: String? = null
)

/**
 * Android readbacks for managed VPN enforcement, separate from recovery bookkeeping.
 * `verified` describes policy configuration, not network health or perfect tamper resistance.
 */
data class ManagedPolicyStatus(
    val isDeviceOwner: Boolean = false,
    val adminActive: Boolean = false,
    val policyConfigured: Boolean = false,
    val alwaysOnSafeNest: Boolean = false,
    val vpnConfigRestricted: Boolean = false,
    val uninstallBlocked: Boolean = false,
    val lockdownEnabled: Boolean? = null,
    val error: String? = null
) {
    val verified: Boolean get() = isDeviceOwner && adminActive && policyConfigured &&
        alwaysOnSafeNest && vpnConfigRestricted && uninstallBlocked && lockdownEnabled == false && error == null
}

/** Counts describe submitted URL rules, not confirmed browser enforcement. */
data class ChromeDomainPolicyStatus(
    val active: Boolean = false,
    val requestedDomains: Int = 0,
    val listedDomains: Int = 0,
    val omittedDomains: Int = 0,
    val preexistingEntries: Int = 0,
    val allowlistEntries: Int = 0,
    val warning: String? = null,
    val lastUpdatedMillis: Long = 0
)

/**
 * Opt-in device-owner policies with a durable journal for release/partial failures.
 * Call apply/release from a worker thread: restoring Private DNS can perform I/O.
 * This is not an HTTPS proxy classifier and never enables VPN lockdown.
 */
object ManagedProtection {
    private const val PREFS = "safenest_managed"
    private const val CHROME = "com.android.chrome"
    private const val DOH_KEY = "DnsOverHttpsMode"
    private const val VPN = "vpn"
    private const val DNS = "dns"
    private const val CHROME_DNS = "chrome_dns"
    private const val CHROME_URLS = "chrome_urls"
    private const val BLOCKLIST_KEY = "URLBlocklist"
    private const val ALLOWLIST_KEY = "URLAllowlist"
    private const val UNINSTALL = "uninstall"
    private fun admin(context: Context) = ComponentName(context, SafeNestAdminReceiver::class.java)
    private fun policy(context: Context): DevicePolicyManager =
        checkNotNull(context.getSystemService(DevicePolicyManager::class.java)) { "Android device policy service is unavailable." }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun pending(prefs: SharedPreferences) = prefs.getStringSet("pending_policies", emptySet()).orEmpty().toSet()
    private fun tracked(prefs: SharedPreferences) = prefs.getStringSet("suspended_vpn_packages", emptySet()).orEmpty().toSet()
    private fun save(editor: SharedPreferences.Editor) { check(editor.commit()) { "Could not save policy recovery state." } }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(context.packageName) == true

    /** Recovery footprint only: true can mean partial setup or incomplete release, not enforcement. */
    fun isConfigured(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        val state = prefs(context)
        if (state.getInt("journal_schema", 0) == 2) {
            return state.getBoolean("active", false) || pending(state).isNotEmpty() || tracked(state).isNotEmpty()
        }
        return legacyConfigured(context)
    }

    /** Worker-thread read of actual Android policy values; never infers enforcement from prefs. */
    fun policyStatus(context: Context): ManagedPolicyStatus {
        val configured = prefs(context).getBoolean("active", false)
        var status = ManagedPolicyStatus(policyConfigured = configured)
        val errors = mutableListOf<String>()
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
            ?: return status.copy(error = "Android device policy service is unavailable.")
        val component = admin(context)
        try { status = status.copy(isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)) }
        catch (error: Exception) { errors.add("Device owner: ${error.message ?: error.javaClass.simpleName}") }
        try { status = status.copy(adminActive = dpm.isAdminActive(component)) }
        catch (error: Exception) { errors.add("Device admin: ${error.message ?: error.javaClass.simpleName}") }
        if (!status.isDeviceOwner || !status.adminActive) {
            return status.copy(error = errors.joinToString(" ").ifEmpty { null })
        }
        try { status = status.copy(alwaysOnSafeNest = dpm.getAlwaysOnVpnPackage(component) == context.packageName) }
        catch (error: Exception) { errors.add("Always-on: ${error.message ?: error.javaClass.simpleName}") }
        try { status = status.copy(vpnConfigRestricted = dpm.getUserRestrictions(component).getBoolean(UserManager.DISALLOW_CONFIG_VPN)) }
        catch (error: Exception) { errors.add("VPN settings: ${error.message ?: error.javaClass.simpleName}") }
        try { status = status.copy(uninstallBlocked = dpm.isUninstallBlocked(component, context.packageName)) }
        catch (error: Exception) { errors.add("Uninstall: ${error.message ?: error.javaClass.simpleName}") }
        if (Build.VERSION.SDK_INT >= 29) {
            try { status = status.copy(lockdownEnabled = dpm.isAlwaysOnVpnLockdownEnabled(component)) }
            catch (error: Exception) { errors.add("Lockdown: ${error.message ?: error.javaClass.simpleName}") }
        } else {
            errors.add("Android 8–9 cannot independently report managed Lockdown through this API; enforcement is not fully verified.")
        }
        return status.copy(error = errors.joinToString(" ").ifEmpty { null })
    }

    private fun legacyConfigured(context: Context): Boolean {
        val dpm = policy(context)
        val component = admin(context)
        val restrictions = dpm.getUserRestrictions(component)
        return dpm.getAlwaysOnVpnPackage(component) == context.packageName ||
            restrictions.getBoolean(UserManager.DISALLOW_CONFIG_VPN) ||
            restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES) ||
            restrictions.getBoolean(UserManager.DISALLOW_SAFE_BOOT) ||
            dpm.isUninstallBlocked(component, context.packageName) || tracked(prefs(context)).isNotEmpty()
    }

    private fun mark(state: SharedPreferences, token: String, values: (SharedPreferences.Editor) -> Unit = {}) {
        if (token in pending(state)) return
        val edit = state.edit().putStringSet("pending_policies", pending(state) + token)
        values(edit)
        save(edit) // Before mutation, so process termination leaves a release path.
    }

    private fun unmark(state: SharedPreferences, token: String) =
        save(state.edit().putStringSet("pending_policies", pending(state) - token))

    @Synchronized fun apply(context: Context): ManagedProtectionResult {
        if (!isDeviceOwner(context)) return ManagedProtectionResult(false, message = "SafeNest is not enrolled as this device's owner.")
        if (!ProtectionCommitment.isActive(context)) return ManagedProtectionResult(false,
            message = "Activate a verified paid protection period before applying managed controls.")
        if (!GuardPreferences.isEnabled(context) || !GuardPreferences.isAccessibilityEnabled(context)) {
            return ManagedProtectionResult(false, message = "Enable SafeNest app guard and its Accessibility permission before applying managed protection.")
        }
        val state = prefs(context)
        val dpm = policy(context)
        val component = admin(context)
        if (state.getInt("journal_schema", 0) != 2 && legacyConfigured(context)) {
            val old = release(context)
            if (old.managed) return old.copy(message = "Earlier managed settings need recovery before applying new rules. ${old.message.orEmpty()}")
        }
        if (!state.getBoolean("active", false) && (pending(state).isNotEmpty() || tracked(state).isNotEmpty())) {
            val remaining = release(context)
            if (remaining.managed) return remaining.copy(message = "Finish recovery before applying managed rules. ${remaining.message.orEmpty()}")
        }
        return try {
            save(state.edit().putInt("journal_schema", 2))
            if (VPN !in pending(state)) {
                val previous = dpm.getAlwaysOnVpnPackage(component)
                check(Build.VERSION.SDK_INT >= 29 || previous == null) {
                    "On Android 8–9, remove the previous always-on VPN in Settings before applying managed rules."
                }
                mark(state, VPN) {
                    it.putString("previous_vpn", previous)
                    if (Build.VERSION.SDK_INT >= 29) {
                        it.putBoolean("previous_vpn_lockdown", dpm.isAlwaysOnVpnLockdownEnabled(component))
                        it.putStringSet("previous_vpn_allowlist", dpm.getAlwaysOnVpnLockdownWhitelist(component).orEmpty())
                    }
                }
            }
            // A DNS-only tunnel cannot safely enable Android's full traffic lockdown.
            dpm.setAlwaysOnVpnPackage(component, context.packageName, false)
            addRestriction(context, UserManager.DISALLOW_CONFIG_VPN)
            addRestriction(context, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            if (Build.VERSION.SDK_INT >= 29) {
                val mode = dpm.getGlobalPrivateDnsMode(component)
                check(mode != DevicePolicyManager.PRIVATE_DNS_MODE_UNKNOWN) { "Android could not report its Private DNS setting." }
                if (mode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
                    val host = dpm.getGlobalPrivateDnsHost(component)
                    check(!host.isNullOrEmpty()) { "Android could not report the existing Private DNS provider." }
                    mark(state, DNS) { it.putString("previous_dns_host", host) }
                    check(dpm.setGlobalPrivateDnsModeOpportunistic(component) == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                        "Android could not switch Private DNS to Automatic."
                    }
                }
                // Off stays Off; Automatic stays Automatic. Neither needs restoration.
                addRestriction(context, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                addRestriction(context, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
            applyChromeDns(context)
            val chromeStatus = applyChromeDomains(context)
            if (!dpm.isUninstallBlocked(component, context.packageName)) {
                mark(state, UNINSTALL)
                dpm.setUninstallBlocked(component, context.packageName, true)
            }
            val failures = suspendVpnApps(context, findVpnPackages(context))
            // Configuration calls can succeed while settings have changed or were rejected by
            // a vendor policy controller. Do not persist success until mandatory readbacks match.
            val applied = policyStatus(context)
            check(applied.isDeviceOwner && applied.adminActive) { "Android did not confirm device-owner administration." }
            check(applied.alwaysOnSafeNest) { "Android did not confirm SafeNest as the managed Always-on VPN." }
            check(applied.vpnConfigRestricted) { "Android did not confirm the managed VPN-settings restriction." }
            check(applied.uninstallBlocked) { "Android did not confirm the SafeNest uninstall restriction." }
            if (Build.VERSION.SDK_INT >= 29) {
                check(applied.lockdownEnabled == false && applied.error == null) {
                    applied.error ?: "Android did not confirm Lockdown is off; DNS-only mode cannot safely use Lockdown."
                }
            }
            save(state.edit().putBoolean("active", true))
            val confirmedSuspended = tracked(state).filter {
                try { dpm.isPackageSuspended(component, it) } catch (_: Exception) { false }
            }.sorted()
            ManagedProtectionResult(true, confirmedSuspended, failures,
                listOfNotNull(
                    if (Build.VERSION.SDK_INT < 29) "Private DNS settings cannot be locked on this Android version." else null,
                    if (failures.isNotEmpty()) "Some VPN clients could not be suspended; review the list." else null,
                    chromeStatus.warning
                ).joinToString(" ").ifEmpty { null })
        } catch (error: Exception) {
            val result = release(context)
            val explanation = error.message?.take(240) ?: error.javaClass.simpleName
            result.copy(message = "Managed setup failed: $explanation Recovery: ${result.message ?: if (result.managed) "incomplete" else "completed"}.")
        }
    }

    private fun addRestriction(context: Context, restriction: String) {
        val dpm = policy(context)
        val component = admin(context)
        if (!dpm.getUserRestrictions(component).getBoolean(restriction)) {
            mark(prefs(context), "restriction:$restriction")
            dpm.addUserRestriction(component, restriction)
        }
    }

    private fun applyChromeDns(context: Context) {
        try { context.packageManager.getApplicationInfo(CHROME, 0) }
        catch (_: PackageManager.NameNotFoundException) { return }
        val dpm = policy(context)
        val component = admin(context)
        val state = prefs(context)
        val current = dpm.getApplicationRestrictions(component, CHROME) ?: Bundle()
        @Suppress("DEPRECATION")
        val previousDns = current.get(DOH_KEY)
        check(!current.containsKey(DOH_KEY) || previousDns is String) {
            "Existing Chrome Secure DNS policy has an unsupported type; it was left unchanged."
        }
        if (current.getString(DOH_KEY) == "off" && CHROME_DNS !in pending(state)) return
        mark(state, CHROME_DNS) {
            it.putBoolean("previous_chrome_dns_present", current.containsKey(DOH_KEY))
                .putString("previous_chrome_dns", current.getString(DOH_KEY))
        }
        dpm.setApplicationRestrictions(component, CHROME, Bundle(current).apply { putString(DOH_KEY, "off") })
    }

    /** Cheap status read. Counts concern the last submitted list, not runtime enforcement. */
    fun chromePolicyStatus(context: Context): ChromeDomainPolicyStatus {
        val state = prefs(context)
        return ChromeDomainPolicyStatus(
            active = state.getBoolean("chrome_urls_active", false) && state.getBoolean("active", false),
            requestedDomains = state.getInt("chrome_urls_requested", 0),
            listedDomains = state.getInt("chrome_urls_listed", 0),
            omittedDomains = state.getInt("chrome_urls_omitted", 0),
            preexistingEntries = state.getInt("chrome_urls_existing", 0),
            allowlistEntries = state.getInt("chrome_urls_allowlist", 0),
            warning = state.getString("chrome_urls_warning", null),
            lastUpdatedMillis = state.getLong("chrome_urls_updated", 0)
        )
    }

    /** Worker thread only. Call after list edits, catalog refresh, and process restart. */
    @Synchronized fun refreshDomainPolicy(context: Context): ChromeDomainPolicyStatus {
        if (!isDeviceOwner(context) || !prefs(context).getBoolean("active", false)) {
            return ChromeDomainPolicyStatus(warning = "Managed Chrome rules require active device-owner enrollment.")
        }
        return try { applyChromeDomains(context) }
        catch (error: Exception) {
            val state = prefs(context)
            save(state.edit().putBoolean("chrome_urls_active", false)
                .putString("chrome_urls_warning", "Chrome rule refresh failed: ${error.message?.take(240) ?: error.javaClass.simpleName}. Previous submitted rules may remain; review chrome://policy."))
            chromePolicyStatus(context)
        }
    }

    // Chromium's Android policy schema advertises a JSON-encoded string for list policies.
    // PolicyConverter also supports String[], which we accept/preserve for existing policies.
    // A malformed existing value is never overwritten or silently discarded.
    @Suppress("DEPRECATION")
    private fun restrictionValue(bundle: Bundle, key: String): Pair<String, String?> {
        if (!bundle.containsKey(key)) return "absent" to null
        val value = bundle.get(key)
        return when (value) {
            is String -> "string" to value
            is Array<*> -> {
                require(value.all { it is String }) { "$key contains a non-string entry; existing policy was left unchanged." }
                "array" to JSONArray(value.toList()).toString()
            }
            else -> error("$key has an unsupported value; existing policy was left unchanged.")
        }
    }

    private fun decodeList(value: Pair<String, String?>): List<String> {
        if (value.first == "absent") return emptyList()
        val raw = checkNotNull(value.second)
        // Chrome treats an empty list-policy string as unset. Preserve its exact
        // original representation in the journal while planning from an empty list.
        if (value.first == "string" && raw.isBlank()) return emptyList()
        require(raw.length <= 512_000) { "Existing Chrome policy is too large to edit safely." }
        val array = JSONArray(raw)
        return (0 until array.length()).map {
            val item = array.get(it)
            require(item is String) { "Existing Chrome URL policy contains a non-string entry." }
            item
        }
    }

    private fun originalChromeUrls(state: SharedPreferences): Pair<String, String?> =
        checkNotNull(state.getString("previous_chrome_urls_type", null)) to state.getString("previous_chrome_urls", null)

    private fun writeRestriction(bundle: Bundle, key: String, value: Pair<String, String?>) {
        when (value.first) {
            "absent" -> bundle.remove(key)
            "string" -> bundle.putString(key, checkNotNull(value.second))
            "array" -> bundle.putStringArray(key, decodeList(value).toTypedArray())
            else -> error("Invalid policy recovery state.")
        }
    }

    private fun applyChromeDomains(context: Context): ChromeDomainPolicyStatus {
        val state = prefs(context)
        try { context.packageManager.getApplicationInfo(CHROME, 0) }
        catch (_: PackageManager.NameNotFoundException) {
            save(state.edit().putBoolean("chrome_urls_active", false)
                .putString("chrome_urls_warning", "Chrome is not installed; browser policy is not active."))
            return chromePolicyStatus(context)
        }
        val chromeMajor = context.packageManager.getPackageInfo(CHROME, 0).versionName?.substringBefore('.')?.toIntOrNull()
        check(chromeMajor == null || chromeMajor >= 86) { "Chrome 86 or later is required for URLBlocklist; update Chrome first." }
        val dpm = policy(context)
        val component = admin(context)
        val currentBundle = dpm.getApplicationRestrictions(component, CHROME) ?: Bundle()
        val current = restrictionValue(currentBundle, BLOCKLIST_KEY)
        val original = if (CHROME_URLS in pending(state)) originalChromeUrls(state) else current
        if (CHROME_URLS in pending(state)) {
            val expected = "string" to checkNotNull(state.getString("chrome_urls_expected", null))
            check(current == expected) { "Chrome URLBlocklist was changed outside SafeNest; refresh stopped to preserve that change. Release/review managed settings first" }
        }
        val existing = decodeList(original)
        val allowlist = decodeList(restrictionValue(currentBundle, ALLOWLIST_KEY))
        // Chrome's managed list is capped; send editable/catalog rules plus the Bangladesh-researched
        // names. DNS filtering still uses the full compact bundled list.
        val bangladesh = BundledGamblingRules.priorityDomains(context)
        val domains = RuleCategory.entries.flatMap { RulesStore.get(context, it) }.toSet() + bangladesh
        val plan = ChromePolicyRules.plan(existing, domains, priorityDomains = RulesStore.priorityDomains(context) + bangladesh)
        val desired = JSONArray(plan.entries).toString()
        val warnings = listOfNotNull(
            if (plan.omittedDomains > 0) "Chrome capacity: ${plan.listedDomains}/${plan.requestedDomains} domains submitted; ${plan.omittedDomains} omitted from this layer. DNS and the optional app guard still use the full list." else null,
            if (allowlist.isNotEmpty()) "${allowlist.size} pre-existing Chrome allowlist entries were preserved and may override blocks. Review chrome://policy." else null,
            "Chrome must apply the submitted policies; verify URLBlocklist and DnsOverHttpsMode at chrome://policy and test a harmless blocked domain."
        ).joinToString(" ")
        mark(state, CHROME_URLS) {
            it.putString("previous_chrome_urls_type", original.first).putString("previous_chrome_urls", original.second)
                .putString("chrome_urls_expected", desired)
        }
        // Write-ahead record is essential on subsequent refreshes too. Retain the last value
        // as well, so recovery works if Android rejects this write or the process terminates.
        save(state.edit().putString("chrome_urls_prior_expected", state.getString("chrome_urls_expected", null))
            .putString("chrome_urls_expected", desired))
        dpm.setApplicationRestrictions(component, CHROME, Bundle(currentBundle).apply { putString(BLOCKLIST_KEY, desired) })
        check(restrictionValue(dpm.getApplicationRestrictions(component, CHROME) ?: Bundle(), BLOCKLIST_KEY) == ("string" to desired)) {
            "Android did not retain the submitted Chrome blocklist"
        }
        save(state.edit().putBoolean("chrome_urls_active", true)
            .putInt("chrome_urls_requested", plan.requestedDomains).putInt("chrome_urls_listed", plan.listedDomains)
            .putInt("chrome_urls_omitted", plan.omittedDomains).putInt("chrome_urls_existing", plan.preexistingEntries)
            .putInt("chrome_urls_allowlist", allowlist.size).putString("chrome_urls_warning", warnings)
            .putLong("chrome_urls_updated", System.currentTimeMillis()).remove("chrome_urls_prior_expected"))
        // Initial apply has not committed active=true yet, so don't mask this result here.
        return chromePolicyStatus(context).copy(active = true)
    }

    private fun restoreChromeDomains(context: Context) {
        val state = prefs(context)
        val dpm = policy(context)
        val component = admin(context)
        val currentBundle = dpm.getApplicationRestrictions(component, CHROME) ?: Bundle()
        val current = restrictionValue(currentBundle, BLOCKLIST_KEY)
        val original = originalChromeUrls(state)
        if (current == original) return // Mutation did not happen, or was already restored.
        val expected = state.getString("chrome_urls_expected", null)
        val priorExpected = state.getString("chrome_urls_prior_expected", null)
        check(ChromePolicyRules.canRestore(current, original, expected, priorExpected)) {
            "Chrome URLBlocklist changed outside SafeNest; it was preserved. Review the policy before retrying recovery"
        }
        val restored = Bundle(currentBundle)
        writeRestriction(restored, BLOCKLIST_KEY, original)
        dpm.setApplicationRestrictions(component, CHROME, restored)
        check(restrictionValue(dpm.getApplicationRestrictions(component, CHROME) ?: Bundle(), BLOCKLIST_KEY) == original) {
            "Android did not confirm restoration of Chrome URLBlocklist"
        }
    }

    private fun suspendVpnApps(context: Context, candidates: List<String>): List<String> {
        val state = prefs(context)
        val dpm = policy(context)
        val component = admin(context)
        val failures = mutableListOf<String>()
        for (name in candidates.distinct()) {
            if (name == context.packageName || GuardPreferences.isEssentialPackage(context, name)) continue
            try {
                if (dpm.isPackageSuspended(component, name)) continue // Preserve prior suspensions.
                save(state.edit().putStringSet("suspended_vpn_packages", tracked(state) + name))
                val failed = dpm.setPackagesSuspended(component, arrayOf(name), true)
                if (name in failed) {
                    save(state.edit().putStringSet("suspended_vpn_packages", tracked(state) - name))
                    failures.add(name)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                save(state.edit().putStringSet("suspended_vpn_packages", tracked(state) - name))
            } catch (_: Exception) {
                failures.add(name) // Journal entry remains for release if mutation took effect.
            }
        }
        return failures
    }

    /**
     * After the paid period: give up device-owner rights so the customer can uninstall SafeNest
     * normally, without a factory reset. A later Strong lock needs a fresh QR setup.
     */
    @Synchronized fun removeManagement(context: Context): ManagedProtectionResult {
        if (!RemovalRules.canRemoveManagement(isDeviceOwner(context), ProtectionCommitment.isActive(context), isConfigured(context))) {
            return ManagedProtectionResult(isConfigured(context),
                message = "SafeNest can be removed after the paid period ends and Strong lock has been released.")
        }
        return try {
            @Suppress("DEPRECATION")
            policy(context).clearDeviceOwnerApp(context.packageName)
            ManagedProtectionResult(false, message = if (isDeviceOwner(context)) "Android kept SafeNest as device owner. Retry." else null)
        } catch (error: SecurityException) {
            ManagedProtectionResult(false, message = "Android refused to remove SafeNest management: ${error.message.orEmpty()}")
        }
    }

    @Synchronized fun release(context: Context): ManagedProtectionResult {
        if (!isDeviceOwner(context)) return ManagedProtectionResult(false)
        val state = prefs(context)
        val dpm = policy(context)
        val component = admin(context)
        val errors = mutableListOf<String>()
        save(state.edit().putBoolean("active", false)) // Receiver stops adding restrictions immediately.
        // The previous always-on client may be one of these apps. Resume it
        // before restoring its VPN policy, or Android can reject the restore.
        for (name in tracked(state)) {
            try {
                context.packageManager.getApplicationInfo(name, 0)
                if (name in dpm.setPackagesSuspended(component, arrayOf(name), false)) errors.add("resume:$name")
                else save(state.edit().putStringSet("suspended_vpn_packages", tracked(state) - name))
            } catch (_: PackageManager.NameNotFoundException) {
                save(state.edit().putStringSet("suspended_vpn_packages", tracked(state) - name))
            } catch (_: Exception) { errors.add("resume:$name") }
        }
        if (state.getInt("journal_schema", 0) != 2) {
            // Compatibility cleanup for policies the earlier SafeNest prototype applied.
            for (restriction in listOf(UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_DEBUGGING_FEATURES, UserManager.DISALLOW_SAFE_BOOT)) {
                try { dpm.clearUserRestriction(component, restriction) } catch (_: Exception) { errors.add(restriction) }
            }
            try { if (dpm.getAlwaysOnVpnPackage(component) == context.packageName) dpm.setAlwaysOnVpnPackage(component, null, false) }
            catch (_: Exception) { errors.add(VPN) }
            try { dpm.setUninstallBlocked(component, context.packageName, false) }
            catch (_: Exception) { errors.add(UNINSTALL) }
        } else {
            for (token in pending(state).filter { it.startsWith("restriction:") }) {
                restore(state, token, errors) { dpm.clearUserRestriction(component, token.removePrefix("restriction:")) }
            }
            restore(state, VPN, errors) {
                if (dpm.getAlwaysOnVpnPackage(component) == context.packageName) {
                    val previous = state.getString("previous_vpn", null)
                    if (Build.VERSION.SDK_INT >= 29) {
                        dpm.setAlwaysOnVpnPackage(component, previous,
                            state.getBoolean("previous_vpn_lockdown", false),
                            state.getStringSet("previous_vpn_allowlist", emptySet()).orEmpty())
                    } else dpm.setAlwaysOnVpnPackage(component, previous, false)
                }
            }
            restore(state, UNINSTALL, errors) { dpm.setUninstallBlocked(component, context.packageName, false) }
            restore(state, CHROME_DNS, errors) {
                val current = dpm.getApplicationRestrictions(component, CHROME) ?: Bundle()
                if (current.getString(DOH_KEY) == "off") {
                    val restored = Bundle(current)
                    if (state.getBoolean("previous_chrome_dns_present", false)) restored.putString(DOH_KEY, state.getString("previous_chrome_dns", null))
                    else restored.remove(DOH_KEY)
                    dpm.setApplicationRestrictions(component, CHROME, restored)
                }
            }
            restore(state, CHROME_URLS, errors) { restoreChromeDomains(context) }
            restore(state, DNS, errors) {
                if (Build.VERSION.SDK_INT >= 29 && dpm.getGlobalPrivateDnsMode(component) == DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC) {
                    val host = checkNotNull(state.getString("previous_dns_host", null))
                    check(dpm.setGlobalPrivateDnsModeSpecifiedHost(component, host) == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR)
                }
            }
        }
        if (errors.isEmpty() && pending(state).isEmpty() && tracked(state).isEmpty()) {
            save(state.edit().clear().putInt("journal_schema", 2))
        }
        return ManagedProtectionResult(isConfigured(context), notSuspendedVpnApps = tracked(state).sorted(),
            message = if (errors.isEmpty()) null else "Some settings still need recovery: ${errors.joinToString()}. Retry recovery; Private DNS restoration needs network access.")
    }

    private fun restore(state: SharedPreferences, token: String, errors: MutableList<String>, action: () -> Unit) {
        if (token !in pending(state)) return
        try { action(); unmark(state, token) }
        catch (error: Exception) { errors.add("$token: ${error.message?.take(180) ?: error.javaClass.simpleName}") }
    }

    fun findVpnPackages(context: Context): List<String> = try {
        context.packageManager.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), PackageManager.MATCH_ALL)
            .mapNotNull { it.serviceInfo?.packageName }
            .plus(KnownVpnPackages.installed(context))
            .distinct().sorted()
    } catch (_: Exception) { KnownVpnPackages.installed(context).sorted() }

    @Synchronized fun onPackageAdded(context: Context, name: String) {
        if (!isDeviceOwner(context) || !prefs(context).getBoolean("active", false)) return
        if (name == CHROME) {
            applyChromeDns(context)
            refreshDomainPolicy(context)
        }
        if (name in findVpnPackages(context)) suspendVpnApps(context, listOf(name))
    }
}

/** Serializes install handling with apply/release and preserves all previous journal entries. */
class VpnPackageAddedReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.data?.schemeSpecificPart ?: return
        val pendingResult = goAsync()
        Thread {
            try { ManagedProtection.onPackageAdded(context.applicationContext, name) }
            catch (error: Exception) { Log.w("SafeNestManaged", "New app policy failed: ${error.javaClass.simpleName}") }
            finally { pendingResult.finish() }
        }.start()
    }
}
