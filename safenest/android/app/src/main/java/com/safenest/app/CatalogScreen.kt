package com.safenest.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Publisher setup is explicit; no unverified internet blocklist is installed automatically. */
@Composable
fun CatalogScreen(language: String, onChanged: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    var status by remember { mutableStateOf<CatalogStatus?>(null) }
    var source by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var confirmTrust by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun operation(action: () -> CatalogStatus) {
        if (busy || loading) return
        busy = true
        message = ""
        scope.launch {
            try {
                status = withContext(Dispatchers.IO) { action() }
                onChanged()
                message = t("Catalog settings checked. See the revision and status below.", "ক্যাটালগ যাচাই হয়েছে। নিচের সংস্করণ ও অবস্থা দেখুন।")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                message = error.message ?: t("Update failed. Existing verified rules remain active.", "আপডেট হয়নি। আগের যাচাইকৃত তালিকা চালু আছে।")
                try { status = withContext(Dispatchers.IO) { CatalogStore.status(context) } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* Keep the last displayed status and the original error. */ }
            } finally { busy = false }
        }
    }

    val importSigned = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) operation {
            context.contentResolver.openInputStream(uri)?.use { CatalogStore.importSigned(context, it) }
                ?: error("Could not open the signed catalog.")
        }
    }

    LaunchedEffect(Unit) {
        try {
            val snapshot = withContext(Dispatchers.IO) { CatalogStore.configuration(context) to CatalogStore.status(context) }
            source = snapshot.first.sourceUrl
            key = snapshot.first.publicKeyPem
            status = snapshot.second
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { message = error.message ?: "Could not read catalog configuration." }
        finally { loading = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("Verified website lists", "যাচাইকৃত ওয়েবসাইট তালিকা"), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(t("Fresh lists help cover new sites. SafeNest verifies the publisher's signature before using an update. This does not make the list complete or correctly classified by itself.", "নতুন সাইট ধরতে হালনাগাদ তালিকা প্রয়োজন। ব্যবহারের আগে SafeNest প্রকাশকের ডিজিটাল স্বাক্ষর যাচাই করে। স্বাক্ষর তালিকাটি সম্পূর্ণ বা নির্ভুল প্রমাণ করে না।"), fontSize = 12.sp)
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val current = status
                Text(when {
                    loading -> t("Reading catalog…", "ক্যাটালগ পড়া হচ্ছে…")
                    current?.revision == null -> t("Starter and personal rules only", "শুধু প্রাথমিক ও ব্যক্তিগত তালিকা")
                    current.stale -> t("Catalog is overdue for an update", "ক্যাটালগ হালনাগাদের সময় পেরিয়েছে")
                    else -> t("Verified catalog installed", "যাচাইকৃত ক্যাটালগ ইনস্টল আছে")
                }, fontWeight = FontWeight.Bold)
                if (current?.revision != null) {
                    Text(t("Revision: ${current.revision}", "সংস্করণ: ${current.revision}"), fontSize = 12.sp)
                    Text(t("Expires: ${current.expiresAt}", "মেয়াদ: ${current.expiresAt}"), fontSize = 11.sp)
                    Text(t("Issued: ${current.issuedAt}", "প্রকাশ: ${current.issuedAt}"), fontSize = 11.sp)
                }
                current?.lastCheckedAt?.let { Text(t("Last check: $it", "শেষ পরীক্ষা: $it"), fontSize = 11.sp) }
                if (current?.stale == true) Text(t("The last verified rules remain active. New sites may be missing.", "আগের যাচাইকৃত তালিকা চালু আছে। নতুন সাইট বাদ পড়তে পারে।"), fontSize = 12.sp)
                if (current?.trustChanged == true) Text(t("The previous verified catalog remains active until an update from the new publisher is accepted.", "নতুন প্রকাশকের আপডেট গ্রহণ না হওয়া পর্যন্ত আগের যাচাইকৃত ক্যাটালগ চালু থাকবে।"), fontSize = 12.sp)
                if (current?.configured != true) Text(t("No publisher is connected. Your provider must supply a public verification key and, for online updates, an HTTPS address. No live catalog service is bundled.", "কোনো প্রকাশক যুক্ত নেই। সেবাদাতাকে যাচাইয়ের পাবলিক কী এবং অনলাইন আপডেটের জন্য HTTPS ঠিকানা দিতে হবে। লাইভ ক্যাটালগ সেবা যুক্ত করা নেই।"), fontSize = 12.sp)
                current?.keyFingerprint?.let { Text(t("Trusted key SHA-256: $it", "বিশ্বস্ত কী SHA-256: $it"), fontSize = 10.sp) }
                current?.lastError?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Button(onClick = { operation { CatalogStore.refresh(context) } }, enabled = !loading && !busy && current?.configured == true && !current.sourceUrl.isBlank()) { Text(t("Check for a signed update", "স্বাক্ষরযুক্ত আপডেট দেখুন")) }
                OutlinedButton(onClick = { importSigned.launch(arrayOf("*/*")) }, enabled = !loading && !busy && current?.configured == true) { Text(t("Import signed catalog file", "স্বাক্ষরযুক্ত ক্যাটালগ আমদানি")) }
            }
        }
        Text(t("Updates are checked when you tap the button. There is no background scheduler in this version. Catalog rules are separate from your personal list.", "বোতাম চাপলে আপডেট দেখা হয়। এই সংস্করণে স্বয়ংক্রিয় পটভূমি আপডেট নেই। ক্যাটালগের তালিকা ব্যক্তিগত তালিকা থেকে আলাদা।"), fontSize = 11.sp)
        TextButton(onClick = { advanced = !advanced }, enabled = !loading && !busy && !ProtectionCommitment.isActive(context)) { Text(t("Publisher setup", "প্রকাশক সেটআপ")) }
        if (advanced) {
            OutlinedTextField(source, onValueChange = { source = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(t("HTTPS update URL (optional for offline files)", "HTTPS আপডেট ঠিকানা (অফলাইনে ঐচ্ছিক)")) })
            OutlinedTextField(key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy, minLines = 4, maxLines = 8, label = { Text(t("Publisher PUBLIC verification key (PEM)", "প্রকাশকের PUBLIC যাচাই কী (PEM)")) })
            Text(t("Use only the public key. Keep the signing private key off customer phones. Confirm the public-key fingerprint with your provider through a trusted channel.", "শুধু পাবলিক কী দিন। সই করার প্রাইভেট কী গ্রাহকের ফোনে রাখবেন না। বিশ্বস্ত মাধ্যমে সেবাদাতার সঙ্গে কী-এর ফিঙ্গারপ্রিন্ট মিলিয়ে নিন।"), fontSize = 11.sp)
            Button(onClick = { confirmTrust = true }, enabled = !busy && key.isNotBlank() && !ProtectionCommitment.isActive(context)) { Text(t("Review publisher trust", "প্রকাশকের বিশ্বাসযোগ্যতা পর্যালোচনা")) }
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        if (message.isNotBlank()) Text(message, fontSize = 12.sp)
        TextButton(onClick = onBack, enabled = !busy) { Text(t("Back to protection", "সুরক্ষায় ফিরুন")) }
    }
    if (confirmTrust) AlertDialog(
        onDismissRequest = { confirmTrust = false },
        title = { Text(t("Trust this publisher?", "এই প্রকাশককে বিশ্বাস করবেন?")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("This key authorizes future gambling/adult blocklist updates. A wrong or malicious publisher can block ordinary websites. Updating sends a request to the configured HTTPS host, which can see your IP address; SafeNest does not attach your browsing history.", "এই কী ভবিষ্যতের জুয়া/প্রাপ্তবয়স্ক তালিকার আপডেট অনুমোদন করবে। ভুল বা ক্ষতিকর প্রকাশক সাধারণ সাইটও ব্লক করতে পারে। আপডেটের সময় HTTPS হোস্ট আপনার IP দেখতে পারে; SafeNest ব্রাউজিং ইতিহাস পাঠায় না।"))
            Text(t("Existing verified rules stay active until a newer signed catalog is accepted. This setup does not start a subscription or create an online account.", "নতুন স্বাক্ষরযুক্ত ক্যাটালগ গ্রহণ না হওয়া পর্যন্ত আগের যাচাইকৃত তালিকা চালু থাকবে। এতে সাবস্ক্রিপশন বা অনলাইন অ্যাকাউন্ট তৈরি হয় না।"))
        } },
        confirmButton = { TextButton(onClick = { confirmTrust = false; operation { CatalogStore.configure(context, source.trim(), key.trim()) } }) { Text(t("Trust public key", "পাবলিক কী বিশ্বাস করুন")) } },
        dismissButton = { TextButton(onClick = { confirmTrust = false }) { Text(t("Cancel", "বাতিল")) } }
    )
}
