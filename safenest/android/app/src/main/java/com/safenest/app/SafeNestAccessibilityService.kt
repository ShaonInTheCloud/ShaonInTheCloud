package com.safenest.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Explicitly consented finite self-exclusion. Reads narrow app/control labels and
 * supported browser address bars, never messages, page bodies, passwords or history.
 * System-screen recognition is best effort, not Device Owner or root access.
 */
class SafeNestAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var isConnected: Boolean = false
            private set
        @Volatile var lastBlockReason: String? = null
            private set
    }

    private var lastHomeAction = 0L
    private var lastToast = 0L
    private var lastVpnRefresh = 0L
    private var vpnPackages: Set<String> = emptySet()
    private val handler = Handler(Looper.getMainLooper())
    private val expiryPoll = object : Runnable {
        override fun run() {
            ProtectionCommitment.checkpoint(this@SafeNestAccessibilityService)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        handler.post(expiryPoll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !GuardPreferences.isEnabled(this)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val name = event.packageName?.toString() ?: return
        if (name == packageName) return
        val now = SystemClock.elapsedRealtime()
        if (SystemScreenGuard.isSystemSurface(name)) {
            // The consumer Play build must leave uninstall, permissions and Settings usable.
            if (!BuildConfig.ALLOW_SYSTEM_GUARD) return
            val root = rootInActiveWindow ?: return
            try {
                if (root.packageName?.toString() != name) return
                val screen = readControlLabels(root)
                if (SystemScreenGuard.blocksSafeNestControl(name, screen.labels, screen.actions, screen.checkedToggle)) {
                    returnHome(now, "safenest_control", "SafeNest commitment is active until your paid period ends.")
                } else if (GuardPreferences.blocksVpnApps(this) &&
                    SystemScreenGuard.blocksVpnInstall(name, screen.titles, screen.actions)) {
                    returnHome(now, "vpn_install", "SafeNest blocked this detected VPN installation screen.")
                }
            } finally { @Suppress("DEPRECATION") root.recycle() }
            return
        }
        if (GuardRules.isBrowserExempt(name)) {
            // In Play, the DNS filter denies hosts while Chrome/Firefox stay open.
            if (!BuildConfig.ALLOW_SYSTEM_GUARD) return
            val root = rootInActiveWindow ?: return
            try {
                if (root.packageName?.toString() != name) return
                // Address-bar resource IDs only: never traverse browser page content.
                val ids = listOf("url_bar", "mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view")
                for (id in ids) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("$name:id/$id")
                    try {
                        val blocked = nodes.any { node ->
                            !node.isPassword && GuardRules.hostFromAddressBar(node.text?.toString().orEmpty())
                                ?.let { RulesStore.isBlocked(this, it) } == true
                        }
                        if (blocked) { returnHome(now, "website", "SafeNest blocked this website."); return }
                    } finally { nodes.forEach { @Suppress("DEPRECATION") it.recycle() } }
                }
            } finally { @Suppress("DEPRECATION") root.recycle() }
            return
        }
        val explicitBlocked = GuardPreferences.isBlockedPackage(this, name)
        if (GuardPreferences.blocksVpnApps(this) &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || now - lastVpnRefresh > 5000L)) {
            vpnPackages = GuardPreferences.getDetectedVpnPackages(this)
            lastVpnRefresh = now
        }
        val blockedApp = explicitBlocked || (GuardPreferences.blocksVpnApps(this) && name in vpnPackages)
        if (GuardRules.shouldReturnHome(name, blockedApp, GuardPreferences.isEssentialPackage(this, name))) {
            // Ignore stale accessibility events from an app that is no longer visible.
            val root = rootInActiveWindow ?: return
            try {
                if (root.packageName?.toString() == name) returnHome(now, "app", "SafeNest blocked this app.")
            } finally { @Suppress("DEPRECATION") root.recycle() }
            return
        }
    }

    private data class ControlLabels(val labels: Set<String>, val actions: Set<String>,
                                     val titles: Set<String>, val checkedToggle: Boolean)

    /** Bounded traversal on system/installer surfaces only; values are never logged or uploaded. */
    private fun readControlLabels(root: AccessibilityNodeInfo): ControlLabels {
        val labels = mutableSetOf<String>(); val actions = mutableSetOf<String>(); val titles = mutableSetOf<String>()
        var checkedToggle = false; var visited = 0
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let(queue::add)
        try {
            while (queue.isNotEmpty() && visited++ < 180) {
                val node = queue.removeFirst()
                try {
                    if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue
                    val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                        .filter { it.length in 1..240 }
                    labels.addAll(values)
                    if (node.isEnabled && node.isClickable) actions.addAll(values)
                    val id = node.viewIdResourceName.orEmpty().lowercase()
                    if (id.contains("title") || id.contains("app_name") || id.contains("headline")) titles.addAll(values)
                    if (node.isEnabled && node.isCheckable && node.isChecked) checkedToggle = true
                    if (queue.size < 180) for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                } finally { @Suppress("DEPRECATION") node.recycle() }
            }
        } finally { queue.forEach { @Suppress("DEPRECATION") it.recycle() } }
        return ControlLabels(labels, actions, titles, checkedToggle)
    }

    private fun returnHome(now: Long, reason: String, message: String) {
        if (!GuardPreferences.isEnabled(this) || now - lastHomeAction < 1000L) return
        lastHomeAction = now
        if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            lastBlockReason = reason
            if (now - lastToast > 3000L) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                lastToast = now
            }
        }
    }

    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        handler.removeCallbacks(expiryPoll)
        // Retain the intended guard selection. If Android revokes Accessibility,
        // the setup status reports the missing permission; re-granting can resume it.
        return super.onUnbind(intent)
    }
    override fun onDestroy() { handler.removeCallbacks(expiryPoll); isConnected = false; super.onDestroy() }
}
