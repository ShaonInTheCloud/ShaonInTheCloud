package com.safenest.app

/** Pure policy planning; Android's Chrome managed configuration uses a JSON string array. */
data class ChromePolicyPlan(
    val entries: List<String>,
    val requestedDomains: Int,
    val listedDomains: Int,
    val omittedDomains: Int,
    val preexistingEntries: Int
)

object ChromePolicyRules {
    const val MAX_ENTRIES = 1000

    /**
     * Preserve all previous patterns and their order. Add a deterministic bounded subset.
     * Bare hosts in URLBlocklist cover that host and its subdomains on all schemes.
     * Existing arbitrary URL patterns are preserved without claiming they cover a whole domain.
     */
    fun plan(existing: List<String>, domains: Collection<String>, limit: Int = MAX_ENTRIES,
             priorityDomains: Collection<String> = emptyList()): ChromePolicyPlan {
        require(limit in 0..MAX_ENTRIES) { "Unsupported Chrome policy capacity." }
        require(existing.size <= limit) { "The pre-existing Chrome blocklist exceeds the $limit-entry budget; it was left unchanged." }
        val normalized = domains.mapNotNull(DomainRules::normalize).toSortedSet()
        val priorities = priorityDomains.mapNotNull(DomainRules::normalize).toSet()
        val roots = normalized.filter { name -> !parents(name).any { it in normalized } }
            .sortedWith(compareBy({ name: String -> if (priorities.any { DomainRules.matches(it, name) }) 0 else 1 }, { it }))
        val result = existing.toMutableList()
        val broadExisting = existing.filter { DomainRules.normalize(it) == it }.toSet()
        val coveredRoots = broadExisting.toMutableSet()
        val blocksAll = "*" in existing
        for (name in roots) {
            if (blocksAll || isCovered(name, coveredRoots)) continue
            if (result.size >= limit) break
            result.add(name)
            coveredRoots.add(name)
        }
        val covered = normalized.count { blocksAll || isCovered(it, coveredRoots) }
        return ChromePolicyPlan(result, normalized.size, covered, normalized.size - covered, existing.size)
    }

    /** Never replace a policy that an administrator changed after our write. */
    fun canRestore(current: Pair<String, String?>, original: Pair<String, String?>,
                   expected: String?, priorExpected: String?): Boolean =
        current == original || (current.first == "string" &&
            ((expected != null && current.second == expected) ||
             (priorExpected != null && current.second == priorExpected)))

    private fun isCovered(name: String, domains: Set<String>): Boolean =
        name in domains || parents(name).any { it in domains }

    private fun parents(name: String): Sequence<String> = sequence {
        var dot = name.indexOf('.')
        while (dot >= 0) {
            yield(name.substring(dot + 1))
            dot = name.indexOf('.', dot + 1)
        }
    }
}
