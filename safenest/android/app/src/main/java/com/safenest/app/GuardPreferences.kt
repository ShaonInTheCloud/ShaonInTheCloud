package com.safenest.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityManager

/** On-device choices. Enabling the guard must follow the UI's explicit disclosure. */
object GuardPreferences {
    private const val PREFS = "safenest_guard"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSelected(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
    fun isEnabled(context: Context): Boolean = isSelected(context) && ProtectionCommitment.isActive(context)
    fun hasTestControlConsent(context: Context): Boolean = LocalTestSession.enabled &&
        prefs(context).getBoolean("test_control_consent_v2", false)
    fun consentToTestControls(context: Context) {
        if (LocalTestSession.enabled) prefs(context).edit().putBoolean("test_control_consent_v2", true).apply()
    }
    fun isTestSetupReady(context: Context): Boolean = hasTestControlConsent(context) &&
        isSelected(context) && isAccessibilityEnabled(context) && isServiceConnected()
    fun testControlsActive(context: Context): Boolean = hasTestControlConsent(context) &&
        isEnabled(context) && LocalTestSession.isActive(context)
    fun setEnabled(context: Context, value: Boolean) {
        if (value && !ProtectionCommitment.hasVerifiedAccess(context)) return
        if (!value && (ProtectionCommitment.isActive(context) || ManagedProtection.isConfigured(context))) return
        prefs(context).edit().putBoolean("enabled", value).apply()
    }
    fun disable(context: Context) = setEnabled(context, false)
    fun blocksVpnApps(context: Context): Boolean = prefs(context).getBoolean("block_vpn_apps", false)
    fun setBlockVpnApps(context: Context, value: Boolean) {
        if (value && !ProtectionCommitment.hasVerifiedAccess(context)) return
        if (!value && (ProtectionCommitment.isActive(context) || ManagedProtection.isConfigured(context))) return
        prefs(context).edit().putBoolean("block_vpn_apps", value).apply()
    }

    internal fun commitVpnBlocking(context: Context) {
        prefs(context).edit().putBoolean("enabled", true).putBoolean("block_vpn_apps", true).apply()
    }
    internal fun clearForExpiry(context: Context) {
        prefs(context).edit().putBoolean("enabled", false).putBoolean("block_vpn_apps", false)
            .putBoolean("test_control_consent", false).putBoolean("test_control_consent_v2", false).commit()
    }

    /** Foreground event path: no installed-app inventory scan per event. */
    fun isBlockedPackage(context: Context, name: String): Boolean =
        (KnownGamblingPackages.contains(name) || name in prefs(context).getStringSet("blocked_packages", emptySet()).orEmpty()) &&
            GuardRules.isPackageName(name) && !GuardRules.isBrowserExempt(name) && !isEssentialPackage(context, name)

    fun getBlockedPackages(context: Context): Set<String> =
        (prefs(context).getStringSet("blocked_packages", emptySet()).orEmpty() + KnownGamblingPackages.installed(context))
            .filter { GuardRules.isPackageName(it) && !GuardRules.isBrowserExempt(it) && !isEssentialPackage(context, it) }.toSet()

    /** Only installed apps can be chosen. System/recovery apps cannot be added. */
    fun addBlockedPackage(context: Context, raw: String): Boolean {
        val name = raw.trim()
        if (!canBlockPackage(context, name)) return false
        val current = getBlockedPackages(context)
        if (name in current) return false
        prefs(context).edit().putStringSet("blocked_packages", current + name).apply()
        return true
    }

    fun canBlockPackage(context: Context, name: String): Boolean {
        if (!GuardRules.isPackageName(name) || GuardRules.isBrowserExempt(name) || isEssentialPackage(context, name)) return false
        return try { context.packageManager.getApplicationInfo(name, 0); true }
        catch (_: PackageManager.NameNotFoundException) { false }
    }

    fun removeBlockedPackage(context: Context, name: String) {
        if (ProtectionCommitment.isActive(context) || ManagedProtection.isConfigured(context)) return
        prefs(context).edit().putStringSet("blocked_packages", getBlockedPackages(context) - name).apply()
    }

    fun getDetectedVpnPackages(context: Context): Set<String> =
        ManagedProtection.findVpnPackages(context).filterNot { isEssentialPackage(context, it) }.toSet()

    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(context, SafeNestAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo?.serviceInfo
            info != null && ComponentName(info.packageName, info.name) == expected
        }
    }

    fun isServiceConnected(): Boolean = SafeNestAccessibilityService.isConnected
    fun lastBlockReason(): String? = SafeNestAccessibilityService.lastBlockReason

    fun isAdminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(
            ComponentName(context, SafeNestAdminReceiver::class.java)
        ) == true

    fun adminActivationIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, SafeNestAdminReceiver::class.java))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Adds an extra confirmation step before SafeNest can be removed. You can revoke this permission in Android Settings.")

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun batterySettingsIntent(context: Context): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Recovery, calls, permission screens and launchers must always remain usable. */
    fun isEssentialPackage(context: Context, name: String): Boolean {
        if (name == context.packageName || name in essentialPackages) return true
        val manager = context.packageManager
        val isSystem = try {
            val flags = manager.getApplicationInfo(name, 0).flags
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) { false }
        if (isSystem) return true
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        if (manager.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY).any { it.activityInfo?.packageName == name }) return true
        if (manager.queryIntentActivities(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY).any { it.activityInfo?.packageName == name }) return true
        return try { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == name } catch (_: Exception) { false }
    }

    private val essentialPackages = setOf(
        "android", "com.android.settings", "com.android.systemui", "com.android.shell",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.samsung.android.packageinstaller",
        "com.android.phone", "com.android.server.telecom", "com.android.dialer", "com.google.android.dialer"
    )
}
