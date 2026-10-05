package com.safenest.app

import org.junit.Assert.*
import org.junit.Test

class ChromePolicyRulesTest {
    @Test fun normalizesAndCompressesWholeDomainRules() {
        val plan = ChromePolicyRules.plan(emptyList(), listOf("sub.Example.org", "EXAMPLE.org", "badexample.org", "https://example.net/path", "invalid"))
        assertEquals(listOf("badexample.org", "example.net", "example.org"), plan.entries)
        assertEquals(4, plan.requestedDomains)
        assertEquals(4, plan.listedDomains)
        assertEquals(0, plan.omittedDomains)
    }
    @Test fun preservesExistingPatternsAndCountsCapacityHonestly() {
        val previous = listOf("https://keep.example/path", ".exact.example")
        val plan = ChromePolicyRules.plan(previous, listOf("c.test", "b.test", "a.test"), 4)
        assertEquals(previous + listOf("a.test", "b.test"), plan.entries)
        assertEquals(2, plan.listedDomains)
        assertEquals(1, plan.omittedDomains)
        assertEquals(2, plan.preexistingEntries)
    }
    @Test fun doesNotTreatPathPolicyAsWholeDomainCoverage() {
        val plan = ChromePolicyRules.plan(listOf("https://example.org/path"), listOf("example.org"), 1)
        assertEquals(0, plan.listedDomains)
        assertEquals(1, plan.omittedDomains)
    }
    @Test fun existingWholeDomainCoversSubdomainsWithoutDuplicateEntries() {
        val plan = ChromePolicyRules.plan(listOf("example.org"), listOf("sub.example.org", "example.org", "notexample.org"), 1)
        assertEquals(listOf("example.org"), plan.entries)
        assertEquals(2, plan.listedDomains)
        assertEquals(1, plan.omittedDomains)
    }
    @Test fun selectionIsStableAndLimited() {
        val names = (0..1500).map { "domain${it.toString().padStart(4, '0')}.test" }
        val forward = ChromePolicyRules.plan(emptyList(), names)
        val backward = ChromePolicyRules.plan(emptyList(), names.reversed())
        assertEquals(forward, backward)
        assertEquals(1000, forward.entries.size)
        assertEquals(501, forward.omittedDomains)
    }
    @Test fun customDomainWinsChromeCapacityOverAlphabeticFeed() {
        val plan = ChromePolicyRules.plan(emptyList(), listOf("a.test", "b.test", "z-user.test"), 1,
            priorityDomains = listOf("z-user.test"))
        assertEquals(listOf("z-user.test"), plan.entries)
        assertEquals(2, plan.omittedDomains)
    }
    @Test fun wildcardPreservedWithoutChangingExceptions() {
        val plan = ChromePolicyRules.plan(listOf("*"), listOf("one.test", "two.test"), 1)
        assertEquals(listOf("*"), plan.entries)
        assertEquals(2, plan.listedDomains) // Separate managed status warns about URLAllowlist.
    }
    @Test fun recoveryAcceptsOwnWritesAndRejectsExternalChanges() {
        val original = "absent" to null
        assertTrue(ChromePolicyRules.canRestore(original, original, "new", "old"))
        assertTrue(ChromePolicyRules.canRestore("string" to "new", original, "new", "old"))
        assertTrue(ChromePolicyRules.canRestore("string" to "old", original, "new", "old"))
        assertFalse(ChromePolicyRules.canRestore("string" to "external", original, "new", "old"))
        assertFalse(ChromePolicyRules.canRestore("array" to "new", original, "new", "old"))
        assertFalse(ChromePolicyRules.canRestore("string" to null, original, null, null))
    }
    @Test(expected = IllegalArgumentException::class)
    fun neverTruncatesPreexistingRules() {
        ChromePolicyRules.plan(listOf("one.test", "two.test"), listOf("three.test"), 1)
    }
}
