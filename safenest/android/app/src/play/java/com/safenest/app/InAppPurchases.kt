package com.safenest.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object InAppPurchases { val provider: InAppPurchaseProvider = PlayBillingProvider }

/**
 * Google Play Billing for the Play-distributed app. One subscription product
 * (`safenest_protection`) with base plans named after SafeNest plan codes.
 * Purchases are acknowledged by the SafeNest server after verification, never here,
 * so an unverified purchase is refunded by Google automatically within three days.
 */
object PlayBillingProvider : InAppPurchaseProvider, PurchasesUpdatedListener {
    const val PRODUCT_ID = "safenest_protection"
    private val PLAN_CODES = listOf("monthly", "quarterly", "annual")
    override val available = true

    @Volatile private var client: BillingClient? = null
    @Volatile private var pendingPurchase: CompletableDeferred<PurchaseOutcome>? = null
    @Volatile private var details: ProductDetails? = null

    private suspend fun connected(context: Context): BillingClient {
        client?.takeIf { it.isReady }?.let { return it }
        val fresh = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(com.android.billingclient.api.PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        val result = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<BillingResult> { continuation ->
                fresh.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) { if (continuation.isActive) continuation.resume(billingResult) }
                    override fun onBillingServiceDisconnected() { client = null }
                })
            }
        } ?: throw AccessException(AccessErrors.OFFLINE)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw AccessException(AccessErrors.SERVER)
        client = fresh
        return fresh
    }

    override suspend fun offers(context: Context): List<StoreOffer> {
        val billing = connected(context)
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build())).build()
        val result = billing.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw AccessException(AccessErrors.SERVER)
        val product = result.productDetailsList?.firstOrNull { it.productId == PRODUCT_ID } ?: return emptyList()
        details = product
        // One offer per base plan; take the cheapest (introductory) offer token Play exposes for it.
        return PLAN_CODES.mapNotNull { plan ->
            val offer = product.subscriptionOfferDetails.orEmpty()
                .filter { it.basePlanId == plan }
                .minByOrNull { it.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE } ?: return@mapNotNull null
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            StoreOffer(plan, product.title, phase.formattedPrice, phase.billingPeriod, offer.offerToken)
        }
    }

    override suspend fun purchase(activity: Activity, offer: StoreOffer, accountId: String): PurchaseOutcome {
        val billing = connected(activity)
        val product = details ?: run { offers(activity); details } ?: return PurchaseOutcome.Failed("Plans are not available right now.")
        val waiting = CompletableDeferred<PurchaseOutcome>()
        pendingPurchase?.cancel()
        pendingPurchase = waiting
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).setOfferToken(offer.offerToken).build()))
            // Binds the purchase to the SafeNest account so the server can refuse tokens bought under another account.
            .setObfuscatedAccountId(accountId)
            .build()
        val launched = billing.launchBillingFlow(activity, params)
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return PurchaseOutcome.Failed(describe(launched))
        }
        return waiting.await()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val waiting = pendingPurchase ?: return
        pendingPurchase = null
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases.orEmpty().firstOrNull { PRODUCT_ID in it.products }
                when {
                    purchase == null -> PurchaseOutcome.Failed("Google Play did not return the purchase.")
                    purchase.purchaseState == Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending(purchase.purchaseToken)
                    else -> PurchaseOutcome.Purchased(purchase.purchaseToken)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.Failed("You already own this plan. Use Restore purchase.")
            else -> PurchaseOutcome.Failed(describe(result))
        }
        waiting.complete(outcome)
    }

    override suspend fun ownedPurchaseTokens(context: Context): List<String> {
        val billing = connected(context)
        val result = billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) throw AccessException(AccessErrors.SERVER)
        return result.purchasesList.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .map { it.purchaseToken }
    }

    private fun describe(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Google Play Billing is unavailable on this device or account."
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, BillingClient.BillingResponseCode.NETWORK_ERROR -> "Google Play could not be reached. Check the internet and try again."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan is not available in your country yet."
        else -> "Google Play purchase failed (code ${result.responseCode})."
    }
}
