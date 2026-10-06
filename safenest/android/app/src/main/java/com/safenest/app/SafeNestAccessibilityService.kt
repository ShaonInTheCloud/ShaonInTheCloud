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
    private var controlExit: ControlScreenExit? = null
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
        controlExit?.cancel()
        if (LocalTestSession.enabled) controlExit = ControlScreenExit(packageName, object : ControlScreenExit.Driver {
            override fun active() = isConnected && GuardPreferences.testControlsActive(this@SafeNestAccessibilityService)
            override fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
            override fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
            override fun foregroundPackage(): String? {
                val root = rootInActiveWindow ?: return null
                return try { root.packageName?.toString() } finally { @Suppress("DEPRECATION") root.recycle() }
            }
            override fun protectedDetail(): Boolean {
                val root = rootInActiveWindow ?: return false
                return try {
                    val name = root.packageName?.toString().orEmpty()
                    if (!SystemScreenGuard.isSystemSurface(name)) false else {
                        val screen = readControlLabels(root)
                        SystemScreenGuard.testControlReason(name, screen.labels, screen.titles, screen.actions).isNotEmpty()
                    }
                } finally { @Suppress("DEPRECATION") root.recycle() }
            }
            override fun post(action: Runnable, delayMs: Long) { handler.postDelayed(action, delayMs) }
        })
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
            val testControls = GuardPreferences.testControlsActive(this)
            if (!BuildConfig.ALLOW_SYSTEM_GUARD && !testControls) return
            val root = rootInActiveWindow ?: return
            try {
                if (root.packageName?.toString() != name) return
                val screen = readControlLabels(root)
                val testReason = if (testControls) SystemScreenGuard.testControlReason(name, screen.labels, screen.titles, screen.actions) else ""
                if (testReason.isNotEmpty()) {
                    returnHome(now, testReason, "SafeNest Test controls are guarded. Use Stop test in the app to end the test.", controlPackage = name)
                } else if (BuildConfig.ALLOW_SYSTEM_GUARD && SystemScreenGuard.blocksSafeNestControl(name, screen.labels, screen.actions, screen.checkedToggle)) {
                    returnHome(now, "safenest_control", "SafeNest commitment is active until your paid period ends.")
                } else if (BuildConfig.ALLOW_SYSTEM_GUARD && GuardPreferences.blocksVpnApps(this) &&
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
        // Settings often puts the label inside a clickable row, rather than on the clickable node.
        val queue = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>()
        for (i in 0 until minOf(root.childCount, 180)) root.getChild(i)?.let { queue.add(it to (root.isEnabled && root.isClickable)) }
        try {
            while (queue.isNotEmpty() && visited++ < 180) {
                val (node, parentActionable) = queue.removeFirst()
                try {
                    if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue
                    val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                        .filter { it.length in 1..240 }
                    labels.addAll(values)
                    val actionable = node.isEnabled && (node.isClickable || parentActionable)
                    if (actionable) actions.addAll(values)
                    val id = node.viewIdResourceName.orEmpty().lowercase()
                    if (id.contains("title") || id.contains("app_name") || id.contains("headline")) titles.addAll(values)
                    if (node.isEnabled && node.isCheckable && node.isChecked) checkedToggle = true
                    for (i in 0 until node.childCount) {
                        if (queue.size >= 180) break
                        node.getChild(i)?.let { queue.add(it to actionable) }
                    }
                } finally { @Suppress("DEPRECATION") node.recycle() }
            }
        } finally { queue.forEach { @Suppress("DEPRECATION") it.first.recycle() } }
        return ControlLabels(labels, actions, titles, checkedToggle)
    }

    private fun returnHome(now: Long, reason: String, message: String, controlPackage: String? = null) {
        val cooldown = if (controlPackage != null) 350L else 1000L
        if (!GuardPreferences.isEnabled(this) || now - lastHomeAction < cooldown) return
        lastHomeAction = now
        if (controlPackage != null) {
            // Pop the detail/dialog before Home so Settings restores its unguarded parent next time.
            if (controlExit?.exit(controlPackage) { recordHome(reason, message) } == true) lastBlockReason = reason
        } else finishHome(reason, message)
    }

    private fun finishHome(reason: String, message: String) {
        if (!GuardPreferences.isEnabled(this)) return
        if (performGlobalAction(GLOBAL_ACTION_HOME)) recordHome(reason, message)
    }

    private fun recordHome(reason: String, message: String) {
        lastBlockReason = reason
        val now = SystemClock.elapsedRealtime()
        if (now - lastToast > 3000L) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            lastToast = now
        }
    }

    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        handler.removeCallbacks(expiryPoll)
        controlExit?.cancel()
        // Retain the intended guard selection. If Android revokes Accessibility,
        // the setup status reports the missing permission; re-granting can resume it.
        return super.onUnbind(intent)
    }
    override fun onDestroy() { controlExit?.cancel(); handler.removeCallbacksAndMessages(null); isConnected = false; super.onDestroy() }
}
