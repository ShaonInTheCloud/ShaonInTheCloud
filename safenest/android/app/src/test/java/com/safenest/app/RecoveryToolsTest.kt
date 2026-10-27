package com.safenest.app

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryToolsTest {
    private fun query(url: String) = url.substringAfter('?').substringBefore('#').split('&')
        .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    @Test fun reportUrlCarriesNormalizedHostAndKind() {
        val missed = siteReportUrl("  https://WWW.Example-Bet.com/promo?x=1 ", wronglyBlocked = false)!!
        assertTrue(missed.startsWith("https://mysafenestbd.com/account?") && missed.endsWith("#support"))
        val q = query(missed)
        assertEquals("app", q["topic"])
        assertEquals("Missed gambling site: example-bet.com", q["subject"]) // same form as blocklist rules: www. dropped
        assertTrue(q["message"]!!.endsWith(": example-bet.com"))
        assertEquals("Wrongly blocked: news.example", query(siteReportUrl("news.example", wronglyBlocked = true)!!)["subject"])
    }

    @Test fun reportUrlRejectsNonDomains() {
        for (bad in listOf("", "   ", "not a site", "a b.com", "\\\\evil", "http://")) assertNull(bad, siteReportUrl(bad, false))
    }

    @Test fun daysFreeNeverNegative() {
        assertEquals(0, daysFree(100, 100))
        assertEquals(7, daysFree(100, 107))
        assertEquals(0, daysFree(107, 100)) // clock set back: show 0, not -7
    }
}
