package com.safenest.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun SubscriptionScreen(language: String, onVerified: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    val committed = ProtectionCommitment.isActive(context)
    Text(t("Your paid protection period", "আপনার পেইড সুরক্ষার মেয়াদ"), style = MaterialTheme.typography.headlineSmall)
    if (committed) {
        Text(t("Protection is committed until ", "সুরক্ষা সক্রিয় থাকবে ") + Instant.ofEpochMilli(ProtectionCommitment.endsAt(context)).toString())
        Text(t("There is no in-app pause or stop during this period. Protection ends automatically at expiry. App blocking depends on your phone and enabled Accessibility permission.",
            "এই মেয়াদে অ্যাপে বিরতি বা বন্ধের বোতাম নেই। মেয়াদ শেষে সুরক্ষা স্বয়ংক্রিয়ভাবে শেষ হবে। সেটিংস শনাক্তকরণ আপনার ফোন ও Accessibility অনুমতির ওপর নির্ভর করে।"))
    } else {
        Text(t("Sign in to verify a subscription confirmed by SafeNest. Protection cannot start without active paid access. No password or session token is saved on this device.",
            "SafeNest-এ নিশ্চিত হওয়া সাবস্ক্রিপশন যাচাই করতে লগইন করুন। পেইড মেয়াদ ছাড়া সুরক্ষা চালু হবে না। পাসওয়ার্ড বা সেশন টোকেন এই ডিভাইসে সংরক্ষিত হয় না।"))
    }
    if (committed) Text(t("For technical support, you can check the server status of this period. Checking does not pause protection. Only a period ended or revoked by the server is released.",
        "প্রযুক্তিগত সহায়তার জন্য এই মেয়াদের সার্ভার অবস্থা যাচাই করতে পারেন। যাচাই করলে সুরক্ষা বন্ধ হয় না। সার্ভারে মেয়াদ শেষ বা অনুমতি প্রত্যাহার হলেই সুরক্ষা শেষ হয়।"))
    OutlinedTextField(email, { email = it }, label = { Text(t("Account email", "অ্যাকাউন্টের ইমেইল")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, label = { Text(t("Password", "পাসওয়ার্ড")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
        busy = true; message = ""
        scope.launch {
            try {
                if (committed) {
                    val id = checkNotNull(ProtectionCommitment.entitlementId(context))
                    val result = withContext(Dispatchers.IO) { SubscriptionClient.checkAccess(email, password, id) }
                    val released = withContext(Dispatchers.IO) { ProtectionCommitment.reconcile(context, result) }
                    message = if (released) t("The server ended this period. Protection has been released.", "সার্ভারে এই মেয়াদ শেষ হয়েছে। সুরক্ষা শেষ করা হয়েছে।")
                        else t("This paid period remains active. Protection continues.", "এই পেইড মেয়াদ এখনও সক্রিয়। সুরক্ষা চলবে।")
                } else {
                    val window = withContext(Dispatchers.IO) { SubscriptionClient.verify(email, password) }
                    withContext(Dispatchers.IO) { ProtectionCommitment.cacheVerified(context, window) }
                    onVerified()
                }
                password = ""
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message = error.message ?: t("Verification failed.", "যাচাই হয়নি।") }
            finally { busy = false; password = "" }
        }
    }) { Text(if (busy) t("Verifying…", "যাচাই হচ্ছে…") else if (committed) t("Check account status", "অ্যাকাউন্টের অবস্থা যাচাই করুন") else t("Verify paid access", "পেইড মেয়াদ যাচাই করুন")) }
    if (!committed) Text(t(if (BuildConfig.ALLOW_SYSTEM_GUARD) "Purchasing and payment confirmation still require the website's billing integration. This app cannot create a paid entitlement." else "This Play build is for existing SafeNest accounts. In-app purchases are not available. A server-verified active entitlement is required.",
        if (BuildConfig.ALLOW_SYSTEM_GUARD) "কেনা ও পেমেন্ট নিশ্চিত করার জন্য ওয়েবসাইটের বিলিং সংযোগ প্রয়োজন। অ্যাপ নিজে পেইড অনুমতি তৈরি করতে পারে না।" else "এই Play সংস্করণ বিদ্যমান SafeNest অ্যাকাউন্টের জন্য। অ্যাপে কেনা যায় না। সার্ভারে যাচাইকৃত সক্রিয় অনুমতি দরকার।"))
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    if (BuildConfig.ALLOW_SYSTEM_GUARD) TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mysafenestbd.com"))) } }) {
        Text(t("Open SafeNest website", "SafeNest ওয়েবসাইট খুলুন"))
    }
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mysafenestbd.com/privacy.html"))) } }) {
        Text(t("Privacy information", "গোপনীয়তার তথ্য"))
    }
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mysafenestbd.com/delete-account.html"))) } }) {
        Text(t("Delete my SafeNest account", "আমার SafeNest অ্যাকাউন্ট মুছুন"))
    }
}
