package com.safenest.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The store delegates these decisions to the Android-independent rule engine. */
class RulesStoreTest {
    @Test fun normalizesUrlsToHostnames() {
        assertEquals("example.com", DomainRules.normalize("https://www.Example.com/path?q=1"))
        assertEquals("xn--bcher-kva.example", DomainRules.normalize("bücher.example"))
    }

    @Test fun rejectsMalformedHostnames() {
        assertNull(DomainRules.normalize("not a domain"))
        assertNull(DomainRules.normalize("-bad.example"))
        assertNull(DomainRules.normalize("example.c"))
        assertNull(DomainRules.normalize("192.0.2.1"))
        assertNull(DomainRules.normalize("https://example.com@other.example"))
        assertNull(DomainRules.normalize("example.com?next=other.example"))
    }

    @Test fun acceptsSubdomainsButNotLookalikesInMatchingContract() {
        val rule = "bet.example"
        assertEquals(true, DomainRules.matches("bet.example", rule))
        assertEquals(true, DomainRules.matches("www.bet.example", rule))
        assertEquals(false, DomainRules.matches("bet.example.attacker.test", rule))
        assertEquals(false, DomainRules.matches("notbet.example", rule))
    }
    @Test fun countryTldNeedsItsOwnRule() {
        assertEquals(false, DomainRules.isBlocked("1xbet.fi", setOf("1xbet.com")))
        assertEquals(true, DomainRules.isBlocked("m.1xbet.fi", setOf("1xbet.fi")))
        assertEquals(false, DomainRules.isBlocked("1xbet.fi.unrelated.test", setOf("1xbet.fi")))
    }
}
