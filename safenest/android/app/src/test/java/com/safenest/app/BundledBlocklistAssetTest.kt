package com.safenest.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the shipped compact assets against their manifests and the exclusion list.
 * Gradle runs JVM unit tests with the module directory (android/app) as working directory.
 */
class BundledBlocklistAssetTest {
    private val assets = File("src/main/assets")
    private val data = File("../../data/blocklists")

    private fun load(name: String) = File(assets, name).inputStream().buffered().use { CompactDomainSet.read(it) }
    private fun manifest(name: String) = JSONObject(File(data, name).readText())

    @Test fun assetsMatchTheirManifests() {
        for ((asset, manifest) in listOf(
            BundledGamblingRules.CORE_ASSET to "gambling_core.manifest.json",
            BundledGamblingRules.GPL_ASSET to "gambling_hagezi_gpl3.manifest.json"
        )) {
            val m = manifest(manifest)
            val bytes = File(assets, asset).readBytes()
            assertEquals("$asset digest is stale; rerun tools/blocklist/build.sh", m.getString("asset_sha256"), CompactDomainSet.hexSha256(bytes))
            assertEquals(m.getInt("fingerprints"), load(asset).size())
        }
    }

    @Test fun combinedListCoversTheRecordedCatalogue() {
        val total = load(BundledGamblingRules.CORE_ASSET).size() + load(BundledGamblingRules.GPL_ASSET).size()
        assertTrue("combined bundled list shrank: $total", total >= 540_000)
    }

    @Test fun bangladeshResearchIsBlocked() {
        val core = load(BundledGamblingRules.CORE_ASSET)
        for (host in listOf("jeetbuzz.com", "baji.live", "krikya.me", "mcw-bangladesh.com", "babu88.com",
                "six6s-bd.com", "jaya9.net", "nagad88.bet", "velkiag.com", "crickex.bet", "www.jeetbuzz.com", "m.baji.live")) {
            assertTrue("expected $host blocked", core.blocksHost(host))
        }
    }

    @Test fun unconfirmedResearchRowsAreNotBundledFromTheResearchFile() {
        // savannahepc.org / indopact.org were marked '?' (possibly repurposed organisation domains).
        val all = listOf(load(BundledGamblingRules.CORE_ASSET), load(BundledGamblingRules.GPL_ASSET))
        for (host in listOf("savannahepc.org", "indopact.org")) assertFalse(host, all.any { it.blocksHost(host) })
    }

    @Test fun exclusionsAndOrdinaryServicesAreNeverBlocked() {
        val all = listOf(load(BundledGamblingRules.CORE_ASSET), load(BundledGamblingRules.GPL_ASSET))
        val exclusions = File(data, "exclusions.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue(exclusions.size > 50)
        for (host in exclusions + listOf("www.google.com", "m.facebook.com", "web.whatsapp.com", "bd.daraz.com.bd")) {
            assertFalse("$host must not be blocked", all.any { it.blocksHost(host) })
        }
    }

    @Test fun chromePriorityListIsPlainValidHostnames() {
        val names = File(assets, BundledGamblingRules.PRIORITY_ASSET).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(names.size in 400..2000)
        for (n in names) assertEquals(n, DomainRules.normalizeHostname(n))
        assertTrue("jeetbuzz.com" in names)
    }

    @Test fun gplLayerShipsWithItsLicence() {
        assertTrue(File(assets, "third_party_hagezi_gpl3.txt").readText().contains("GNU GENERAL PUBLIC LICENSE"))
    }
}
