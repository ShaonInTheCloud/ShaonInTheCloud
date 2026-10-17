package com.safenest.app

import android.content.Context
import java.net.InetAddress

/** Curated gambling IP ranges (assets/blocked_apps/blocked_ips.txt), validated by IpRules. */
object BlockedIps {
    data class Range(val address: InetAddress, val prefix: Int)
    @Volatile private var ranges: List<Range>? = null

    fun ranges(context: Context): List<Range> {
        ranges?.let { return it }
        return synchronized(this) {
            ranges ?: try {
                context.assets.open("blocked_apps/blocked_ips.txt").bufferedReader().useLines { lines ->
                    lines.mapNotNull { IpRules.parseBlockEntry(it) }
                        .map { Range(InetAddress.getByAddress(it[0] as ByteArray), it[1] as Int) }
                        .take(2000).toList()
                }
            } catch (_: Exception) { emptyList<Range>() }.also { ranges = it }
        }
    }
}
