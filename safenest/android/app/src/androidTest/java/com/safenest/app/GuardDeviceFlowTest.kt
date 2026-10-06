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
import androidx.test.uiautomator.Direction
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
            stopTest()
            openVpnDetails()
            capture("vpn-detail-before-guard")
            tap("Always-on VPN")
            await("Always-on persisted") { alwaysOnPackage() == context.packageName }
            pass("Always-on enabled through Android UI before guarding")
            appInfo(context.packageName)
            assertSettings("Test App info available before starting")
            capture("test-app-info-before-guard")
            appInfo("com.safenest.app")
            assertSettings("Regular SafeNest App info available before starting")
            capture("regular-app-info-before-guard")
            prepareAndStart()

            for ((index, pkg) in listOf(context.packageName, "com.safenest.app").withIndex()) {
                if (index > 0) {
                    ownAccessibilityDetail()
                    await("Own Accessibility guard before second removal test") {
                        device.currentPackageName == launcher && GuardPreferences.lastBlockReason() == "test_accessibility"
                    }
                }
                appInfo(pkg)
                await("$pkg App info returns Home") {
                    device.currentPackageName == launcher && GuardPreferences.lastBlockReason() == "test_app_control"
                }
                capture(if (pkg == context.packageName) "test-uninstall-guard-home" else "regular-uninstall-guard-home")
                pass("$pkg uninstall/force-stop page returns Home")
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
            await("VPN gear returns Home") {
                device.currentPackageName == launcher && GuardPreferences.lastBlockReason() == "test_vpn_detail"
            }
            assertEquals("Always-on must remain enabled", context.packageName, alwaysOnPackage())
            capture("vpn-guard-home")
            pass("VPN gear/detail returns Home and Always-on stays enabled")
            start(Intent(Settings.ACTION_VPN_SETTINGS))
            assertSettings("VPN list stays available")
            assertNotNull(device.wait(Until.findObject(By.text("SafeNest Test")), 5000))
            capture("vpn-list-allowed")
            pass("VPN list remains available after redirect")
            appInfo("com.android.settings")
            assertSettings("Unrelated App info stays available")
            pass("Unrelated app settings remain available")

            stopTest()
            appInfo(context.packageName)
            assertSettings("Stop test releases uninstall page")
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
            File(evidence, "result.txt").writeText("FAIL SafeNest Test ${BuildConfig.VERSION_NAME}\n" + outcomes.joinToString("\n") + "\n" + failure.stackTraceToString())
            preserveEvidence()
            val hierarchy = java.io.ByteArrayOutputStream()
            try { device.dumpWindowHierarchy(hierarchy); println("SafeNest QA failure UI: " + hierarchy.toString("UTF-8")) }
            catch (_: Exception) { }
            throw failure
        } finally {
            LocalTestSession.stop(context)
        }
    }

    private fun prepareAndStart() {
        openApp()
        assertTrue("App startup must finish before setup", device.wait(Until.hasObject(By.text("Start test")), 30_000))
        tap("Start test", scroll = true)
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

    private fun appInfo(pkg: String) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    private fun openApp() = start(Intent().setComponent(ComponentName(context.packageName, MainActivity::class.java.name)))
    private fun start(intent: Intent) {
        // The app is in the background while Settings is open. Use the QA
        // shell to launch only these public activities, as an owner would from
        // the launcher; a blocked background start must not masquerade as Home.
        val command = buildString {
            append("am start -W -f 0x10000000")
            intent.component?.let { append(" -n ").append(commandToken(it.flattenToString())) }
            intent.action?.let { append(" -a ").append(commandToken(it)) }
            intent.data?.let { append(" -d ").append(commandToken(it.toString())) }
        }
        val output = device.executeShellCommand(command)
        check(!output.contains("Error:") && !output.contains("Exception")) { "Public activity launch failed: $output" }
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
        var node = device.wait(Until.findObject(By.text(text)), 15_000)
        if (node == null && scroll) {
            repeat(6) { device.findObject(By.scrollable(true))?.scroll(Direction.UP, 0.85f) }
            repeat(12) {
                node = device.findObject(By.text(text))
                if (node != null) { node!!.click(); return }
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.65f)
            }
        }
        (node ?: error("Missing visible button: $text")).click()
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
