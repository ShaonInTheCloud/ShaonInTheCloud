package com.safenest.app

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** Runs only on the separate lab app and an empty, disposable emulator. */
class GuardDeviceFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var device: UiDevice
    private lateinit var evidence: File
    private val outcomes = mutableListOf<String>()
    private var networkTestDomain: String? = null

    @Test fun consentedSettingsGuardAndRelease() {
        assertTrue("Device flow must target labDebug", LocalTestSession.enabled)
        // Default UiAutomation would disable the Accessibility service being tested.
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .setWaitForIdleTimeout(300).setWaitForSelectorTimeout(3000)
        device = UiDevice.getInstance(instrumentation)
        evidence = File(context.getExternalFilesDir(null), "guard-qa").apply { mkdirs() }
        try {
            device.pressHome()
            val launcher = device.currentPackageName ?: error("No launcher")
            prepareAndStart()
            pass("Permission-first setup and active connected guard")
            verifyFilteredInternet()
            stopTest()
            openVpnDetails()
            capture("vpn-detail-before-guard")
            tap("Always-on VPN")
            await("Always-on persisted") { alwaysOnPackage() == context.packageName }
            pass("Always-on enabled through Android UI before guarding")
            appInfo(context.packageName)
            assertSettings("Test App info available before starting")
            assertNotNull("Actual uninstall page before guarding", device.wait(Until.findObject(By.text("Uninstall")), 5000))
            capture("test-app-info-before-guard")
            appInfo("com.safenest.app")
            assertSettings("Regular SafeNest App info available before starting")
            assertNotNull("Actual regular uninstall page before guarding", device.wait(Until.findObject(By.text("Uninstall")), 5000))
            capture("regular-app-info-before-guard")
            prepareAndStart()

            // Owner's contract (17 Oct 2026): App info stays open; the TAP on Uninstall / Force stop bounces.
            val removalReasons = setOf("destructive_tap", "test_uninstall_confirm", "test_force_stop_confirm")
            for ((index, pkg) in listOf(context.packageName, "com.safenest.app").withIndex()) {
                if (index > 0) {
                    ownAccessibilityDetail()
                    await("Own Accessibility guard before second removal test") {
                        device.currentPackageName == launcher && GuardPreferences.lastBlockReason() == "test_accessibility"
                    }
                }
                appInfo(pkg)
                assertSettings("$pkg App info stays open while guarded")
                assertNotNull("$pkg App info shows Uninstall while guarded", device.wait(Until.findObject(By.text("Uninstall")), 5000))
                capture(if (pkg == context.packageName) "test-app-info-open-while-guarded" else "regular-app-info-open-while-guarded")
                tap("Uninstall")
                await("$pkg Uninstall tap returns Home") {
                    device.currentPackageName == launcher && GuardPreferences.lastBlockReason() in removalReasons
                }
                capture(if (pkg == context.packageName) "test-uninstall-tap-home" else "regular-uninstall-tap-home")
                pass("$pkg App info open; Uninstall tap returns Home")
                appInfo(pkg)
                assertSettings("$pkg App info open again")
                // Force stop is inert for an app that is not running (the consumer build in CI);
                // only the lab app, which is running the test, has a live button.
                val forceStop = device.wait(Until.findObject(By.text("Force stop")), 5000)
                if (pkg == context.packageName && forceStop != null) {
                    tap("Force stop")
                    await("$pkg Force stop tap returns Home") {
                        device.currentPackageName == launcher && GuardPreferences.lastBlockReason() in removalReasons
                    }
                    pass("$pkg Force stop tap returns Home")
                } else pass("$pkg Force stop is disabled (app not running); nothing to guard")
                device.pressHome()
            }
            appInfo(context.packageName)
            assertSettings("Storage sub-page reachable")
            if (tapVisibleText("Storage & cache") || tapVisibleText("Storage")) {
                assertSettings("Own Storage page stays open while guarded")
                capture("test-storage-open-while-guarded")
                tap("Clear storage")
                await("Clear storage tap returns Home") {
                    device.currentPackageName == launcher && GuardPreferences.lastBlockReason() in setOf("destructive_tap", "test_clear_storage_confirm")
                }
                pass("Own Storage page open; Clear storage tap returns Home")
            }
            start(Intent(Settings.ACTION_SETTINGS))
            assertSettings("Main Settings stays usable after redirect")
            capture("main-settings-allowed")
            pass("Main Settings remains available after redirects")
            start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            assertSettings("Accessibility list stays usable")
            pass("Accessibility services list remains available")
            ownAccessibilityDetail()
            await("Own Accessibility detail returns Home") {
                device.currentPackageName == launcher && GuardPreferences.lastBlockReason() == "test_accessibility"
            }
            pass("Own Accessibility detail returns Home")
            openVpnDetails()
            assertSettings("Own VPN detail page stays open while guarded")
            assertNotNull("Always-on row visible on own VPN detail", device.wait(Until.findObject(By.text("Always-on VPN")), 5000))
            capture("vpn-detail-open-while-guarded")
            // Android hides the Always-on switch from non-accessibility-tool services, so the
            // guard reacts to the consequence instead: Android drops the VPN the moment the switch
            // goes off, and that revoke sends the screen Home and raises the restore flow.
            tap("Always-on VPN")
            await("Switching Always-on off sends the screen Home") {
                device.currentPackageName == launcher && GuardPreferences.lastBlockReason() in setOf("vpn_revoked", "always_on_restored")
            }
            capture("vpn-always-on-off-home")
            pass("Always-on switched off: screen sent Home, protection marked interrupted")
            // Restore: SafeNest re-requests its VPN slot as soon as it is in front.
            openApp()
            if (android.net.VpnService.prepare(context) != null) tap("OK")
            await("Protection restored after the Always-on interruption") { SafeNestVpnService.isRunning.get() }
            pass("Protection restored through SafeNest's own prompt")
            openVpnDetails()
            assertSettings("Own VPN detail open again")
            if (alwaysOnPackage() != context.packageName) {
                tap("Always-on VPN")
                await("Always-on re-enabled through Android UI") { alwaysOnPackage() == context.packageName }
            }
            assertSettings("Still on own VPN detail after re-enabling Always-on")
            tap("Forget VPN")
            await("Forget VPN tap returns Home") {
                device.currentPackageName == launcher && GuardPreferences.lastBlockReason() in setOf("destructive_tap", "test_forget_vpn_confirm")
            }
            assertEquals("Always-on must remain enabled", context.packageName, alwaysOnPackage())
            capture("vpn-forget-tap-home")
            pass("Forget VPN tap returns Home and Always-on stays enabled")
            start(Intent(Settings.ACTION_VPN_SETTINGS))
            assertSettings("VPN list stays available")
            assertNotNull(device.wait(Until.findObject(By.text("SafeNest Test")), 5000))
            capture("vpn-list-allowed")
            pass("VPN list remains available after redirect")
            appInfo("com.android.settings")
            assertSettings("Unrelated App info stays available")
            pass("Unrelated app settings remain available")

            // 0.5.11+ app blocking, with stand-in apps CI installs (android/teststubs). Skipped if absent.
            val stubs = listOf(
                Triple("com.opera.browser", "Opera (browser with built-in VPN)", setOf("vpn_browser")),
                Triple("com.krikya.krikya", "Krikya (gambling app by package ID)", setOf("app")),
                Triple("com.example.luckycasino", "Lucky Casino Slots (gambling word in the name)", setOf("app")),
                Triple("com.example.stubvpn", "Stub VPN (detected VPN app)", setOf("app")))
            for ((pkg, label, reasons) in stubs) {
                if (!installed(pkg)) { outcomes.add("SKIP $label: stand-in not installed"); continue }
                device.pressHome()
                openStub(pkg)
                await("$label returns Home") {
                    device.currentPackageName == launcher && GuardPreferences.lastBlockReason() in reasons
                }
                capture("blocked-${pkg.substringAfterLast('.')}")
                pass("$label opened and returned Home")
            }
            if (installed("com.example.notes")) {
                device.pressHome()
                openStub("com.example.notes")
                SystemClock.sleep(2500)
                assertEquals("Ordinary app must stay open", "com.example.notes", device.currentPackageName)
                capture("ordinary-app-open")
                pass("Ordinary app (Notes) stays open with protection on")
                device.pressHome()
            }

            stopTest()
            appInfo(context.packageName)
            assertSettings("Stop test releases uninstall page")
            assertNotNull("Uninstall control released after Stop test", device.wait(Until.findObject(By.text("Uninstall")), 5000))
            capture("uninstall-page-released")
            pass("Stop test releases uninstall/force-stop page")
            ownAccessibilityDetail()
            assertSettings("Stop test releases own Accessibility detail")
            pass("Stop test releases own Accessibility detail")
            openVpnDetails()
            assertSettings("Stop test releases VPN detail")
            assertNotNull(device.wait(Until.findObject(By.text("Always-on VPN")), 5000))
            tap("Always-on VPN")
            await("Always-on can be unchecked after stopping") { alwaysOnPackage().isNullOrBlank() }
            capture("vpn-detail-released")
            pass("Stop test releases Always-on toggle")
            File(evidence, "result.txt").writeText("PASS SafeNest Test ${BuildConfig.VERSION_NAME}\n" + outcomes.joinToString("\n") + "\n")
            preserveEvidence()
        } catch (failure: Throwable) {
            capture("failure")
            val state = "Guard ready: ${GuardPreferences.testControlGuardReady(context)}; " +
                "last action: ${GuardPreferences.lastBlockReason()}; exit: ${GuardPreferences.lastControlExitOutcome()}"
            var windows = ""
            instrumentation.runOnMainSync { windows = SafeNestAccessibilityService.windowStatusForQa() }
            val automationWindows = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).windows
            try {
                windows += "; automation=" + automationWindows.take(12).joinToString("|") { window ->
                    val root = window.root
                    try { "${window.id},type=${window.type},focus=${window.isFocused},active=${window.isActive},pkg=${root?.packageName}" }
                    finally { @Suppress("DEPRECATION") root?.recycle() }
                }
            } finally { automationWindows.forEach { @Suppress("DEPRECATION") it.recycle() } }
            File(evidence, "result.txt").writeText("FAIL SafeNest Test ${BuildConfig.VERSION_NAME}\n" + outcomes.joinToString("\n") + "\n" + state + "\n" + windows + "\n" + failure.stackTraceToString())
            File(evidence, "accessibility-before-teardown.txt").writeText(device.executeShellCommand("dumpsys accessibility"))
            File(evidence, "activities-before-teardown.txt").writeText(device.executeShellCommand("dumpsys activity activities"))
            preserveEvidence()
            val hierarchy = java.io.ByteArrayOutputStream()
            try { device.dumpWindowHierarchy(hierarchy); println("SafeNest QA failure UI: " + hierarchy.toString("UTF-8")) }
            catch (_: Exception) { }
            throw failure
        } finally {
            LocalTestSession.stop(context)
            networkTestDomain?.let { RulesStore.remove(context, RuleCategory.PERSONAL, it) }
        }
    }

    private fun prepareAndStart() {
        openApp()
        assertTrue("App startup must finish before setup", device.wait(Until.hasObject(By.text("Start test")), 30_000))
        tap("Start test", scroll = true)
        device.findObject(By.text("OK"))?.let { tapVisibleText("OK") }
        tap("Review and enable test guard", scroll = true)
        tap("I agree — open settings")
        if (!GuardPreferences.isAccessibilityEnabled(context)) {
            ownAccessibilityDetail()
            tap("Use SafeNest Test app guard")
            val allow = device.wait(Until.findObject(By.text(Pattern.compile("(?i)^allow$"))), 5000)
                ?: error("Accessibility permission confirmation missing")
            allow.click()
        }
        await("Accessibility enabled and connected") {
            GuardPreferences.isAccessibilityEnabled(context) && GuardPreferences.isServiceConnected()
        }
        openApp()
        tap("Start test", scroll = true)
        tap("I agree — start protection")
        if (android.net.VpnService.prepare(context) != null) tap("OK")
        await("Active guard and VPN") {
            GuardPreferences.testControlGuardReady(context) && SafeNestVpnService.isRunning.get()
        }
        capture("guard-active-${outcomes.size}")
    }

    private fun verifyFilteredInternet() {
        val blocked = "qa-${SystemClock.elapsedRealtime()}.example.com"
        networkTestDomain = blocked
        RulesStore.addAll(context, RuleCategory.PERSONAL, listOf(blocked))
        try {
            java.net.InetAddress.getAllByName(blocked)
            fail("Listed harmless name must fail DNS")
        } catch (_: java.net.UnknownHostException) { }
        await("Listed name reached the local filter") { SafeNestVpnService.lastBlockedHost.get() == blocked }
        pass("Listed harmless domain failed through the active DNS filter")
        assertTrue("Allowed name must resolve", java.net.InetAddress.getAllByName("example.com").isNotEmpty())
        await("Allowed lookup used encrypted DNS") {
            SafeNestVpnService.dnsHealth.get() == "ok" && SafeNestVpnService.dnsTransport.get() == "cloudflare-https"
        }
        pass("Allowed DNS resolved over certificate-verified HTTPS")
        val connection = java.net.URL("https://example.com/").openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            assertEquals("Ordinary HTTPS must remain usable", 200, connection.responseCode)
            connection.inputStream.use { assertTrue("Actual page body received", it.read() >= 0) }
        } finally { connection.disconnect() }
        pass("Ordinary HTTPS browsing stayed available with protection on")

    }

    private fun stopTest() {
        openApp()
        tap("Stop test", scroll = true)
        await("Test stopped") { !LocalTestSession.isActive(context) && !GuardPreferences.testControlGuardReady(context) }
    }

    private fun ownAccessibilityDetail() {
        // Android 16 reserves the details intent for privileged callers. Follow
        // the same public Accessibility list and service row as the owner.
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        val service = device.wait(Until.findObject(By.text("SafeNest Test app guard")), 3000)
        if (service != null) {
            service.click()
            return
        }
        capture("accessibility-list-navigation")
        val apps = device.wait(Until.findObject(By.text(Pattern.compile(
            "(?i)^(downloaded apps|installed apps|downloaded services|installed services)$"))), 5000)
            ?: error("Accessibility downloaded-apps row missing")
        apps.click()
        tap("SafeNest Test app guard", scroll = true)
    }

    private fun installed(pkg: String) = try { context.packageManager.getApplicationInfo(pkg, 0); true } catch (_: Exception) { false }
    private fun openStub(pkg: String) {
        val output = device.executeShellCommand("am start -W -n $pkg/com.safenest.teststub.StubActivity")
        check(!output.contains("Error:")) { "Stand-in launch failed: $output" }
        SystemClock.sleep(300)
    }
    private fun appInfo(pkg: String) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    private fun openApp() {
        // Permission Settings can sit above MainActivity in the app's task.
        // Clear those activities so bringing the task forward really opens
        // SafeNest, rather than restoring its last external Settings page.
        start(Intent().setComponent(ComponentName(context.packageName, MainActivity::class.java.name))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        await("SafeNest Test foreground after reopening") { device.currentPackageName == context.packageName }
    }
    private fun start(intent: Intent) {
        // The app is in the background while Settings is open. Use the QA
        // shell to launch only these public activities, as an owner would from
        // the launcher; a blocked background start must not masquerade as Home.
        val command = buildString {
            // Reach the requested public page, not a detail page sitting above
            // a previously opened Settings activity in its existing task.
            append("am start -W -f 0x").append(Integer.toHexString(
                intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            intent.component?.let { append(" -n ").append(commandToken(it.flattenToString())) }
            intent.action?.let { append(" -a ").append(commandToken(it)) }
            intent.data?.let { append(" -d ").append(commandToken(it.toString())) }
        }
        val output = device.executeShellCommand(command)
        check(!output.contains("Error:") && !output.contains("Exception")) { "Public activity launch failed: $output" }
        println("SafeNest device QA launch: $output")
        SystemClock.sleep(500)
    }

    private fun commandToken(value: String): String {
        // UiAutomation tokenizes this command without shell quote processing.
        // Only our fixed component/action/package URI tokens are permitted.
        check(value.matches(Regex("[A-Za-z0-9._:/-]+"))) { "Unexpected QA activity token" }
        return value
    }

    private fun openVpnDetails() {
        start(Intent(Settings.ACTION_VPN_SETTINGS))
        val label = device.wait(Until.findObject(By.text("SafeNest Test")), 5000) ?: error("SafeNest Test VPN row missing")
        var row: UiObject2? = label.parent
        repeat(5) {
            val current = row ?: error("VPN gear missing")
            val gear = current.findObject(By.res("com.android.settings", "settings_button"))
                ?: current.findObject(By.desc(Pattern.compile("(?i).*settings.*")))
            if (gear != null) { gear.click(); return }
            row = current.parent
        }
        error("VPN gear missing; refusing to guess coordinates")
    }

    private fun tap(text: String, scroll: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + if (scroll) 5_000 else 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (tapVisibleText(text)) return
            SystemClock.sleep(200)
        }
        if (scroll) {
            // Two full passes: top-to-bottom, then back up, settling after each swipe so a
            // Compose page that is still laying out does not hide the button between frames.
            repeat(2) {
                repeat(12) { swipePage(towardStart = true); SystemClock.sleep(250); if (tapVisibleText(text)) return }
                repeat(24) { swipePage(towardStart = false); SystemClock.sleep(250); if (tapVisibleText(text)) return }
            }
        }
        error("Missing visible button: $text")
    }

    private fun swipePage(towardStart: Boolean) {
        // Compose can omit the scroll event UiObject2.scroll waits for. Use
        // fresh tree-derived visible bounds for a normal owner swipe instead.
        val panel = device.findObject(By.scrollable(true)) ?: return
        val bounds = try { panel.visibleBounds } catch (_: StaleObjectException) { return }
        if (bounds.width() < 16 || bounds.height() < 80) return
        val upper = bounds.top + bounds.height() / 4
        val lower = bounds.bottom - bounds.height() / 4
        device.swipe(bounds.centerX(), if (towardStart) upper else lower,
            bounds.centerX(), if (towardStart) lower else upper, 35)
        SystemClock.sleep(450)
    }

    private fun tapVisibleText(text: String): Boolean {
        repeat(3) {
            val node = device.findObject(By.text(text)) ?: return false
            try {
                // Read fresh visible bounds after scrolling. UiObject2.click
                // may retain a Compose node that is replaced during layout.
                val bounds = node.visibleBounds
                if (bounds.width() >= 8 && bounds.height() >= 12)
                    return device.click(bounds.centerX(), bounds.centerY())
            } catch (_: StaleObjectException) { }
            SystemClock.sleep(150)
        }
        return false
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(150)
        }
        error("Timed out: $description; foreground=${device.currentPackageName}")
    }

    private fun assertSettings(description: String) {
        await(description) { device.currentPackageName == "com.android.settings" }
        SystemClock.sleep(1200)
        assertEquals(description, "com.android.settings", device.currentPackageName)
    }

    private fun alwaysOnPackage(): String? = device.executeShellCommand("settings get secure always_on_vpn_app").trim().takeUnless { it == "null" || it.isEmpty() }
    private fun pass(message: String) { outcomes.add("PASS $message"); println("SafeNest device QA: $message") }
    private fun capture(name: String) {
        try {
            device.takeScreenshot(File(evidence, "$name.png"))
            device.dumpWindowHierarchy(File(evidence, "$name.xml"))
        } catch (_: Exception) { /* Keep the original failure if evidence capture fails. */ }
    }

    private fun preserveEvidence() {
        // Gradle uninstalls the test app after execution; keep disposable-device
        // evidence outside its app directory before that teardown.
        device.executeShellCommand("mkdir -p /sdcard/Download/safenest-guard-qa")
        device.executeShellCommand("cp -R /sdcard/Android/data/com.safenest.app.lab/files/guard-qa/. /sdcard/Download/safenest-guard-qa/")
    }
}
