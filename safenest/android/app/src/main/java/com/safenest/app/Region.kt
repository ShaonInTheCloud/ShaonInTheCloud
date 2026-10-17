package com.safenest.app

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * One app, two editions. The Bangladesh edition defaults to Bangla and the local
 * support channel; the Global edition defaults to English. Prices come from Google
 * Play per country, so the edition never changes what is charged. The user can
 * switch edition in Settings at any time; detection only picks the first default.
 */
object Region {
    const val BANGLADESH = "bd"
    const val GLOBAL = "global"
    private const val KEY = "region"

    fun current(context: Context): String {
        val prefs = context.getSharedPreferences("safenest_app", Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { if (it == BANGLADESH || it == GLOBAL) return it }
        val detected = detect(context)
        prefs.edit().putString(KEY, detected).apply()
        return detected
    }

    fun set(context: Context, region: String) {
        require(region == BANGLADESH || region == GLOBAL)
        context.getSharedPreferences("safenest_app", Context.MODE_PRIVATE).edit().putString(KEY, region).apply()
    }

    /** SIM country first (stable while travelling), then network, then the device locale. */
    internal fun detect(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val candidates = listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso,
            Locale.getDefault().country).map { it.lowercase(Locale.ROOT) }
        return if (candidates.firstOrNull { it.isNotBlank() } == "bd") BANGLADESH else GLOBAL
    }

    fun defaultLanguage(region: String): String = if (region == BANGLADESH) "bn" else "en"
    fun supportUrl(region: String): String =
        if (region == BANGLADESH) "https://mysafenestbd.com/support" else "https://mysafenestbd.com/global/support"
}
