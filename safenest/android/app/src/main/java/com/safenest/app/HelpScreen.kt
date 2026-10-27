package com.safenest.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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

/** Help & support: crisis, gambling and debt help by country, plus practical steps. Dials, never calls directly. */
@Composable
fun HelpScreen(language: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    val edition = remember { context.getSharedPreferences("safenest_app", android.app.Activity.MODE_PRIVATE).getString("edition", "bd") ?: "bd" }
    var region by remember { mutableStateOf(if (edition == "global") "intl" else "bd") }
    fun open(c: HelpContent.Contact) {
        val intent = if (c.tel != null) Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.tel}")) else Intent(Intent.ACTION_VIEW, Uri.parse(c.url))
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(t("‹ Back", "‹ ফিরে যান"), fontSize = 12.sp) }
        Text(t("Help & support", "সাহায্য ও সহায়তা"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(t("Gambling problems are common and treatable. You do not have to deal with this alone.",
            "জুয়ার সমস্যা খুবই সাধারণ এবং এর চিকিৎসা আছে। এটা একা সামলাতে হবে না।"), fontSize = 12.sp)
        Text(t("Where are you?", "আপনি কোথায় আছেন?"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HelpContent.regions.forEach { (code, name) ->
                FilterChip(selected = region == code, onClick = { region = code }, label = { Text(name.of(language), fontSize = 11.sp) })
            }
        }
        HelpContent.sections(region).forEachIndexed { index, section ->
            Surface(shape = RoundedCornerShape(18.dp), color = if (index == 0) Neon.Pink else Neon.Plum) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.title.of(language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    section.body?.let { Text(it.of(language), fontSize = 12.sp) }
                    section.contacts.forEach { c ->
                        Surface(shape = RoundedCornerShape(12.dp), color = Neon.Night, modifier = Modifier.fillMaxWidth().clickable { open(c) }) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(if (c.tel != null) t("Call ${c.tel}", "কল ${c.tel}") else t("Open ›", "খুলুন ›"), fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, color = Neon.PinkSoft)
                                }
                                Text(c.detail.of(language), fontSize = 11.sp, color = Neon.Muted)
                            }
                        }
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = Neon.Raised) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Steps that help", "যে পদক্ষেপগুলো সাহায্য করে"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                HelpContent.steps.forEachIndexed { i, step -> Text("${i + 1}. ${step.of(language)}", fontSize = 12.sp) }
            }
        }
        Text(t("SafeNest is not a medical or crisis service. Numbers checked October 2026; hours can change.",
            "SafeNest কোনো চিকিৎসা বা জরুরি সেবা নয়। নম্বরগুলো অক্টোবর ২০২৬-এ যাচাই করা; সময় বদলাতে পারে।"), fontSize = 10.sp, color = Neon.Muted)
    }
}
