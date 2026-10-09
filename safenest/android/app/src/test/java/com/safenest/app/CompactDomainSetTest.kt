package com.safenest.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.random.Random

class CompactDomainSetTest {
    /** Changing the fingerprint silently would invalidate every shipped asset. */
    @Test fun fingerprintIsStable() {
        assertEquals(0x8cb50e61f7cb405dUL.toLong(), CompactDomainSet.fingerprint("jeetbuzz.com"))
        assertEquals(0x4d07e1c6cc597ab7L, CompactDomainSet.fingerprint("example.com"))
        assertEquals(0xd82737e47fc8c746UL.toLong(), CompactDomainSet.fingerprint("a.bc"))
    }

    @Test fun matchesExactNamesAndSubdomainsLikeDomainRules() {
        val set = CompactDomainSet.fromHostnames(listOf("bet.example", "Casino.Test", " spaced.example ", "not a host", "-bad.example"))
        assertEquals(3, set.size())
        assertTrue(set.blocksHost("bet.example"))
        assertTrue(set.blocksHost("www.bet.example"))
        assertTrue(set.blocksHost("deep.sub.casino.test"))
        assertTrue(set.blocksHost("spaced.example"))
        assertFalse(set.blocksHost("notbet.example"))
        assertFalse(set.blocksHost("bet.example.attacker.test"))
        assertFalse(set.blocksHost("example"))
        assertFalse(set.blocksHost(null))
        assertTrue(set.containsNormalized("casino.test"))
        assertFalse(set.containsNormalized("www.casino.test"))
    }

    @Test fun agreesWithStringSetMatcherOnRandomNames() {
        val rng = Random(7)
        fun label() = (1..rng.nextInt(1, 9)).map { "abcdefghijklmnopqrstuvwxyz0123456789"[rng.nextInt(36)] }.joinToString("")
        val tlds = listOf("com", "net", "bd", "bet", "casino")
        val rules = (1..2000).map { "${label()}.${tlds[rng.nextInt(tlds.size)]}" }.toSet()
        val compact = CompactDomainSet.fromHostnames(rules)
        val probes = rules.take(300).map { "www.$it" } + (1..2000).map { "${label()}.${label()}.${tlds[rng.nextInt(tlds.size)]}" }
        for (host in probes) assertEquals(host, DomainRules.isBlocked(host, rules), compact.blocksHost(host))
    }

    @Test fun roundTripsAndRejectsDamagedAssets() {
        val set = CompactDomainSet.fromHostnames(listOf("one.example", "two.example", "three.example"))
        val bytes = set.toBytes()
        val back = CompactDomainSet.read(ByteArrayInputStream(bytes))
        assertEquals(3, back.size())
        assertTrue(back.blocksHost("www.two.example"))

        val flipped = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(IOException::class.java) { CompactDomainSet.read(ByteArrayInputStream(flipped)) }
        assertThrows(IOException::class.java) { CompactDomainSet.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 3))) }
        assertThrows(IOException::class.java) { CompactDomainSet.read(ByteArrayInputStream(bytes + byteArrayOf(0))) }
        val wrongMagic = bytes.copyOf().also { it[0] = 0 }
        assertThrows(IOException::class.java) { CompactDomainSet.read(ByteArrayInputStream(wrongMagic)) }
    }

    @Test fun minusRemovesOverlap() {
        val a = CompactDomainSet.fromHostnames(listOf("a.example", "b.example", "c.example"))
        val b = CompactDomainSet.fromHostnames(listOf("b.example"))
        val diff = a.minus(b)
        assertEquals(2, diff.size())
        assertFalse(diff.containsNormalized("b.example"))
        assertEquals(0, CompactDomainSet.empty().size())
    }
}
