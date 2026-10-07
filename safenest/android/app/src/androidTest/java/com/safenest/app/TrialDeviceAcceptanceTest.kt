package com.safenest.app

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.app.UiAutomation
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** Compatible-build UI gates and isolated cached-window cleanup only.
 * No live account, entitlement or network fixture is created. These tests cannot
 * by themselves satisfy live login/start/protection/72-hour acceptance.
 */
class TrialDeviceAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun prefs() = context.getSharedPreferences("safenest_paid_commitment", Context.MODE_PRIVATE)

    @Test fun ownedLiveTrialConsentActivationAndAcceleratedDeviceCleanup() {
        assumeFalse("Lab mode cannot verify live trial access",LocalTestSession.enabled)
        val input = File(context.filesDir,"trial-qa.json")
        assumeTrue("Live account checks require private disposable credentials; skipping is not acceptance",input.exists())
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim()=="1") {
            "This destructive QA teardown runs only on a disposable emulator"
        }
        try {
            val config = JSONObject(input.readText())
            val email = config.getString("email")
            val password = config.getString("password")
            val plan = config.getString("plan")
            check(email.matches(Regex("[^@]+\\+safenest-trial-[A-Za-z0-9-]+@gmail\\.com")))
            check(plan in setOf("monthly","quarterly","annual"))
            check(config.getString("consent")=="72-hour-disposable-test")
            assertNull(ProtectionCommitment.entitlementId(context))
            Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .setWaitForIdleTimeout(300)
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Account").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Account").performClick()
            compose.onNodeWithTag("trial-email").performScrollTo().performTextInput(email)
            compose.onNodeWithTag("trial-password").performScrollTo().performTextInput(password)
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("trial-plan-$plan").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("trial-consent").performScrollTo().assertIsOff().performClick()
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(45_000) { ProtectionCommitment.entitlementId(context)!=null }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("trial-start").fetchSemanticsNodes().isEmpty() }
            val entitlementId = ProtectionCommitment.entitlementId(context)
            val end = ProtectionCommitment.endsAt(context)
            assertEquals(72L*3600000,end-prefs().getLong("starts",0))
            assertFalse("Trial enrolment is not protection activation",ProtectionCommitment.isActive(context))
            // Retry through the real application flow, including a fresh security
            // challenge. Never bypass CAPTCHA with a tokenless test-only login.
            compose.onNodeWithContentDescription("Account").performClick()
            compose.onNodeWithTag("trial-email").performScrollTo().performTextInput(email)
            compose.onNodeWithTag("trial-password").performScrollTo().performTextInput(password)
            compose.onNodeWithTag("trial-plan-${if(plan=="annual") "monthly" else "annual"}").performScrollTo().performClick()
            compose.onNodeWithTag("trial-consent").performScrollTo().assertIsOff().performClick()
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(45_000) { compose.onAllNodesWithTag("trial-start").fetchSemanticsNodes().isEmpty() }
            assertEquals(entitlementId,ProtectionCommitment.entitlementId(context))
            assertEquals(end,ProtectionCommitment.endsAt(context))
            compose.onNodeWithText("Review and enable app guard").performScrollTo().performClick()
            compose.onNodeWithText("I agree — open settings").performClick()
            if (!GuardPreferences.isAccessibilityEnabled(context)) {
                var row = device.wait(Until.findObject(By.text("SafeNest app guard")),3000)
                if (row==null) {
                    device.wait(Until.findObject(By.text(Pattern.compile("(?i)^(downloaded apps|installed apps|downloaded services|installed services)$"))),5000)
                        ?.click() ?: error("Accessibility service list missing")
                    row = device.wait(Until.findObject(By.text("SafeNest app guard")),5000)
                }
                row?.click() ?: error("SafeNest Accessibility row missing")
                device.wait(Until.findObject(By.text("Use SafeNest app guard")),5000)?.click()
                    ?: error("SafeNest Accessibility switch missing")
                device.wait(Until.findObject(By.text(Pattern.compile("(?i)^allow$"))),5000)?.click()
                    ?: error("Android Accessibility permission confirmation missing")
            }
            compose.waitUntil(15_000) { GuardPreferences.isAccessibilityEnabled(context) && GuardPreferences.isServiceConnected() }
            device.executeShellCommand("am start -W -f 0x14000000 -n ${context.packageName}/${MainActivity::class.java.name}")
            compose.onNodeWithText("Start protection").performScrollTo().performClick()
            compose.onNodeWithText("I agree — start protection").performClick()
            if (android.net.VpnService.prepare(context)!=null) {
                device.wait(Until.findObject(By.text("OK")),5000)?.click() ?: error("Android VPN consent missing")
            }
            compose.waitUntil(20_000) { ProtectionCommitment.isActive(context) && SafeNestVpnService.isRunning.get() }
            assertEquals(end,ProtectionCommitment.endsAt(context))
            // Advance only this disposable cached anchor. Do not shorten the live
            // server trial or claim that 72 hours really elapsed on the emulator.
            prefs().edit().putLong("server_anchor",end).putLong("elapsed_anchor",SystemClock.elapsedRealtime())
                .putLong("high_water",end).commit()
            ProtectionCommitment.checkpoint(context)
            compose.waitUntil(15_000) { !SafeNestVpnService.isRunning.get() && !GuardPreferences.isSelected(context) && !GuardPreferences.blocksVpnApps(context) }
            assertFalse(ProtectionCommitment.hasVerifiedAccess(context))
            assertFalse(ProtectionCommitment.begin(context))
            File(context.filesDir,"trial-device-result.json").writeText(JSONObject()
                .put("entitlement_id",entitlementId).put("ends_at_ms",end).put("plan",plan)
                .put("live_login_consent_start_activation",true).put("accelerated_device_cleanup",true)
                .put("natural_72_hour_expiry",false).put("acceptance_passed",false).toString())
        } catch (_: Throwable) {
            // Compose assertion messages can dump text-entry semantics. Never
            // put credentials, UI trees, screenshots or the original cause in CI.
            throw AssertionError("Live trial device check failed; private UI details suppressed")
        } finally {
            input.delete()
            clearFixture()
        }
    }

    @Test fun explicitConsentAndSelectedPlanAreRequired() {
        assumeFalse("Lab mode does not have a trial screen", LocalTestSession.enabled)
        try {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription("Account").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Account").performClick()
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("trial-email").performScrollTo().performTextInput("fixture@example.invalid")
            compose.onNodeWithTag("trial-password").performScrollTo().performTextInput("fixture-not-a-login")
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("trial-plan-monthly").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("trial-plan-quarterly").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("trial-plan-monthly").assertIsNotSelected()
            compose.onNodeWithTag("trial-consent").performScrollTo().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsEnabled()
            // Never click Start using these fixture credentials.
            compose.onNodeWithTag("trial-consent").performScrollTo().performClick().assertIsOff()
            compose.onNodeWithTag("trial-start").performScrollTo().assertIsNotEnabled()
            assertNull("Plan/consent alone cannot cache a grant", ProtectionCommitment.entitlementId(context))
            assertFalse(ProtectionCommitment.isActive(context))
            assertFalse(SafeNestVpnService.isRunning.get())
        } finally { clearFixture() }
    }

    @Test fun expiredCachedTrialReleasesGuardAndCannotBeReactivated() {
        assumeFalse("Lab sessions are not server trial commitments", LocalTestSession.enabled)
        val start = System.currentTimeMillis()
        val end = start + 72L * 3600000
        try {
            ProtectionCommitment.cacheVerified(context, PaidWindow("fixture-trial", "fixture-user", "trial",start,end,start))
            assertEquals(end,ProtectionCommitment.endsAt(context))
            assertFalse("Verification alone is not activation",ProtectionCommitment.isActive(context))
            assertTrue(ProtectionCommitment.hasVerifiedAccess(context))
            assertTrue(CommitmentRules.active(true,end,end-1))
            assertFalse(CommitmentRules.active(true,end,end))
            // Isolated app-private cached fixture, NOT a production server update
            // or a real 72-hour elapsed trial. Advance the server-time anchor
            // instead of the device wall clock (rollback/jumps are ignored).
            prefs().edit().putBoolean("committed",true).putLong("server_anchor",end)
                .putLong("elapsed_anchor",SystemClock.elapsedRealtime()).putLong("high_water",end)
                .putInt("boot",Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)).commit()
            context.getSharedPreferences("safenest_guard",Context.MODE_PRIVATE).edit()
                .putBoolean("enabled",true).putBoolean("block_vpn_apps",true).commit()
            ProtectionCommitment.checkpoint(context)
            compose.waitUntil(10_000) { !GuardPreferences.isSelected(context) && !GuardPreferences.blocksVpnApps(context) }
            assertFalse(ProtectionCommitment.isActive(context))
            assertFalse(ProtectionCommitment.hasVerifiedAccess(context))
            assertFalse(ProtectionCommitment.begin(context))
            assertFalse(SafeNestVpnService.isRunning.get())
            assertFalse(prefs().getBoolean("committed",false))
        } finally { clearFixture() }
    }

    private fun clearFixture() {
        prefs().edit().putLong("ends",0).commit()
        ProtectionCommitment.releaseExpired(context)
        prefs().edit().clear().commit()
    }
}
