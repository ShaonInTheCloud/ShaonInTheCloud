package com.safenest.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun TestBuildBanner(language: String, active: Boolean, onStart: () -> Unit, onStop: () -> Unit, onTools: () -> Unit) {
    if (!LocalTestSession.enabled) return
    val t = { en: String, bn: String -> if(language == "bn") bn else en }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("SAFENEST TEST · No login needed", "SAFENEST TEST · লগইন লাগবে না"), style = MaterialTheme.typography.titleSmall)
            Text(t("Local testing only. Each session lasts up to 60 minutes; restart whenever needed.",
                "শুধু ফোনে পরীক্ষা। প্রতিবার সর্বোচ্চ ৬০ মিনিট চলে; প্রয়োজনে আবার চালু করুন।"), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) Button(onClick = onStop) { Text(t("Stop test", "পরীক্ষা বন্ধ করুন")) }
                else Button(onClick = onStart) { Text(t("Start test", "পরীক্ষা চালু করুন")) }
                TextButton(onClick = onTools) { Text(t("Test tools", "পরীক্ষার টুল")) }
            }
        }
    }
}

@Composable
fun TestLabScreen(language: String, onStart: () -> Unit, onSetup: () -> Unit, onRulesChanged: () -> Unit) {
    if (!LocalTestSession.enabled) return
    val context = LocalContext.current
    val t = { en: String, bn: String -> if(language == "bn") bn else en }
    val prefs = remember { context.getSharedPreferences("safenest_test_notes", Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf(prefs.getString("notes", "") ?: "") }
    var message by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf(SafeNestVpnService.dnsHealth.get()) }
    var running by remember { mutableStateOf(SafeNestVpnService.isRunning.get()) }
    var error by remember { mutableStateOf(SafeNestVpnService.lastError.get()) }
    var blocked by remember { mutableStateOf(SafeNestVpnService.lastBlockedHost.get()) }
    var sessionActive by remember { mutableStateOf(LocalTestSession.isActive(context)) }
    LaunchedEffect(context) {
        while (true) {
            dns = SafeNestVpnService.dnsHealth.get()
            running = SafeNestVpnService.isRunning.get()
            error = SafeNestVpnService.lastError.get()
            blocked = SafeNestVpnService.lastBlockedHost.get()
            sessionActive = LocalTestSession.isActive(context)
            delay(750)
        }
    }
    fun open(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { message = t("No browser is available.", "ব্রাউজার পাওয়া যায়নি।") }
    }
    Text(t("Try it. Shape what comes next.", "পরীক্ষা করুন। পরের পরিবর্তন বেছে নিন।"), style = MaterialTheme.typography.headlineSmall)
    Text(t("No account or payment is required in this test app. It has separate settings from SafeNest. Use Setup for optional app blocking; DNS filtering needs VPN permission only.",
        "এই পরীক্ষার অ্যাপে অ্যাকাউন্ট বা পেমেন্ট লাগবে না। SafeNest থেকে সেটিংস আলাদা। ঐচ্ছিক অ্যাপ ব্লকের জন্য সেটআপ ব্যবহার করুন; DNS ফিল্টারের জন্য শুধু VPN অনুমতি লাগে।"))
    Text(t("DNS service: ", "DNS সেবা: ") + if (running) t("Running", "চালু") else t("Stopped", "বন্ধ"))
    Text(t("DNS lookup status: ", "DNS অনুরোধের অবস্থা: ") + dns)
    Text(t("Last blocked request: ", "শেষ ব্লক অনুরোধ: ") + blocked.ifBlank { t("None yet", "এখনো নেই") })
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    if (!running) Button(onClick = onStart) { Text(t("Start or retry DNS test", "DNS পরীক্ষা চালু বা আবার চেষ্টা করুন")) }
    OutlinedButton(onClick = onSetup) { Text(t("Permissions and app blocking", "অনুমতি ও অ্যাপ ব্লক")) }
    Text(t("Harmless website test", "নিরাপদ ওয়েবসাইট পরীক্ষা"), style = MaterialTheme.typography.titleMedium)
    Text(t("Add example.org as a test rule, then open it. Keep example.com allowed. Close old tabs and retry if the browser uses a cached connection. A blocked DNS request should appear above.",
        "example.org পরীক্ষার নিয়ম হিসেবে যোগ করে খুলুন। example.com চালু রাখুন। ক্যাশ সংযোগ হলে পুরনো ট্যাব বন্ধ করে আবার চেষ্টা করুন। উপরে ব্লক DNS অনুরোধ দেখা উচিত।"))
    OutlinedButton(onClick = {
        RulesStore.add(context, RuleCategory.PERSONAL, "example.org")
        onRulesChanged()
        message = t("Test rule saved. Open the blocked test website next.", "পরীক্ষার নিয়ম সংরক্ষিত। এবার ব্লক পরীক্ষার ওয়েবসাইট খুলুন।")
    }) { Text(t("Add harmless test rule", "নিরাপদ পরীক্ষার নিয়ম যোগ করুন")) }
    OutlinedButton(onClick = { open("https://example.org") }) { Text(t("Open blocked test website", "ব্লক পরীক্ষার ওয়েবসাইট খুলুন")) }
    OutlinedButton(onClick = { open("https://example.com") }) { Text(t("Open allowed test website", "অনুমোদিত পরীক্ষার ওয়েবসাইট খুলুন")) }
    TextButton(enabled = !sessionActive, onClick = {
        RulesStore.remove(context, RuleCategory.PERSONAL, "example.org")
        onRulesChanged()
        message = t("Test rule removed from your custom rules.", "নিজের তালিকা থেকে পরীক্ষার নিয়ম সরানো হয়েছে।")
    }) { Text(t("Remove test rule after stopping", "বন্ধ করার পর পরীক্ষার নিয়ম সরান")) }
    OutlinedTextField(notes, onValueChange = { notes = it.take(2000) },
        label = { Text(t("Bugs and features you want", "সমস্যা ও যে ফিচার চান")) },
        modifier = Modifier.fillMaxWidth(), minLines = 3)
    Button(onClick = {
        prefs.edit().putString("notes", notes).apply()
        message = t("Notes saved on this phone.", "নোট ফোনে সংরক্ষিত।")
    }) { Text(t("Save notes", "নোট সংরক্ষণ করুন")) }
    OutlinedButton(onClick = {
        prefs.edit().putString("notes", notes).apply()
        val report = "SafeNest Test ${BuildConfig.VERSION_NAME}\nDNS running: $running\nDNS status: $dns\nError: $error\nNotes: $notes"
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SafeNest test feedback", report))
        message = t("Copied. Paste the report into this ChatGPT conversation.", "কপি হয়েছে। এই ChatGPT কথোপকথনে রিপোর্ট পেস্ট করুন।")
    }) { Text(t("Copy feedback for ChatGPT", "ChatGPT-র জন্য মতামত কপি করুন")) }
    if (message.isNotBlank()) Text(message)
}
