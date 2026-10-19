package com.safenest.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder
import java.time.LocalDate

/** Whole days since [sinceEpochDay]; a date set in the future (clock change) shows 0, never a negative count. */
fun daysFree(sinceEpochDay: Long, todayEpochDay: Long): Long = (todayEpochDay - sinceEpochDay).coerceAtLeast(0)

/** Opens the signed-in support form on the website, prefilled. Null when [input] is not a valid domain or URL. */
fun siteReportUrl(input: String, wronglyBlocked: Boolean): String? {
    val host = DomainRules.normalize(input) ?: return null
    val subject = (if (wronglyBlocked) "Wrongly blocked: " else "Missed gambling site: ") + host
    val message = if (wronglyBlocked) "This site is blocked but is not a gambling site: $host"
        else "This gambling site is not blocked: $host"
    fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    return "https://mysafenestbd.com/account?topic=app&subject=${enc(subject.take(120))}&message=${enc(message)}#support"
}

private fun recoveryPrefs(c: Context) = c.getSharedPreferences("safenest_recovery", Context.MODE_PRIVATE)

/** Self-reported days free: counts from the first time it is shown, restarted only by the user. Stays on this device. */
@Composable fun DaysFreeCard(lang: String) {
    val t = { en: String, bn: String -> if (lang == "bn") bn else en }
    val prefs = recoveryPrefs(LocalContext.current)
    val today = LocalDate.now().toEpochDay()
    var since by remember { mutableLongStateOf(prefs.getLong("free_since_day", today).also {
        if (!prefs.contains("free_since_day")) prefs.edit().putLong("free_since_day", it).apply() }) }
    var confirm by remember { mutableStateOf(false) }
    val days = daysFree(since, today)
    Surface(shape = RoundedCornerShape(18.dp), color = Neon.Plum, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("DAYS FREE", "জুয়ামুক্ত দিন"), fontSize = 9.sp, letterSpacing = 1.sp, color = Neon.Muted, fontWeight = FontWeight.Bold)
            Text("$days", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Neon.Text)
            Text(t("Since ${LocalDate.ofEpochDay(since)}. Only you can restart this count.", "${LocalDate.ofEpochDay(since)} থেকে। শুধু আপনি এই গণনা আবার শুরু করতে পারেন।"), fontSize = 11.sp, color = Neon.Muted)
            TextButton(onClick = { confirm = true }) { Text(t("I gambled. Start again", "জুয়া খেলেছি। আবার শুরু করুন"), fontSize = 11.sp) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(t("Start again from today?", "আজ থেকে আবার শুরু করবেন?")) },
        text = { Text(t("A slip is part of many recoveries, not the end of one. Your protection stays exactly as it is.", "অনেকের পুনরুদ্ধারে এমন পিছলে পড়া ঘটে; এটি শেষ নয়। আপনার সুরক্ষা আগের মতোই থাকবে।"), fontSize = 12.sp) },
        confirmButton = { TextButton(onClick = { prefs.edit().putLong("free_since_day", today).apply(); since = today; confirm = false }) { Text(t("Start again", "আবার শুরু করুন")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Cancel", "বাতিল")) } })
}

/** Report a missed or wrongly blocked site through the website's signed-in support desk; reports are reviewed, never auto-applied. */
@Composable fun ReportSiteButton(lang: String) {
    val t = { en: String, bn: String -> if (lang == "bn") bn else en }
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var site by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    OutlinedButton(onClick = { open = true; error = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Text(t("Report a site", "একটি সাইট রিপোর্ট করুন"), fontSize = 10.sp)
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(t("Report a site", "একটি সাইট রিপোর্ট করুন")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = site, onValueChange = { site = it; error = "" }, singleLine = true,
                label = { Text(t("Website or link", "ওয়েবসাইট বা লিংক")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !wrong, onClick = { wrong = false }, label = { Text(t("Not blocked", "ব্লক হয়নি")) })
                FilterChip(selected = wrong, onClick = { wrong = true }, label = { Text(t("Wrongly blocked", "ভুলভাবে ব্লক")) })
            }
            Text(t("Opens your SafeNest account's support form with this filled in. Our team reviews every report before the list changes.", "আপনার SafeNest অ্যাকাউন্টের সহায়তা ফর্মে এটি পূরণ করে খুলবে। তালিকা বদলানোর আগে আমাদের দল প্রতিটি রিপোর্ট দেখে।"), fontSize = 11.sp, color = Neon.Muted)
            if (error.isNotEmpty()) Text(error, fontSize = 11.sp, color = Color(0xFFFF8A80))
        } },
        confirmButton = { TextButton(onClick = {
            val url = siteReportUrl(site, wrong)
            if (url == null) error = t("Enter a website such as example.com.", "example.com-এর মতো একটি ওয়েবসাইট লিখুন।")
            else try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); open = false; site = "" }
                catch (_: Exception) { error = t("No browser is available.", "কোনো ব্রাউজার পাওয়া যায়নি।") }
        }) { Text(t("Continue", "এগিয়ে যান")) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(t("Cancel", "বাতিল")) } })
}
