package com.safenest.app

import android.app.Activity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Play subscription purchase and restore. Flow: sign in (short-lived, never
 * saved) → Play purchase bound to the account id → server verification → cached
 * verified window → separate protection consent on the setup screen.
 */
@Composable
fun StorePurchaseSection(language: String, email: String, password: String, busy: Boolean,
                         onBusy: (Boolean) -> Unit, onMessage: (String) -> Unit, onVerified: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = InAppPurchases.provider
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    var offers by remember { mutableStateOf<List<StoreOffer>>(emptyList()) }
    var loadError by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("monthly") }
    var running by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try { offers = withContext(Dispatchers.IO) { store.offers(context) }; loadError = "" }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { loadError = t("Google Play prices could not be loaded. Check the internet and reopen this screen.", "Google Play-এর মূল্য লোড হয়নি। ইন্টারনেট দেখে এই পৃষ্ঠা আবার খুলুন।") }
    }
    fun explain(error: Exception): String = when ((error as? AccessException)?.reason) {
        AccessErrors.WRONG_PASSWORD -> t("Email or password is incorrect.", "ইমেইল বা পাসওয়ার্ড ভুল।")
        AccessErrors.EMAIL_NOT_CONFIRMED -> t("Confirm your email first, then try again.", "আগে ইমেইল নিশ্চিত করুন, তারপর আবার চেষ্টা করুন।")
        AccessErrors.OFFLINE -> t("No connection. Check the internet and try again.", "সংযোগ নেই। ইন্টারনেট দেখে আবার চেষ্টা করুন।")
        AccessErrors.TOO_MANY -> t("Too many attempts. Wait a minute.", "অনেকবার চেষ্টা হয়েছে। এক মিনিট অপেক্ষা করুন।")
        "purchase_bound_to_other_account" -> t("This Google Play purchase belongs to a different SafeNest account. Sign in with that account.", "এই Google Play ক্রয় অন্য SafeNest অ্যাকাউন্টের। সেই অ্যাকাউন্টে লগইন করুন।")
        "purchase_not_found" -> t("Google Play has no record of this purchase yet. Wait a minute and use Restore purchase.", "Google Play-এ এই ক্রয়ের রেকর্ড এখনো নেই। এক মিনিট পর Restore purchase চাপুন।")
        else -> error.message?.takeIf { it.isNotBlank() } ?: t("The purchase could not be verified.", "ক্রয় যাচাই করা যায়নি।")
    }
    suspend fun verifyTokens(session: SubscriptionClient.Session, tokens: List<String>): Boolean {
        var pending = false
        for (token in tokens) {
            val result = withContext(Dispatchers.IO) { SubscriptionClient.verifyPlayPurchase(session, token) }
            if (result == null) { pending = true; continue }
            val window = result.window ?: continue
            withContext(Dispatchers.IO) { ProtectionCommitment.cacheVerified(context, window) }
            return true
        }
        onMessage(if (pending) t("Google Play is still completing your payment. Protection starts once it is confirmed; use Restore purchase later.", "Google Play আপনার পেমেন্ট সম্পন্ন করছে। নিশ্চিত হলে সুরক্ষা শুরু হবে; পরে Restore purchase চাপুন।")
            else t("No active SafeNest subscription was found on this Google account.", "এই Google অ্যাকাউন্টে সক্রিয় SafeNest সাবস্ক্রিপশন পাওয়া যায়নি।"))
        return false
    }

    Text(t("Subscribe with Google Play", "Google Play দিয়ে সাবস্ক্রাইব করুন"), style = MaterialTheme.typography.titleMedium)
    Text(t("Choose how long you want to be protected. The subscription renews until you cancel it in Google Play; protection stays locked for each paid period and cannot be paused inside SafeNest.",
        "কতদিন সুরক্ষা চান বেছে নিন। Google Play-তে বাতিল না করা পর্যন্ত সাবস্ক্রিপশন নবায়ন হয়; প্রতিটি পেইড মেয়াদে সুরক্ষা লক থাকে এবং SafeNest-এ বিরতি দেওয়া যায় না।"),
        style = MaterialTheme.typography.bodySmall)
    if (loadError.isNotBlank()) Text(loadError, color = MaterialTheme.colorScheme.error)
    offers.forEach { offer ->
        val label = when (offer.planCode) {
            "monthly" -> t("Monthly", "মাসিক"); "quarterly" -> t("Three months", "তিন মাস"); "annual" -> t("Yearly", "বার্ষিক"); else -> offer.planCode
        }
        Row { RadioButton(selected = selected == offer.planCode, enabled = !busy, onClick = { selected = offer.planCode }); Text("$label · ${offer.formattedPrice}") }
    }
    val ready = !busy && email.isNotBlank() && password.isNotEmpty() && offers.any { it.planCode == selected }
    if (!busy && offers.isNotEmpty() && (email.isBlank() || password.isEmpty())) Text(
        t("Enter your SafeNest account email and password above first; the purchase is tied to that account.", "আগে ওপরে SafeNest অ্যাকাউন্টের ইমেইল ও পাসওয়ার্ড দিন; ক্রয় সেই অ্যাকাউন্টের সাথে যুক্ত হয়।"),
        style = MaterialTheme.typography.bodySmall)
    Button(enabled = ready, onClick = {
        val offer = offers.first { it.planCode == selected }
        val activity = context as? Activity ?: return@Button
        onBusy(true); onMessage(""); running = "buy"
        scope.launch {
            try {
                val session = withContext(Dispatchers.IO) { SubscriptionClient.signIn(email, password) }
                when (val outcome = store.purchase(activity, offer, session.userId)) {
                    is PurchaseOutcome.Purchased -> if (verifyTokens(session, listOf(outcome.purchaseToken))) onVerified()
                    is PurchaseOutcome.Pending -> onMessage(t("Google Play accepted the order; protection starts when the payment completes. Use Restore purchase afterwards.", "Google Play অর্ডার গ্রহণ করেছে; পেমেন্ট সম্পন্ন হলে সুরক্ষা শুরু হবে। পরে Restore purchase চাপুন।"))
                    PurchaseOutcome.Cancelled -> onMessage(t("Purchase cancelled.", "ক্রয় বাতিল হয়েছে।"))
                    is PurchaseOutcome.Failed -> onMessage(outcome.message)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { onMessage(explain(error)) }
            finally { onBusy(false); running = "" }
        }
    }) { Text(if (running == "buy") t("Opening Google Play…", "Google Play খুলছে…") else t("Subscribe", "সাবস্ক্রাইব করুন")) }
    OutlinedButton(enabled = !busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
        onBusy(true); onMessage(""); running = "restore"
        scope.launch {
            try {
                val session = withContext(Dispatchers.IO) { SubscriptionClient.signIn(email, password) }
                val tokens = withContext(Dispatchers.IO) { store.ownedPurchaseTokens(context) }
                if (verifyTokens(session, tokens)) onVerified()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { onMessage(explain(error)) }
            finally { onBusy(false); running = "" }
        }
    }) { Text(if (running == "restore") t("Checking Google Play…", "Google Play যাচাই হচ্ছে…") else t("Restore purchase", "ক্রয় পুনরুদ্ধার করুন")) }
}
