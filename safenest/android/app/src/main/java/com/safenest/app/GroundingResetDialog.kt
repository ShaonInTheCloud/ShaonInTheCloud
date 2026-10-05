package com.safenest.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun GroundingResetDialog(language: String, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val t = { en: String, bn: String -> if(language == "bn") bn else en }
    val end = remember { SystemClock.elapsedRealtime() + 60_000L }
    var remaining by remember { mutableLongStateOf(60) }
    LaunchedEffect(end) {
        while (remaining > 0) {
            remaining = ((end - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
            delay(200)
        }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(t("Take a 60-second pause", "৬০ সেকেন্ডের বিরতি নিন")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Put both feet on the floor. Breathe in slowly, then breathe out.", "দুই পা মেঝেতে রাখুন। ধীরে শ্বাস নিন, তারপর ছাড়ুন।"))
            Text(String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(t("Name five things you can see and one person you could reach out to.", "চোখে দেখা পাঁচটি জিনিস এবং যোগাযোগ করতে পারেন এমন একজনের নাম বলুন।"))
            Spacer(Modifier.height(4.dp))
        } },
        confirmButton = { TextButton(onClick = onComplete, enabled = remaining == 0L) { Text(t("Finish pause", "বিরতি শেষ করুন")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close", "বন্ধ করুন")) } })
}
