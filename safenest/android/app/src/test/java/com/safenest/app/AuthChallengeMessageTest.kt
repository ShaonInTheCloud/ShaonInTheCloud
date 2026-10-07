package com.safenest.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthChallengeMessageTest {
    private val page = "https://mysafenestbd.com/android-captcha"
    private val origin = "https://mysafenestbd.com"
    private val nonce = "1234567890abcdef1234567890abcdef"
    private fun body(n: String = nonce, type: String = "token", token: String = "fresh") =
        JSONObject().put("nonce", n).put("type", type).put("token", token).toString()
    private fun read(url: String? = "$page#nonce=$nonce", source: String = origin,
        frame: Boolean = true, data: String? = body()) = readChallengeMessage(page, origin, nonce, url, source, frame, data)

    @Test fun acceptsOnlyTheBoundAttemptAndConsumesItsToken() {
        val token = checkNotNull(read())
        assertEquals("fresh", token.take())
        assertThrows(IllegalStateException::class.java) { token.take() }
    }
    @Test fun rejectsFramesForeignOriginsAndRedirectedPages() {
        assertNull(read(frame = false))
        for (source in listOf("https://challenges.cloudflare.com", "https://mysafenestbd.com.evil.test", "http://mysafenestbd.com"))
            assertNull(read(source = source))
        for (url in listOf(null, "about:blank", "$origin/account.html", "$page.html", "$page/extra", "https://evil.test/android-captcha"))
            assertNull(read(url = url))
    }
    @Test fun rejectsOldAttemptsMalformedMessagesAndMissingTokens() {
        for (data in listOf(null, "not-json", "{}", "x".repeat(4097), body(n = "old-attempt"),
            body(type = "credentials"), body(token = ""), body(token = "x".repeat(2049)))) assertNull(read(data = data))
    }
}
