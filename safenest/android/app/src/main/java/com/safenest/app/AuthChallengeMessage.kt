package com.safenest.app

import org.json.JSONObject

/** Accept only this attempt's trusted top-level page. Provider validity is checked by Auth. */
internal fun readChallengeMessage(page: String, origin: String, nonce: String,
    currentPage: String?, source: String, mainFrame: Boolean, data: String?): AuthCaptchaToken? {
    if (!mainFrame || source != origin || currentPage?.substringBefore('#') != page) return null
    if (data == null || data.length > 4096) return null
    val body = runCatching { JSONObject(data) }.getOrNull() ?: return null
    if (body.optString("nonce") != nonce || body.optString("type") != "token") return null
    return runCatching { AuthCaptchaToken(body.optString("token")) }.getOrNull()
}
