package com.safenest.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionCaptchaTest {
    private fun access(id: String? = null) = JSONObject()
        .put("user_id", "owned-user").put("server_now", "2026-10-07T01:00:00Z")
        .put("checked_entitlement_id", id ?: JSONObject.NULL).put("active", true)
        .put("entitlement", JSONObject().put("id", id ?: "period-1").put("plan_code", "trial")
            .put("starts_at", "2026-10-07T00:00:00Z").put("ends_at", "2026-10-10T00:00:00Z"))

    @Test fun freshChallengeAuthenticatesBeforeInitialVerification() {
        val paths = mutableListOf<String>()
        val result = SubscriptionClient.checkAccessUsing(" user@example.test ", "owned-password", AuthCaptchaToken("fresh")) { path, jwt, body ->
            paths.add(path)
            if (path.startsWith("/auth/")) {
                assertNull(jwt)
                assertEquals("user@example.test", body.getString("email"))
                assertEquals("owned-password", body.getString("password"))
                assertEquals("fresh", body.getJSONObject("gotrue_meta_security").getString("captcha_token"))
                JSONObject().put("access_token", "verified-jwt")
            } else {
                assertEquals("verified-jwt", jwt)
                assertFalse(body.has("gotrue_meta_security"))
                access()
            }
        }
        assertEquals(listOf("/auth/v1/token?grant_type=password", "/functions/v1/protection-access"), paths)
        assertEquals("period-1", result.window?.id)
    }

    @Test fun reVerificationPreservesTheRequestedEntitlement() {
        val result = SubscriptionClient.checkAccessUsing("user@example.test", "password", AuthCaptchaToken("fresh-again"), "period-2") { path, _, body ->
            if (path.startsWith("/auth/")) JSONObject().put("access_token", "jwt")
            else { assertEquals("period-2", body.getString("entitlement_id")); access("period-2") }
        }
        assertEquals("period-2", result.checkedId)
        assertEquals("period-2", result.window?.id)
    }

    @Test fun trialStartRequiresFreshAuthAndKeepsThePlanChoice() {
        val paths = mutableListOf<String>()
        SubscriptionClient.checkAccessUsing("user@example.test", "password", AuthCaptchaToken("trial-fresh"), startTrialPlan = "quarterly") { path, jwt, body ->
            paths.add(path)
            when {
                path.startsWith("/auth/") -> JSONObject().put("access_token", "jwt")
                path.endsWith("start-trial") -> { assertEquals("jwt", jwt); assertEquals("quarterly", body.getString("plan_code")); JSONObject() }
                else -> access()
            }
        }
        assertEquals(listOf("/auth/v1/token?grant_type=password", "/functions/v1/start-trial", "/functions/v1/protection-access"), paths)
    }

    @Test fun missingTokenMakesNoNetworkRequest() {
        assertThrows(IllegalStateException::class.java) {
            SubscriptionClient.checkAccessUsing("user@example.test", "password", null) { _, _, _ -> error("Must not request") }
        }
    }

    @Test fun invalidOrReplayedProviderTokenNeverReachesAccessOrTrial() {
        // Provider stub: only it can establish validity, not the app's shape check.
        val used = mutableSetOf<String>()
        var accessCalls = 0
        val request: (String, String?, JSONObject) -> JSONObject = { path, _, body ->
            if (path.startsWith("/auth/")) {
                val token = body.getJSONObject("gotrue_meta_security").getString("captcha_token")
                check(token == "valid" && used.add(token)) { "captcha_failed" }
                JSONObject().put("access_token", "jwt")
            } else { accessCalls++; access() }
        }
        assertThrows(IllegalStateException::class.java) {
            SubscriptionClient.checkAccessUsing("user@example.test", "password", AuthCaptchaToken("invalid"), startTrialPlan = "monthly", request = request)
        }
        assertEquals(0, accessCalls)
        SubscriptionClient.checkAccessUsing("user@example.test", "password", AuthCaptchaToken("valid"), request = request)
        assertEquals(1, accessCalls)
        assertThrows(IllegalStateException::class.java) {
            SubscriptionClient.checkAccessUsing("user@example.test", "password", AuthCaptchaToken("valid"), request = request)
        }
        assertEquals(1, accessCalls)
    }

    @Test fun networkFailureConsumesTheAttemptAndRetryNeedsANewChallenge() {
        val token = AuthCaptchaToken("fresh")
        assertThrows(IllegalStateException::class.java) {
            SubscriptionClient.checkAccessUsing("user@example.test", "password", token) { _, _, _ -> error("network failure") }
        }
        assertThrows(IllegalStateException::class.java) { token.take() }
    }
}
