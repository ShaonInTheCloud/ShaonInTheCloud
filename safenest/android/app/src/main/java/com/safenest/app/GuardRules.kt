package com.safenest.app

import java.net.IDN
import java.net.URI
import java.util.Locale

/** Pure parsing rules. A URL is never matched using page text or gambling keywords. */
object GuardRules {
    // Browsers are exempt from whole-app selection; supported address bars are checked separately.
    private val browserExemptions = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix"
    )
    fun isBrowserExempt(packageName: String): Boolean = packageName in browserExemptions

    fun shouldReturnHome(packageName: String, selectedOrVpn: Boolean, essential: Boolean): Boolean =
        isPackageName(packageName) && selectedOrVpn && !essential && !isBrowserExempt(packageName)

    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    fun isPackageName(value: String): Boolean =
        value.length in 3..255 && packagePattern.matches(value)

    /**
     * Accept a displayed HTTP(S) URL or bare domain; reject search phrases,
     * app-internal URLs, credentials and IP addresses. Never inspect the path.
     */
    fun hostFromAddressBar(text: String): String? {
        val value = text.trim()
        if (value.isEmpty() || value.length > 8192 || value.any { it.isWhitespace() || it.isISOControl() }) return null
        if (value.contains('\\')) return null
        val url = when {
            value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
            value.contains("://") || value.startsWith("//") -> return null
            else -> "https://$value"
        }
        val uri = try { URI(url) } catch (_: Exception) { return null }
        if (uri.scheme.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        if (uri.rawUserInfo != null) return null
        val authority = uri.rawAuthority ?: return null
        if (authority.contains('@') || authority.contains('%') || authority.contains('[') || authority.contains(']')) return null
        val hostAndPort = authority.split(':')
        if (hostAndPort.size > 2) return null
        if (hostAndPort.size == 2 && hostAndPort[1].toIntOrNull()?.let { it in 1..65535 } != true) return null
        val host = try {
            IDN.toASCII(hostAndPort[0].trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) { return null }
        val labels = host.split('.')
        if (host.length !in 3..253 || labels.size < 2 || labels.all { it.toIntOrNull() != null }) return null
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null
        if (labels.last().length < 2) return null
        return host
    }
}
