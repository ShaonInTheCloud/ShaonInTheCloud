package com.safenest.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeviceOwnerSetupDialog(language: String, onDismiss: () -> Unit) {
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Enroll a test device", "পরীক্ষার ডিভাইস নিবন্ধন")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("A VPN profile is permission to filter traffic. Device Owner is a separate Android provisioning step. A work profile protects only that profile; SafeNest requires full-device owner enrollment.", "VPN প্রোফাইল ট্রাফিক ফিল্টারের অনুমতি। Device Owner হলো Android-এর আলাদা নিবন্ধন প্রক্রিয়া। Work profile শুধু সেই প্রোফাইল নিয়ন্ত্রণ করে; SafeNest-এর জন্য পূর্ণ ডিভাইস মালিকানা নিবন্ধন প্রয়োজন।"), fontSize = 12.sp)
                Text(t("1. Use a dedicated emulator or spare Android phone you own. Install this SafeNest update. The test device must be eligible, with no accounts or other owner. Back up a real phone before any provisioning that requires a reset; this app does not reset it for you.", "১. নিজের পরীক্ষার এমুলেটর বা অতিরিক্ত Android ফোন ব্যবহার করুন। SafeNest-এর এই আপডেট ইনস্টল করুন। ডিভাইসে কোনো অ্যাকাউন্ট বা অন্য মালিক থাকা চলবে না। রিসেট প্রয়োজন হলে আগে বাস্তব ফোনের ব্যাকআপ নিন; এই অ্যাপ নিজে রিসেট করে না।"), fontSize = 12.sp)
                Text(t("2. From your computer's Android SDK platform-tools terminal, select the intended device and run:", "২. কম্পিউটারের Android SDK platform-tools টার্মিনালে সঠিক ডিভাইস বেছে চালান:"), fontSize = 12.sp)
                SelectionContainer {
                    Text("adb devices\nadb -s DEVICE_SERIAL shell dpm set-device-owner com.safenest.app/.SafeNestAdminReceiver", fontSize = 11.sp)
                }
                Text(t("Replace DEVICE_SERIAL with the identifier from adb devices. If Android rejects enrollment, stop and read its error; installing the VPN profile again will not fix eligibility. Detailed Windows commands are in docs/device-owner-setup.md in the ZIP.", "DEVICE_SERIAL-এর জায়গায় adb devices থেকে পাওয়া পরিচয় দিন। Android নিবন্ধন প্রত্যাখ্যান করলে ত্রুটিটি দেখুন; VPN প্রোফাইল আবার ইনস্টল করলে যোগ্যতা বদলাবে না। ZIP-এর docs/device-owner-setup.md-এ বিস্তারিত Windows নির্দেশনা আছে।"), fontSize = 12.sp)
                Text(t("3. Reopen SafeNest → Setup. Start protection, grant app-guard Accessibility, and test ordinary browsing and a blocked test domain. Choose Apply managed controls and store the administrator recovery code outside the phone for repair if browsing breaks.", "৩. SafeNest → Setup খুলে সুরক্ষা চালু করুন, অ্যাপ গার্ডের Accessibility অনুমতি দিন এবং সাধারণ ও ব্লক করা পরীক্ষার সাইট যাচাই করুন। পরিচালিত নিয়ন্ত্রণ প্রয়োগ করে প্রশাসকের পুনরুদ্ধার কোড ফোনের বাইরে রাখুন, যাতে ব্রাউজিং নষ্ট হলে মেরামত করা যায়।"), fontSize = 12.sp)
                Text(t("4. Verify every required policy is confirmed. Test VPN Disconnect, switching VPNs, uninstall, and restart. Leave “Block connections without VPN” OFF: this build routes DNS only.", "৪. প্রয়োজনীয় প্রতিটি নীতি নিশ্চিত হয়েছে দেখুন। VPN বন্ধ, VPN বদল, আনইনস্টল ও রিস্টার্ট পরীক্ষা করুন। “Block connections without VPN” বন্ধ রাখুন: এই সংস্করণ শুধু DNS বহন করে।"), fontSize = 12.sp)
                Text(t("This is a development enrollment guide, not a finished customer subscription enrollment service. Recovery, device reset, root access and privileged debugging remain outside an absolute no-removal guarantee.", "এটি ডেভেলপমেন্টের নিবন্ধন নির্দেশিকা, সম্পূর্ণ গ্রাহক সাবস্ক্রিপশন সেবা নয়। পুনরুদ্ধার, ডিভাইস রিসেট, রুট ও বিশেষ ডিবাগিংয়ের মাধ্যমে অপসারণ একেবারে অসম্ভব করার নিশ্চয়তা নেই।"), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done", "সম্পন্ন")) } }
    )
}
