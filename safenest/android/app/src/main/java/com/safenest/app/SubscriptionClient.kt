package com.safenest.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** A sign-in or access-check failure with a reason key from [AccessErrors]. */
class AccessException(val reason: String) : Exception(reason)

data class AccessCheck(val userId: String, val checkedId: String?, val serverNow: Long, val window: PaidWindow?)

/** Auth credentials are sent only to the configured Supabase HTTPS endpoint and never logged/saved. */
object SubscriptionClient {
    fun verify(email: String, password: String): PaidWindow {
        return checkAccess(email, password).window
            ?: error("This account has no active trial or subscription. Start the three-day trial below, or buy a plan on the SafeNest website.")
    }
    fun startTrial(email: String, password: String, plan: String): PaidWindow {
        require(plan in setOf("monthly", "quarterly", "annual"))
        return checkAccess(email, password, startTrialPlan = plan).window
            ?: error("Your trial has already ended. A verified subscription is required.")
    }
    /** Short-lived sign-in used for one purchase flow. Never persisted. */
    data class Session(val jwt: String, val userId: String)

    fun signIn(email: String, password: String): Session {
        val base = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        check(base.startsWith("https://") && key.isNotBlank()) { "Paid access is not configured in this build." }
        val auth = post("$base/auth/v1/token?grant_type=password", key, null,
            JSONObject().put("email", email.trim()).put("password", password))
        val token = auth.optString("access_token")
        check(token.isNotBlank()) { "Sign in to your SafeNest account first." }
        val userId = auth.optJSONObject("user")?.optString("id").orEmpty()
        check(userId.isNotBlank()) { "Sign in to your SafeNest account first." }
        return Session(token, userId)
    }

    fun checkAccess(email: String, password: String, entitlementId: String? = null, startTrialPlan: String? = null): AccessCheck {
        val base = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        val session = signIn(email, password)
        if (startTrialPlan != null) {
            post("$base/functions/v1/start-trial", key, session.jwt, JSONObject().put("plan_code", startTrialPlan))
        }
        val body = JSONObject().apply { if (entitlementId != null) put("entitlement_id", entitlementId) }
        return parseAccess(post("$base/functions/v1/protection-access", key, session.jwt, body), entitlementId)
    }

    /**
     * Hands a Google Play purchase token to the SafeNest server. The server re-fetches
     * the subscription from Google, binds it to this account and returns the entitlement.
     * Returns null while Google still reports the payment as pending.
     */
    fun verifyPlayPurchase(session: Session, purchaseToken: String): AccessCheck? {
        val base = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        val reply = post("$base/functions/v1/play-billing-verify", key, session.jwt, JSONObject().put("purchase_token", purchaseToken))
        if (reply.optBoolean("pending", false)) return null
        return parseAccess(reply, null)
    }

    private fun parseAccess(reply: JSONObject, entitlementId: String?): AccessCheck {
        val user = reply.getString("user_id")
        val serverNow = Instant.parse(reply.getString("server_now")).toEpochMilli()
        val checkedId = if (reply.isNull("checked_entitlement_id")) null else reply.getString("checked_entitlement_id")
        check(checkedId == entitlementId) { "The server did not verify the requested period." }
        val window = if (reply.getBoolean("active")) {
            val item = reply.getJSONObject("entitlement")
            PaidWindow(item.getString("id"), user, item.getString("plan_code"),
                Instant.parse(item.getString("starts_at")).toEpochMilli(),
                Instant.parse(item.getString("ends_at")).toEpochMilli(), serverNow).also {
                check(it.plan in setOf("weekly", "monthly", "quarterly", "annual", "trial")) { "Unknown access plan." }
                check(CommitmentRules.validWindow(it.starts, it.ends, serverNow) &&
                    (entitlementId == null || it.id == entitlementId)) { "The paid period could not be verified." }
            }
        } else null
        return AccessCheck(user, checkedId, serverNow, window)
    }
    private fun post(url: String, key: String, jwt: String?, body: JSONObject): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000; connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("apikey", key)
            if (jwt != null) connection.setRequestProperty("Authorization", "Bearer $jwt")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    check(out.size() + n <= 65536) { "Paid access reply was too large." }; out.write(buffer, 0, n)
                }; out.toByteArray()
            } ?: byteArrayOf()
            if (code !in 200..299) {
                val reply = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                // Supabase Auth uses error_code (newer) or error (older); SafeNest functions use error.
                val serverCode = reply?.let { r -> listOf("error_code", "error", "msg").map { r.optString(it) }.firstOrNull { it.isNotBlank() } }
                throw AccessException(AccessErrors.reason(code, serverCode))
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } catch (offline: java.io.IOException) {
            throw AccessException(AccessErrors.OFFLINE)
        } finally { connection.disconnect() }
    }
}
