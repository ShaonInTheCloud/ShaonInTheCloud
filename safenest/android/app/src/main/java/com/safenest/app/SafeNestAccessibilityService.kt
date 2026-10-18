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
        /**
         * Called by the VPN service when Android revokes SafeNest's VPN during committed
         * protection (Always-on switched off in Settings, or another VPN taking over).
         * Android hides the Always-on switch itself from non-accessibility-tool services on
         * recent versions, so the consequence is guarded instead of the control: leave the
         * screen for Home at once. The restore notification and SafeNest's own prompt follow.
         */
        fun protectionRevoked(reason: String) {
            val service = connectedService ?: return
            service.handler.post {
                if (!isConnected || !GuardPreferences.isEnabled(service)) return@post
                val testControls = GuardPreferences.testControlsActive(service)
                if (!BuildConfig.ALLOW_SYSTEM_GUARD && !testControls) return@post
                service.hardExit(SystemClock.elapsedRealtime(), reason,
                    "SafeNest protection was switched off. Open SafeNest to restore it.")
            }
        }
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
        // Warm the 541k-name blocklist off the main thread so the first app/address check does not stall.
        Thread({ try { BundledGamblingRules.load(applicationContext) } catch (_: Exception) {} }, "SafeNest-blocklist-warmup").start()
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
            // A window disappears from the interactive list when its task is
            // hidden, then can return with the same unique ID and no new state
            // event. Keep bounded ownership until eviction or service unbind.
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
        if (BuildConfig.DEBUG && SystemScreenGuard.isSystemSurface(event.packageName?.toString().orEmpty()))
            qa("event type=${event.eventType} pkg=${event.packageName} win=${event.windowId} cls=${event.className}")
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) { onControlTapped(event); return }
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
        // Browsers with a built-in VPN/proxy (Opera, Aloha, Tor, Puffin...) bypass the on-device filter: block whole.
        if (GuardPreferences.blocksVpnApps(this) && BlockedAppCatalog.isVpnBrowser(this, name)) {
            val root = rootInActiveWindow ?: return
            try {
                if (root.packageName?.toString() == name) returnHome(now, "vpn_browser", "SafeNest blocks browsers with a built-in VPN.")
            } finally { @Suppress("DEPRECATION") root.recycle() }
            return
        }
        val addressBars = AppBlockRules.addressBarIds(name)
        if (addressBars != null) {
            // The address bar shows the real site even when the browser's own VPN (e.g. Edge Secure Network)
            // tunnels the traffic past the DNS filter. In Play, only the DNS filter applies.
            if (!BuildConfig.ALLOW_SYSTEM_GUARD && !GuardPreferences.testControlsActive(this)) return
            val root = rootInActiveWindow ?: return
            try {
                if (root.packageName?.toString() != name) return
                // Address-bar resource IDs only: never traverse browser page content.
                for (id in addressBars) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("$name:id/$id")
                    try {
                        val blocked = nodes.any { node ->
                            !node.isPassword && GuardRules.hostFromAddressBar(node.text?.toString().orEmpty())
                                ?.let { RulesStore.isBlocked(this, it) } == true
                        }
                        if (blocked) { leaveSite(now, "website", "SafeNest blocked this website."); return }
                        // A site opened by public IP address never asks DNS; the router's 192.168.x.x page stays usable.
                        if (nodes.any { !it.isPassword && IpRules.isPublicIpAddressBar(it.text?.toString()) }) {
                            leaveSite(now, "ip_address", "SafeNest blocks websites opened by IP address."); return
                        }
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
            val root = focus.root
            val pkg = try {
                root?.packageName?.toString()?.also { windowPackages.observe(focus.id, it) }
                    ?: windowPackages.owner(focus.id)
            } finally { @Suppress("DEPRECATION") root?.recycle() }
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

    // SafeNest's own Settings page (App info, Storage, VPN detail) was on screen recently.
    // Confirmation dialogs such as "Force stop?" do not repeat the app name, so this is
    // how they are attributed. Cleared as soon as another app's detail page is seen.
    private var lastOwnPageAt = 0L
    private fun recentlyOnOwnPage(now: Long) = lastOwnPageAt != 0L && now - lastOwnPageAt < 20_000L

    /** A tap on Uninstall / Force stop / Clear storage / Forget VPN on SafeNest's own page bounces at once. */
    private fun onControlTapped(event: AccessibilityEvent) {
        val name = event.packageName?.toString() ?: return
        if (name == packageName || !SystemScreenGuard.isSystemSurface(name)) return
        val testControls = GuardPreferences.testControlsActive(this)
        if (!BuildConfig.ALLOW_SYSTEM_GUARD && !testControls) return
        val now = SystemClock.elapsedRealtime()
        val texts = mutableListOf<String>()
        event.text?.forEach { it?.toString()?.takeIf { t -> t.length in 1..120 }?.let(texts::add) }
        event.contentDescription?.toString()?.takeIf { it.length in 1..120 }?.let(texts::add)
        val source = try { event.source } catch (_: Exception) { null }
        if (source != null) {
            try {
                // The tapped row plus its direct labels only; never the whole page.
                listOfNotNull(source.text?.toString(), source.contentDescription?.toString()).forEach(texts::add)
                for (i in 0 until minOf(source.childCount, 6)) source.getChild(i)?.let { child ->
                    try { listOfNotNull(child.text?.toString(), child.contentDescription?.toString()).forEach(texts::add) }
                    finally { @Suppress("DEPRECATION") child.recycle() }
                }
            } finally { @Suppress("DEPRECATION") source.recycle() }
        }
        val destructive = texts.any { SystemScreenGuard.isDestructiveControl(it) }
        val alwaysOnRow = texts.any { it.trim().equals("Always-on VPN", ignoreCase = true) || it.trim() == "সবসময় চালু VPN" }
        qa("tap pkg=$name texts=${texts.take(4)} destructive=$destructive alwaysOn=$alwaysOnRow recentOwn=${recentlyOnOwnPage(now)}")
        if (!destructive && !alwaysOnRow) return
        // Attribute the tap to SafeNest: the page was ours moments ago, or the live window says so.
        var own = recentlyOnOwnPage(now)
        if (!own) {
            val root = controlForegroundRoot()
            if (root != null) try {
                if (root.packageName?.toString() == name) {
                    val screen = readControlLabels(root)
                    own = SystemScreenGuard.hasOwnIdentity(screen.labels) &&
                        !SystemScreenGuard.isForeignDetailPage(name, screen.labels, screen.detailTitles)
                    if (own) lastOwnPageAt = now
                }
            } finally { @Suppress("DEPRECATION") root.recycle() }
        }
        if (!own) return
        if (alwaysOnRow && !destructive) {
            // The switch has just flipped; re-read the page shortly and put it back on if it is off.
            handler.postDelayed({ val root = controlForegroundRoot() ?: return@postDelayed
                try { if (root.packageName?.toString() == name) ensureAlwaysOn(root, SystemClock.elapsedRealtime(), force = true) }
                finally { @Suppress("DEPRECATION") root.recycle() } }, 350)
            return
        }
        cancelControlScan()
        hardExit(now, "destructive_tap", if (LocalTestSession.enabled)
            "SafeNest controls are guarded. Use Stop test in SafeNest Test to end the test."
            else "SafeNest commitment is active until your paid period ends.")
    }

    private fun inspectControlScreen(name: String, expectedWindowId: Int = -1): Boolean {
        if (!isConnected || !GuardPreferences.isEnabled(this)) return false
        val testControls = GuardPreferences.testControlsActive(this)
        if (!BuildConfig.ALLOW_SYSTEM_GUARD && !testControls) return false
        // The Settings window title names the selected app on pages whose content does not
        // (the VPN detail page shows it only in a collapsing toolbar). Use it as identity.
        var windowTitle: String? = null
        if (testControls) focusedControlWindow()?.let { focus ->
            if (focus.pkg == name) windowTitle = focus.title
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
        val root = controlForegroundRoot()
        if (root == null) { qa("inspect pkg=$name win=$expectedWindowId root=null active=${rootInActiveWindow?.packageName}"); return false }
        try {
            if (root.packageName?.toString() != name) { qa("inspect pkg=$name root belongs to ${root.packageName}"); return false }
            // A new Settings activity can report its event while the previous
            // Settings page is still the active root. Package equality alone
            // must not let that older page trigger navigation for this event.
            if (expectedWindowId >= 0 && root.windowId != expectedWindowId) { qa("inspect pkg=$name win=$expectedWindowId rootWin=${root.windowId} mismatch"); return false }
            val read = readControlLabels(root)
            if (windowTitle == null && !testControls) windowTitle = root.window?.title?.toString()?.takeIf { it.length <= 240 }
            val screen = if (windowTitle != null) read.copy(labels = read.labels + windowTitle!!, detailTitles = read.detailTitles + windowTitle!!) else read
            val now = SystemClock.elapsedRealtime()
            val guardActive = BuildConfig.ALLOW_SYSTEM_GUARD || testControls
            qa("inspect pkg=$name title=$windowTitle own=${SystemScreenGuard.hasOwnIdentity(screen.labels)} vpnDetail=${SystemScreenGuard.isOwnVpnDetail(name, screen.labels)} labels=${screen.labels.size}")
            if (SystemScreenGuard.isForeignDetailPage(name, screen.labels, screen.detailTitles)) lastOwnPageAt = 0L
            else if ((SystemScreenGuard.isSettingsPackage(name) || SystemScreenGuard.isInstallerPackage(name)) &&
                SystemScreenGuard.hasOwnIdentity(screen.labels)) lastOwnPageAt = now
            // Another VPN can only start after Android's "Connection request" prompt. Refusing
            // that prompt for every app except SafeNest closes the simplest bypass.
            if (guardActive && GuardPreferences.blocksVpnApps(this) &&
                SystemScreenGuard.blocksForeignVpnConsent(name, screen.labels)) {
                cancelControlScan()
                hardExit(now, "vpn_consent", "SafeNest blocked another VPN from taking over the connection.")
                return true
            }
            // SafeNest's own VPN page: open while Always-on is still off, so it can be switched
            // on. Once Always-on is SafeNest, the page itself goes Home before the switch (or
            // Forget VPN) can be touched, so it can be turned on but never off.
            if (guardActive && ownVpnDetailOnScreen(name, root, screen) &&
                !SystemScreenGuard.isForgetConfirmation(screen.labels, screen.actions)) {
                qa("ownVpnDetail alwaysOnIsSafeNest=${alwaysOnIsSafeNest()} labels=${screen.labels}")
                if (alwaysOnIsSafeNest()) {
                    cancelControlScan()
                    returnHome(now, "always_on_guarded", "Always-on VPN stays on while SafeNest protection is active.", controlPackage = name)
                    return true
                }
                ensureAlwaysOn(root, now)
            }
            val reason = if (testControls) SystemScreenGuard.testControlReason(name, screen.labels, screen.detailTitles, screen.actions, recentlyOnOwnPage(now)) else ""
            if (reason.isNotEmpty()) {
                cancelControlScan()
                val message = "SafeNest controls are guarded. Use Stop test in SafeNest Test to end the test."
                if (reason.endsWith("_confirm")) hardExit(now, reason, message)
                else returnHome(now, reason, message, controlPackage = name)
                return true
            }
            if (BuildConfig.ALLOW_SYSTEM_GUARD && SystemScreenGuard.blocksSafeNestControl(name, screen.labels, screen.actions, screen.checkedToggle, recentlyOnOwnPage(now))) {
                hardExit(now, "safenest_control", "SafeNest commitment is active until your paid period ends.")
                return true
            }
            if (guardActive && GuardPreferences.blocksVpnApps(this) &&
                SystemScreenGuard.blocksVpnInstall(name, screen.titles, screen.actions)) {
                returnHome(now, "vpn_install", "SafeNest blocked this detected VPN installation screen.")
                return true
            }
            if (guardActive && (name == "com.android.vending" || SystemScreenGuard.isSystemSurface(name)) &&
                BlockedAppCatalog.blocksInstall(this, screen.titles, screen.actions)) {
                returnHome(now, "app_install", "SafeNest blocked installing a gambling app or VPN browser.")
                return true
            }
            return false
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }

    private var lastAlwaysOnClick = 0L
    private var alwaysOnAttempt = 0
    /**
     * SafeNest's own VPN detail page. Settings hides the Always-on row's text from the label
     * walk on some builds, so fall back to the page heading plus a text search for the row.
     */
    private fun ownVpnDetailOnScreen(name: String, root: AccessibilityNodeInfo, screen: ControlLabels): Boolean {
        if (SystemScreenGuard.isOwnVpnDetail(name, screen.labels)) return true
        if (!SystemScreenGuard.isSettingsPackage(name) || !SystemScreenGuard.hasOwnIdentity(screen.detailTitles)) return false
        for (text in listOf("Always-on VPN", "সবসময় চালু VPN", "Forget VPN", "Block connections without VPN")) {
            val found = root.findAccessibilityNodeInfosByText(text) ?: continue
            try { if (found.isNotEmpty()) return true } finally { found.forEach { @Suppress("DEPRECATION") it.recycle() } }
        }
        return false
    }
    /**
     * Always-on is SafeNest: the running VPN service reports VpnService.isAlwaysOn (the Secure
     * setting is not readable by apps since Android 12). A fresh sample is taken on each visit.
     */
    private fun alwaysOnIsSafeNest(): Boolean = SafeNestVpnService.isRunning.get() &&
        (SafeNestVpnService.sampleAlwaysOn() ?: SafeNestVpnService.alwaysOnEnabled.get())
    private fun qa(message: String) { if (BuildConfig.DEBUG) android.util.Log.i("SafeNestGuard", message) }
    /**
     * On SafeNest's own VPN page: turn Always-on VPN back on when it is found off. Android
     * disconnects the VPN the moment Always-on is switched off, so this must succeed; it
     * alternates between clicking the row and the switch and verifies after each attempt.
     */
    private fun ensureAlwaysOn(root: AccessibilityNodeInfo, now: Long, force: Boolean = false) {
        if (!force && now - lastAlwaysOnClick < 1500L) return
        val rows = (root.findAccessibilityNodeInfosByText("Always-on VPN") ?: emptyList()) +
            (root.findAccessibilityNodeInfosByText("সবসময় চালু VPN") ?: emptyList())
        qa("ensureAlwaysOn rows=${rows.size} force=$force attempt=$alwaysOnAttempt")
        if (rows.isEmpty()) return
        try {
            for (label in rows) {
                if (!label.isVisibleToUser) continue
                var row: AccessibilityNodeInfo? = label.parent
                var hops = 0
                while (row != null && hops++ < 4) {
                    val toggle = findToggle(row, 0)
                    if (toggle != null) {
                        try {
                            qa("ensureAlwaysOn toggle checked=${toggle.isChecked} enabled=${toggle.isEnabled} clickable=${toggle.isClickable} rowClickable=${row.isClickable}")
                            if (toggle.isChecked) { alwaysOnAttempt = 0; return }
                            if (!toggle.isEnabled) return
                            // Alternate targets across attempts: the row handles the click on most
                            // Settings builds; a few route it through the switch itself.
                            val preferSwitch = alwaysOnAttempt % 2 == 1
                            val first = if (preferSwitch) toggle else row
                            val second = if (preferSwitch) row else toggle
                            val clicked = first.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                                second.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            qa("ensureAlwaysOn click sent=$clicked preferSwitch=$preferSwitch")
                            lastAlwaysOnClick = now
                            alwaysOnAttempt++
                            if (clicked) recordHome("always_on_restored", "SafeNest keeps Always-on VPN switched on during protection.")
                            // Verify shortly; retry with the other target if the switch is still off.
                            if (alwaysOnAttempt < 6) handler.postDelayed({
                                val again = controlForegroundRoot() ?: return@postDelayed
                                try { if (SystemScreenGuard.isSettingsPackage(again.packageName?.toString().orEmpty())) ensureAlwaysOn(again, SystemClock.elapsedRealtime(), force = true) }
                                finally { @Suppress("DEPRECATION") again.recycle() }
                            }, 700)
                        } finally { @Suppress("DEPRECATION") toggle.recycle() }
                        return
                    }
                    val next = row.parent
                    @Suppress("DEPRECATION") row.recycle()
                    row = next
                }
                @Suppress("DEPRECATION") row?.recycle()
            }
            qa("ensureAlwaysOn: no switch found near the label")
        } finally { rows.forEach { @Suppress("DEPRECATION") it.recycle() } }
    }
    private fun findToggle(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 3) return null
        for (i in 0 until minOf(node.childCount, 12)) {
            val child = node.getChild(i) ?: continue
            if (child.isCheckable) return child
            val nested = findToggle(child, depth + 1)
            @Suppress("DEPRECATION") child.recycle()
            if (nested != null) return nested
        }
        return null
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

    private var lastSiteExit = 0L
    /**
     * A blocked website in an ordinary browser: the browser stays open and steps back to the
     * previous page (the DNS filter already keeps the site from loading). Only destructive
     * actions, gambling apps and VPN apps/browsers are sent Home.
     */
    private fun leaveSite(now: Long, reason: String, message: String) {
        if (!GuardPreferences.isEnabled(this)) return
        if (now - lastSiteExit < 1000L) return
        lastSiteExit = now
        if (performGlobalAction(GLOBAL_ACTION_BACK)) {
            lastBlockReason = reason
            if (now - lastToast > 3000L) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); lastToast = now }
        }
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

    private var lastHardExit = 0L
    /**
     * A destructive action (Uninstall / Force stop / Clear storage / Forget VPN / foreign VPN
     * consent) always ends on Home: close whatever dialog opened, then go Home, regardless of
     * which package ends up in front in between.
     */
    private fun hardExit(now: Long, reason: String, message: String) {
        if (!GuardPreferences.isEnabled(this)) return
        if (now - lastHardExit < 700L) return
        lastHardExit = now
        controlExit?.cancel()
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            if (!isConnected || !GuardPreferences.isEnabled(this)) return@postDelayed
            if (performGlobalAction(GLOBAL_ACTION_HOME)) recordHome(reason, message)
            // No second Home: it would also eject whatever the user opens next. A dialog that
            // appears late is caught by the confirmation scan and gets its own exit.
        }, 250)
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
