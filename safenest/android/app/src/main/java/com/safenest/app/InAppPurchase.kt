package com.safenest.app

import android.app.Activity
import android.content.Context

/** One purchasable plan as the store presents it (localized price, store offer token). */
data class StoreOffer(val planCode: String, val title: String, val formattedPrice: String, val period: String, val offerToken: String)

sealed class PurchaseOutcome {
    /** The store completed the purchase; the server must verify this token before any access is granted. */
    data class Purchased(val purchaseToken: String) : PurchaseOutcome()
    /** The store accepted the order but payment is still pending (e.g. cash/slow payment methods). */
    data class Pending(val purchaseToken: String) : PurchaseOutcome()
    object Cancelled : PurchaseOutcome()
    data class Failed(val message: String) : PurchaseOutcome()
}

/**
 * Store billing abstraction. The Play flavor talks to Google Play Billing; the
 * direct, lab and owner-test flavors have no store and report [available] false.
 * A purchase token is never trusted on the device: it is handed to the SafeNest
 * server, which re-fetches the subscription from Google and issues the entitlement.
 */
interface InAppPurchaseProvider {
    val available: Boolean
    /** Store product id and base plans. Empty when the store has nothing to sell on this device. */
    suspend fun offers(context: Context): List<StoreOffer>
    /** Launches the store purchase flow bound to the SafeNest account id (obfuscated account id). */
    suspend fun purchase(activity: Activity, offer: StoreOffer, accountId: String): PurchaseOutcome
    /** Purchase tokens the store still reports as owned on this device, for "Restore purchase". */
    suspend fun ownedPurchaseTokens(context: Context): List<String>
}

object NoStorePurchases : InAppPurchaseProvider {
    override val available = false
    override suspend fun offers(context: Context): List<StoreOffer> = emptyList()
    override suspend fun purchase(activity: Activity, offer: StoreOffer, accountId: String): PurchaseOutcome =
        PurchaseOutcome.Failed("In-app purchases are not available in this build.")
    override suspend fun ownedPurchaseTokens(context: Context): List<String> = emptyList()
}
