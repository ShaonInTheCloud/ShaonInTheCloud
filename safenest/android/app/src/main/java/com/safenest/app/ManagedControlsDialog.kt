package com.safenest.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ManagedDialogSnapshot(val owner: Boolean, val active: Boolean, val hasCode: Boolean)
private enum class ManagedDialogAction { APPLY, RELEASE }
private data class ManagedDialogOutcome(val message: String, val result: ManagedProtectionResult? = null)

private fun managedText(language: String, english: String, bangla: String) = if (language == "bn") bangla else english

/** All credentials remain in temporary, non-saveable memory. Policy/crypto operations run on IO. */
@Composable
fun ManagedControlsDialog(
    language: String,
    protectionRunning: Boolean,
    dnsHealthy: Boolean,
    lockdown: Boolean,
    onDismiss: () -> Unit,
    onResult: (ManagedProtectionResult) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val t = { english: String, bangla: String -> managedText(language, english, bangla) }
    var snapshot by remember { mutableStateOf<ManagedDialogSnapshot?>(null) }
    var busy by remember { mutableStateOf(true) }
    var action by remember { mutableStateOf(ManagedDialogAction.APPLY) }
    var code by remember { mutableStateOf("") }
    var repeatedCode by remember { mutableStateOf("") }
    var codeStored by remember { mutableStateOf(false) }
    var browsingTested by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            snapshot = withContext(Dispatchers.IO) { managedDialogSnapshot(context) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            message = t("SafeNest could not read the managed settings. Close this dialog and retry.", "SafeNest পরিচালিত সেটিংস পড়তে পারেনি। এই ডায়ালগ বন্ধ করে আবার চেষ্টা করুন।")
        } finally { busy = false }
    }

    val current = snapshot
    val applying = action == ManagedDialogAction.APPLY
    val newCodeNeeded = applying && current?.hasCode == false
    val appGuardReady = GuardPreferences.isEnabled(context) && GuardPreferences.isAccessibilityEnabled(context)
    val readyForApply = browsingTested && protectionRunning && dnsHealthy && !lockdown && appGuardReady
    val credentialsReady = when {
        current == null -> false
        current.hasCode -> code.isNotEmpty()
        newCodeNeeded -> code.length in 12..128 && repeatedCode == code && codeStored
        else -> true // Existing legacy policies retain their original code-free recovery path.
    }
    val canSubmit = !busy && current?.owner == true && credentialsReady &&
        (if (applying) readyForApply else current.active || current.hasCode)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(t("Managed protection controls", "পরিচালিত সুরক্ষার নিয়ন্ত্রণ")) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(t(
                    "For a phone enrolled with SafeNest as device owner. These Android policies make changes harder; they do not make the phone permanently impossible to reset or bypass.",
                    "ফোনে SafeNest-কে device owner হিসেবে নিবন্ধন করা থাকতে হবে। Android-এর এই নীতিগুলো পরিবর্তন কঠিন করে; ফোন রিসেট বা সুরক্ষা এড়িয়ে যাওয়া চিরতরে অসম্ভব করে না।"
                ), fontSize = 12.sp)
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                if (current != null) {
                    Text(when {
                        !current.owner -> t("Device-owner enrollment is missing. A normal administrator permission is not enough.", "Device-owner নিবন্ধন নেই। সাধারণ administrator অনুমতি যথেষ্ট নয়।")
                        current.active && current.hasCode -> t("Managed controls are present. The administrator recovery code is required for repair or release.", "পরিচালিত নিয়ন্ত্রণ আছে। মেরামত বা সরাতে administrator recovery code প্রয়োজন।")
                        current.active -> t("Legacy installation: existing controls have no recovery code. Apply repair to add one.", "পুরোনো সংস্করণ: বর্তমান নিয়ন্ত্রণে recovery code নেই। মেরামত প্রয়োগ করে কোড যোগ করুন।")
                        current.hasCode -> t("A saved administrator code exists. Verify it to apply controls.", "সংরক্ষিত administrator code আছে। নিয়ন্ত্রণ প্রয়োগ করতে যাচাই করুন।")
                        else -> t("Managed controls have not been applied. Set up a recovery code before continuing.", "পরিচালিত নিয়ন্ত্রণ প্রয়োগ করা হয়নি। এগোনোর আগে recovery code তৈরি করুন।")
                    }, fontSize = 12.sp)
                    if (current.owner) {
                        FilterChip(
                            selected = applying, enabled = !busy,
                            onClick = { action = ManagedDialogAction.APPLY; code = ""; repeatedCode = ""; message = null },
                            label = { Text(t("Apply / repair controls", "নিয়ন্ত্রণ প্রয়োগ / মেরামত")) }
                        )
                        if (applying) {
                            Text(t(
                                "This applies always-on SafeNest without Lockdown; restricts VPN/Private DNS changes where supported; limits unknown-source installation; suspends detected VPN apps; blocks SafeNest uninstall; and applies supported Chrome policies. Existing settings are saved for recovery. Repair may roll back SafeNest policies if setup fails.",
                                "এতে Lockdown ছাড়া SafeNest always-on হবে; সমর্থিত ফোনে VPN/Private DNS পরিবর্তন ও অজানা উৎসের ইনস্টল সীমিত হবে; শনাক্ত VPN অ্যাপ স্থগিত হবে; SafeNest আনইনস্টল বন্ধ হবে; এবং সমর্থিত Chrome নীতি প্রয়োগ হবে। পুনরুদ্ধারের জন্য আগের সেটিংস রাখা হবে। মেরামত ব্যর্থ হলে SafeNest নীতি ফিরিয়ে নেওয়া হতে পারে।"
                            ), fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.Top) {
                                Checkbox(checked = browsingTested, enabled = !busy, onCheckedChange = { browsingTested = it })
                                Text(t(
                                    "I tested this build: a normal website loads and a website on my blocklist does not.",
                                    "এই সংস্করণে পরীক্ষা করেছি: সাধারণ ওয়েবসাইট খোলে এবং আমার ব্লক তালিকার ওয়েবসাইট খোলে না।"
                                ), fontSize = 12.sp)
                            }
                            if (!protectionRunning || !dnsHealthy || lockdown || !appGuardReady) Text(t(
                                "Before applying: start protection, test normal DNS, grant Accessibility and enable app guard, and leave Block connections without VPN OFF.",
                                "প্রয়োগের আগে: সুরক্ষা চালু করে সাধারণ DNS পরীক্ষা করুন, Accessibility ও অ্যাপ গার্ড চালু করুন, এবং ‘Block connections without VPN’ বন্ধ রাখুন।"
                            ), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        } else {
                            Text(t(
                                "Release restores settings changed by SafeNest and resumes VPN apps it suspended. Blocking may then be easier to disable. Partial recovery keeps the code so you can retry.",
                                "নিয়ন্ত্রণ সরালে SafeNest বদলানো সেটিংস ফিরবে এবং স্থগিত VPN অ্যাপ আবার চলবে। এরপর সুরক্ষা বন্ধ করা সহজ হতে পারে। পুনরুদ্ধার অসম্পূর্ণ হলে আবার চেষ্টা করার জন্য কোড রাখা হবে।"
                            ), fontSize = 12.sp)
                        }
                        if (current.hasCode || newCodeNeeded) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { if (it.length <= 128) code = it },
                                enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(if (newCodeNeeded) t("New recovery code (12–128 characters)", "নতুন recovery code (১২–১২৮ অক্ষর)") else t("Administrator recovery code", "Administrator recovery code")) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                        }
                        if (newCodeNeeded) {
                            OutlinedTextField(
                                value = repeatedCode,
                                onValueChange = { if (it.length <= 128) repeatedCode = it },
                                enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(t("Retype the recovery code", "Recovery code আবার লিখুন")) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            Text(t(
                                "Use a unique passphrase and keep a copy outside this phone, ideally with a trusted person. SafeNest stores a salted verifier, not a readable copy. A lost code cannot be shown or reset from this screen. The code is independent of payments or subscriptions.",
                                "আলাদা একটি passphrase ব্যবহার করুন এবং এই ফোনের বাইরে কপি রাখুন—সম্ভব হলে বিশ্বস্ত কারও কাছে। SafeNest পড়া যায় এমন কোডের বদলে salted verifier রাখে। হারানো কোড এই স্ক্রিনে দেখা বা রিসেট করা যায় না। কোডের সঙ্গে পেমেন্ট বা সাবস্ক্রিপশনের সম্পর্ক নেই।"
                            ), fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.Top) {
                                Checkbox(checked = codeStored, enabled = !busy, onCheckedChange = { codeStored = it })
                                Text(t("I saved the code safely and understand how recovery works.", "কোড নিরাপদে রেখেছি এবং পুনরুদ্ধারের নিয়ম বুঝেছি।"), fontSize = 12.sp)
                            }
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = {
                // Capture temporary input for this authorized operation; never persist plaintext.
                val entered = code
                val selected = action
                if (selected == ManagedDialogAction.APPLY && (!readyForApply || (newCodeNeeded && (!codeStored || code != repeatedCode)))) return@TextButton
                busy = true
                message = null
                scope.launch {
                    try {
                        // Finish the recovery journal/credential transaction even if the activity rotates.
                        val outcome = withContext(NonCancellable + Dispatchers.IO) {
                            performManagedDialogOperation(context, selected, entered, language)
                        }
                        code = ""
                        repeatedCode = ""
                        codeStored = false
                        snapshot = withContext(Dispatchers.IO) { managedDialogSnapshot(context) }
                        message = outcome.message
                        outcome.result?.let(onResult)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        snapshot = null
                        message = t("The operation could not be completed or read back. Close and reopen this dialog to check the saved recovery state before retrying.", "কাজটি শেষ করা বা ফল পড়া যায়নি। আবার চেষ্টা করার আগে ডায়ালগ বন্ধ করে খুলে সংরক্ষিত পুনরুদ্ধারের অবস্থা দেখুন।")
                    } finally {
                        code = ""
                        repeatedCode = ""
                        busy = false
                    }
                }
            }) {
                Text(t("Apply / repair", "প্রয়োগ / মেরামত"))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(t("Close", "বন্ধ করুন")) } }
    )
}

