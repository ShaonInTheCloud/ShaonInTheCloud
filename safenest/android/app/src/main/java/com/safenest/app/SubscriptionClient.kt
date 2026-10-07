package com.safenest.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class AccessCheck(val userId: String, val checkedId: String?, val serverNow: Long, val window: PaidWindow?)

/** Auth credentials are sent only to the configured Supabase HTTPS endpoint and never logged/saved. */
object SubscriptionClient {
    fun verify(email: String, password: String): PaidWindow {
        return checkAccess(email, password).window
            ?: error("No active trial or subscription. Access must be confirmed on the server.")
    }
    fun startTrial(email: String, password: String, plan: String): PaidWindow {
        require(plan in setOf("monthly", "quarterly", "annual"))
        return checkAccess(email, password, startTrialPlan = plan).window
            ?: error("Your trial has already ended. A verified subscription is required.")
    }
    fun checkAccess(email: String, password: String, entitlementId: String? = null, startTrialPlan: String? = null): AccessCheck {
        val base = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        check(base.startsWith("https://") && key.isNotBlank()) { "Paid access is not configured in this build." }
        val auth = post("$base/auth/v1/token?grant_type=password", key, null,
            JSONObject().put("email", email.trim()).put("password", password))
        val token = auth.optString("access_token")
        check(token.isNotBlank()) { "Sign in to your SafeNest account first." }
        if (startTrialPlan != null) {
            post("$base/functions/v1/start-trial", key, token, JSONObject().put("plan_code", startTrialPlan))
        }
        val body = JSONObject().apply { if (entitlementId != null) put("entitlement_id", entitlementId) }
        val reply = post("$base/functions/v1/protection-access", key, token, body)
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
            if (code !in 200..299) error(when (code) {
                400, 401, 403 -> "Sign-in or access verification failed. Check your account and confirmed email."
                429 -> "Too many attempts. Wait before retrying."
                else -> "Paid access verification is unavailable. Try again when connected."
            })
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
