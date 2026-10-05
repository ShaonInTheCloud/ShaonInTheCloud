package com.safenest.app

import android.content.Context

/** Attributed third-party gambling feeds. Exact hostnames and their subdomains only. */
object BundledGamblingRules {
    private val files = listOf("gambling_hosts_vn.txt", "gambling_hosts_sinfonietta.txt", "gambling_bangladesh_researched.txt", "gambling_brand_families.txt")
    @Volatile private var cache: Set<String>? = null

    fun domains(context: Context): Set<String> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: files.flatMap { file ->
                context.assets.open(file).bufferedReader().use { reader ->
                    reader.lineSequence().mapNotNull(DomainRules::normalizeHostname).toList()
                }
            }.toSet().also { cache = it }
        }
    }
}