private fun managedDialogSnapshot(context: Context) = ManagedDialogSnapshot(
    ManagedProtection.isDeviceOwner(context),
    ManagedProtection.isConfigured(context),
    ManagedRecovery.configured(context)
)

/** Called only on IO. The durable policy journal remains authoritative after interruption. */
@Synchronized
private fun performManagedDialogOperation(
    context: Context, action: ManagedDialogAction, code: String, language: String
): ManagedDialogOutcome {
    val t = { english: String, bangla: String -> managedText(language, english, bangla) }
    if (!ManagedProtection.isDeviceOwner(context)) return ManagedDialogOutcome(t(
        "Device-owner enrollment is required. No settings were changed.", "Device-owner নিবন্ধন প্রয়োজন। কোনো সেটিংস বদলানো হয়নি।"
    ))
    val hasCode = ManagedRecovery.configured(context)
    if (hasCode) {
        val verification = ManagedRecovery.verify(context, code)
        if (!verification.success) return ManagedDialogOutcome(
            t("The recovery code could not be verified. ", "Recovery code যাচাই করা যায়নি। ") + verification.message +
                if (verification.retryAfterSeconds > 0) t(" Retry in ${verification.retryAfterSeconds} seconds.", " ${verification.retryAfterSeconds} সেকেন্ড পরে চেষ্টা করুন।") else ""
        )
    }
    if (action == ManagedDialogAction.APPLY) {
        if (!GuardPreferences.isEnabled(context) || !GuardPreferences.isAccessibilityEnabled(context)) {
            return ManagedDialogOutcome(t("Enable app guard and grant Accessibility before applying managed controls.", "পরিচালিত নিয়ন্ত্রণ প্রয়োগের আগে অ্যাপ গার্ড ও Accessibility চালু করুন।"))
        }
        if (!SafeNestVpnService.isRunning.get() || SafeNestVpnService.dnsHealth.get() != "ok" || SafeNestVpnService.lockdownEnabled.get()) {
            return ManagedDialogOutcome(t("Protection status changed. Retest normal browsing before applying policies.", "সুরক্ষার অবস্থা বদলেছে। নীতি প্রয়োগের আগে সাধারণ ব্রাউজিং আবার পরীক্ষা করুন।"))
        }
        if (!hasCode) {
            val stored = ManagedRecovery.set(context, code)
            if (!stored.success) return ManagedDialogOutcome(t("The recovery code was not saved. ", "Recovery code সংরক্ষণ হয়নি। ") + stored.message)
        }
    } else if (!hasCode && !ManagedProtection.isConfigured(context)) {
        return ManagedDialogOutcome(t("There are no legacy controls to release.", "সরানোর মতো পুরোনো নিয়ন্ত্রণ নেই।"))
    }

    val result = try {
        if (action == ManagedDialogAction.APPLY) ManagedProtection.apply(context) else ManagedProtection.release(context)
    } catch (_: Exception) {
        // Never erase a credential if readback is uncertain or any policy footprint remains.
        val active = try { ManagedProtection.isConfigured(context) } catch (_: Exception) { true }
        if (!active && ManagedRecovery.configured(context)) ManagedRecovery.clear(context)
        return ManagedDialogOutcome(t(
            "Android could not complete the operation. Reopen the controls and retry; any remaining managed settings retain their recovery code.",
            "Android কাজটি শেষ করতে পারেনি। নিয়ন্ত্রণ আবার খুলে চেষ্টা করুন; কোনো পরিচালিত সেটিংস অবশিষ্ট থাকলে তার recovery code রাখা হয়েছে।"
        ), ManagedProtectionResult(active))
    }
    val remaining = result.managed || ManagedProtection.isConfigured(context)
    val credentialCleared = remaining || !ManagedRecovery.configured(context) || ManagedRecovery.clear(context)
    val status = when {
        !credentialCleared -> t("Policies were released, but the saved recovery verifier could not be removed. Retry release with the same code.", "নীতি সরানো হয়েছে, কিন্তু সংরক্ষিত recovery verifier মুছতে পারেনি। একই কোড দিয়ে আবার সরানোর চেষ্টা করুন।")
        action == ManagedDialogAction.RELEASE && remaining -> t("Recovery is incomplete. The code is retained; retry release. ", "পুনরুদ্ধার অসম্পূর্ণ। কোড রাখা হয়েছে; আবার সরানোর চেষ্টা করুন। ")
        action == ManagedDialogAction.RELEASE -> t("Managed controls were released and their recovery code was removed.", "পরিচালিত নিয়ন্ত্রণ এবং তার recovery code সরানো হয়েছে।")
        !remaining -> t("Controls were not applied or were rolled back. No managed policy footprint remains.", "নিয়ন্ত্রণ প্রয়োগ হয়নি বা ফিরিয়ে নেওয়া হয়েছে। পরিচালিত নীতির কোনো অবশিষ্ট অংশ নেই।")
        else -> t("Managed policy operation finished. Check the reported status and test normal and blocked browsing again. ", "পরিচালিত নীতির কাজ শেষ হয়েছে। দেখানো অবস্থা যাচাই করে সাধারণ ও ব্লক করা ব্রাউজিং আবার পরীক্ষা করুন। ")
    }
    return ManagedDialogOutcome(status + result.message?.let { "\n$it" }.orEmpty(), result)
}
