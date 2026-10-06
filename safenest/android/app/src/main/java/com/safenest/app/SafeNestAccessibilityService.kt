package com.safenest.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        @Volatile var lastControlExitOutcome: String? = null
            private set
        private var connectedService: SafeNestAccessibilityService? = null
        internal fun windowStatusForQa(): String = if (BuildConfig.DEBUG && LocalTestSession.enabled)
            connectedService?.windowStatusForQa().orEmpty() else "disabled"
    }

    private var lastHomeAction = 0L
    private var lastToast = 0L
    private var lastVpnRefresh = 0L
    private var vpnPackages: Set<String> = emptySet()
    private var controlExit: ControlScreenExit? = null
    private val windowPackages = WindowPackageCache()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingControlScan: Runnable? = null
    private var pendingControlPackage: String? = null
    private var pendingControlWindowId = -1
    private val expiryPoll = object : Runnable {
        override fun run() {
            ProtectionCommitment.checkpoint(this@SafeNestAccessibilityService)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        connectedService = this
        controlExit?.cancel()
        if (LocalTestSession.enabled) controlExit = ControlScreenExit(packageName, object : ControlScreenExit.Driver {
            override fun active() = isConnected && GuardPreferences.testControlsActive(this@SafeNestAccessibilityService)
            override fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
            override fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
            override fun foregroundPackage(): String? {
                focusedControlWindow()?.pkg?.let { return it.takeUnless { it == "com.android.systemui" } }
                val root = controlForegroundRoot() ?: return null
                return try {
                    // Status/navigation windows may temporarily take focus during Back.
                    root.packageName?.toString()?.takeUnless { it == "com.android.systemui" }
                } finally { @Suppress("DEPRECATION") root.recycle() }
            }
            override fun protectedDetail(): Boolean {
                focusedControlWindow()?.let {
                    if (SystemScreenGuard.testFocusedWindowReason(it.pkg, it.title).isNotEmpty()) return true
                }
                val root = controlForegroundRoot() ?: return false
                return try {
                    val name = root.packageName?.toString().orEmpty()
                    if (!SystemScreenGuard.isSystemSurface(name)) false else {
                        val screen = readControlLabels(root)
                        SystemScreenGuard.testControlReason(name, screen.labels, screen.detailTitles, screen.actions).isNotEmpty()
                    }
                } finally { @Suppress("DEPRECATION") root.recycle() }
            }
            override fun post(action: Runnable, delayMs: Long) { handler.postDelayed(action, delayMs) }
            override fun note(outcome: String) { lastControlExitOutcome = outcome }
            override fun isHomePackage(pkg: String) = packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName == pkg
        })
        handler.post(expiryPoll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Public package/window IDs only. Remember ownership of reused Settings
        // windows even during consent/setup; no content or title is retained.
        if (LocalTestSession.enabled) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                windowPackages.observe(event.windowId, event.packageName?.toString())
            if (Build.VERSION.SDK_INT >= 28 && event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                (event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED) != 0)
                windowPackages.remove(event.windowId)
        }
        if (!GuardPreferences.isEnabled(this)) return
        if (LocalTestSession.enabled && event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Window-focus changes need not emit a new Settings content event.
            // Inspect only the focused root, never a Settings window behind a
            // different focused app, permission dialog or notification shade.
            val focus = focusedControlWindow() ?: return
            val name = focus.pkg ?: return
            val windowId = focus.id
            controlExit?.observeForeground(name)
            if (SystemScreenGuard.isSystemSurface(name)) {
                if (!inspectControlScreen(name, windowId)) scheduleControlScan(name, windowId)
            } else cancelControlScan()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val name = event.packageName?.toString() ?: return
        controlExit?.observeForeground(name)
        if (!SystemScreenGuard.isSystemSurface(name)) cancelControlScan()
        if (name == packageName) return
        val now = SystemClock.elapsedRealtime()
        if (SystemScreenGuard.isSystemSurface(name)) {
            // The consumer Play build must leave uninstall, permissions and Settings usable.
            val testControls = GuardPreferences.testControlsActive(this)
            if (!BuildConfig.ALLOW_SYSTEM_GUARD && !testControls) return
            if (!inspectControlScreen(name, event.windowId)) scheduleControlScan(name, event.windowId)
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
                                     val titles: Set<String>, val detailTitles: Set<String>, val checkedToggle: Boolean)

    private data class ControlWindow(val id: Int, val pkg: String?, val title: String?)
    private fun focusedControlWindow(): ControlWindow? {
        if (!LocalTestSession.enabled) return null
        val interactive = windows
        return try {
            val focus = interactive.take(12).firstOrNull { it.isFocused } ?: return null
            var pkg = windowPackages.owner(focus.id)
            if (pkg == null) {
                val root = focus.root
                try { pkg = root?.packageName?.toString(); windowPackages.observe(focus.id, pkg) }
                finally { @Suppress("DEPRECATION") root?.recycle() }
            }
            // Read a title only on the selected, focused Settings application
            // window; never underneath a system dialog or on other apps.
            val title = if (focus.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                pkg != null && SystemScreenGuard.isSystemSurface(pkg))
                focus.title?.toString()?.takeIf { it.length <= 240 } else null
            ControlWindow(focus.id, pkg, title)
        } finally { interactive.forEach { @Suppress("DEPRECATION") it.recycle() } }
    }

    /** Lab-only focus lookup. Active touch windows can outlive Back transitions. */
    private fun controlForegroundRoot(): AccessibilityNodeInfo? {
        if (!LocalTestSession.enabled) return rootInActiveWindow
        val interactive = windows
        try {
            val focused = interactive.take(12).firstOrNull { it.isFocused }
            if (focused != null) {
                focused.root?.let { return it }
                val active = rootInActiveWindow ?: return null
                if (active.windowId == focused.id) return active
                @Suppress("DEPRECATION") active.recycle()
                return null
            }
            return rootInActiveWindow
        } finally { interactive.forEach { @Suppress("DEPRECATION") it.recycle() } }
    }

    /** Disposable emulator diagnostic: window metadata/package only, no labels. */
    private fun windowStatusForQa(): String {
        val active = rootInActiveWindow
        val activeState = try { "active=${active?.packageName}:${active?.windowId}" }
            finally { @Suppress("DEPRECATION") active?.recycle() }
        val interactive = windows
        return try {
            activeState + "; windows=" + interactive.take(12).joinToString("|") { window ->
                val root = window.root
                try { "${window.id},type=${window.type},focus=${window.isFocused},active=${window.isActive},pkg=${root?.packageName},owner=${windowPackages.owner(window.id)}" }
                finally { @Suppress("DEPRECATION") root?.recycle() }
            }
        } finally { interactive.forEach { @Suppress("DEPRECATION") it.recycle() } }
    }

    private fun inspectControlScreen(name: String, expectedWindowId: Int = -1): Boolean {
        if (!isConnected || !GuardPreferences.isEnabled(this)) return false
        val testControls = GuardPreferences.testControlsActive(this)
        if (!BuildConfig.ALLOW_SYSTEM_GUARD && !testControls) return false
        if (testControls) focusedControlWindow()?.let { focus ->
            if (focus.pkg == name && (expectedWindowId < 0 || focus.id == expectedWindowId)) {
                val reason = SystemScreenGuard.testFocusedWindowReason(name, focus.title)
                if (reason.isNotEmpty()) {
                    cancelControlScan()
                    returnHome(SystemClock.elapsedRealtime(), reason,
                        "SafeNest controls are guarded. Use Stop test in SafeNest Test to end the test.", controlPackage = name)
                    return true
                }
            }
        }
        val root = controlForegroundRoot() ?: return false
        try {
            if (root.packageName?.toString() != name) return false
            // A new Settings activity can report its event while the previous
            // Settings page is still the active root. Package equality alone
            // must not let that older page trigger navigation for this event.
            if (expectedWindowId >= 0 && root.windowId != expectedWindowId) return false
            val screen = readControlLabels(root)
            val reason = if (testControls) SystemScreenGuard.testControlReason(name, screen.labels, screen.detailTitles, screen.actions) else ""
            val now = SystemClock.elapsedRealtime()
            if (reason.isNotEmpty()) {
                cancelControlScan()
                returnHome(now, reason, "SafeNest controls are guarded. Use Stop test in SafeNest Test to end the test.", controlPackage = name)
                return true
            }
            if (BuildConfig.ALLOW_SYSTEM_GUARD && SystemScreenGuard.blocksSafeNestControl(name, screen.labels, screen.actions, screen.checkedToggle)) {
                returnHome(now, "safenest_control", "SafeNest commitment is active until your paid period ends.")
                return true
            }
            if (BuildConfig.ALLOW_SYSTEM_GUARD && GuardPreferences.blocksVpnApps(this) &&
                SystemScreenGuard.blocksVpnInstall(name, screen.titles, screen.actions)) {
                returnHome(now, "vpn_install", "SafeNest blocked this detected VPN installation screen.")
                return true
            }
            return false
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }

    /** A Settings window event can arrive before its Compose/toolbar content is ready. */
    private fun scheduleControlScan(name: String, expectedWindowId: Int) {
        if (pendingControlPackage == name && pendingControlWindowId == expectedWindowId) return
        cancelControlScan()
        pendingControlPackage = name
        pendingControlWindowId = expectedWindowId
        var attempt = 0
        val scan = object : Runnable {
            override fun run() {
                if (pendingControlScan !== this) return
                if (!isConnected || !GuardPreferences.isEnabled(this@SafeNestAccessibilityService) ||
                    (!BuildConfig.ALLOW_SYSTEM_GUARD && !GuardPreferences.testControlsActive(this@SafeNestAccessibilityService))) {
                    cancelControlScan(); return
                }
                if (inspectControlScreen(name, expectedWindowId)) return
                if (++attempt < 3) handler.postDelayed(this, 100) else cancelControlScan()
            }
        }
        pendingControlScan = scan
        handler.postDelayed(scan, 60)
    }

    private fun cancelControlScan() {
        pendingControlScan?.let { handler.removeCallbacks(it) }
        pendingControlScan = null
        pendingControlPackage = null
        pendingControlWindowId = -1
    }

    /** Bounded traversal on system/installer surfaces only; values are never logged or uploaded. */
    private fun readControlLabels(root: AccessibilityNodeInfo): ControlLabels {
        val labels = mutableSetOf<String>(); val actions = mutableSetOf<String>(); val titles = mutableSetOf<String>()
        val detailTitles = mutableSetOf<String>()
        var checkedToggle = false; var visited = 0
        // Settings often puts the label inside a clickable row, rather than on the clickable node.
        data class Entry(val node: AccessibilityNodeInfo, val parentActionable: Boolean, val inToolbar: Boolean)
        fun toolbar(node: AccessibilityNodeInfo) = node.className?.toString()?.endsWith("Toolbar") == true
        val queue = java.util.ArrayDeque<Entry>()
        if (!root.isPassword && !root.isEditable) labels.addAll(listOfNotNull(root.text?.toString(), root.contentDescription?.toString()).filter { it.length in 1..240 })
        for (i in 0 until minOf(root.childCount, 180)) root.getChild(i)?.let { queue.add(Entry(it, root.isEnabled && root.isClickable, toolbar(root))) }
        try {
            while (queue.isNotEmpty() && visited++ < 180) {
                val (node, parentActionable, inToolbar) = queue.removeFirst()
                try {
                    if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue
                    val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                        .filter { it.length in 1..240 }
                    labels.addAll(values)
                    val actionable = node.isEnabled && (node.isClickable || parentActionable)
                    if (actionable) actions.addAll(values)
                    val id = node.viewIdResourceName.orEmpty().lowercase()
                    if (id.contains("title") || id.contains("app_name") || id.contains("headline")) titles.addAll(values)
                    val localId = id.substringAfterLast('/')
                    if (inToolbar || toolbar(node) || (Build.VERSION.SDK_INT >= 28 && node.isHeading) ||
                        localId in setOf("entity_header_title", "app_name", "app_label", "app_title", "action_bar_title", "alerttitle", "header_title")) {
                        detailTitles.addAll(if (inToolbar || toolbar(node)) listOfNotNull(node.text?.toString()).filter { it.length in 1..240 } else values)
                    }
                    if (node.isEnabled && node.isCheckable && node.isChecked) checkedToggle = true
                    for (i in 0 until node.childCount) {
                        if (queue.size >= 180) break
                        node.getChild(i)?.let { queue.add(Entry(it, actionable, inToolbar || toolbar(node))) }
                    }
                } finally { @Suppress("DEPRECATION") node.recycle() }
            }
        } finally { queue.forEach { @Suppress("DEPRECATION") it.node.recycle() } }
        return ControlLabels(labels, actions, titles, detailTitles, checkedToggle)
    }

    private fun returnHome(now: Long, reason: String, message: String, controlPackage: String? = null) {
        if (!GuardPreferences.isEnabled(this)) return
        if (controlPackage != null) {
            // Pop the detail/dialog before Home so Settings restores its unguarded parent next time.
            // The controller coalesces repeated events. A time cooldown here
            // could leave a newly opened protected page unguarded.
            controlExit?.exit(controlPackage) { recordHome(reason, message) }
        } else {
            if (now - lastHomeAction < 1000L) return
            lastHomeAction = now
            finishHome(reason, message)
        }
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
        windowPackages.clear()
        if (connectedService === this) connectedService = null
        handler.removeCallbacks(expiryPoll)
        cancelControlScan()
        controlExit?.cancel()
        // Retain the intended guard selection. If Android revokes Accessibility,
        // the setup status reports the missing permission; re-granting can resume it.
        return super.onUnbind(intent)
    }
    override fun onDestroy() { cancelControlScan(); controlExit?.cancel(); windowPackages.clear(); handler.removeCallbacksAndMessages(null); isConnected = false; if (connectedService === this) connectedService = null; super.onDestroy() }
}
