package com.safenest.app

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SetupInk = Color(0xFF28111F)
private val SetupPurple = Color(0xFF982957)
private val SetupMuted = Color(0xFF69545D)

@Composable
fun ProtectionSetupScreen(
    language: String,
    active: Boolean,
    isOwner: Boolean,
    managedActive: Boolean,
    onStart: () -> Unit,
    onTestInternet: () -> Unit,
    onVpnSettings: () -> Unit,
    onManaged: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    var guardEnabled by remember { mutableStateOf(GuardPreferences.isSelected(context)) }
    var accessEnabled by remember { mutableStateOf(GuardPreferences.isAccessibilityEnabled(context)) }
    var guardConnected by remember { mutableStateOf(GuardPreferences.isServiceConnected()) }
    var unrestricted by remember { mutableStateOf(GuardPreferences.isBatteryUnrestricted(context)) }
    var blockVpns by remember { mutableStateOf(GuardPreferences.blocksVpnApps(context)) }
    var selected by remember { mutableStateOf(GuardPreferences.getBlockedPackages(context)) }
    var health by remember { mutableStateOf(SafeNestVpnService.dnsHealth.get()) }
    var lastBlockedHost by remember { mutableStateOf(SafeNestVpnService.lastBlockedHost.get()) }
    var running by remember { mutableStateOf(active) }
    var vpnTakenOver by remember { mutableStateOf(false) }
    var lockdown by remember { mutableStateOf(SafeNestVpnService.lockdownEnabled.get()) }
    var alwaysOn by remember { mutableStateOf(SafeNestVpnService.alwaysOnEnabled.get()) }
    var privateDns by remember { mutableStateOf(SafeNestVpnService.privateDnsState.get()) }
    var chromePolicy by remember { mutableStateOf(ManagedProtection.chromePolicyStatus(context)) }
    var managedPolicy by remember { mutableStateOf<ManagedPolicyStatus?>(null) }
    var recoveryConfigured by remember { mutableStateOf(false) }
    var showEnrollment by remember { mutableStateOf(false) }
    var policyBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    var enableVpnAfterConsent by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<AppChoice>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(false) }
    var refreshApps by remember { mutableIntStateOf(0) }
    var notifications by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = it }

    fun openSettings(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: Exception) { message = t("This screen is unavailable on your phone. Open Android Settings manually.", "এই ফোনে স্ক্রিনটি খোলা যাচ্ছে না। Android সেটিংস খুলুন।") }
    }

    LaunchedEffect(Unit) {
        while (true) {
            vpnTakenOver = SafeNestVpnService.isRunning.get() && VpnService.prepare(context) != null
            running = SafeNestVpnService.isRunning.get() && !vpnTakenOver
            health = SafeNestVpnService.dnsHealth.get()
            lastBlockedHost = SafeNestVpnService.lastBlockedHost.get()
            lockdown = SafeNestVpnService.lockdownEnabled.get()
            alwaysOn = SafeNestVpnService.alwaysOnEnabled.get()
            privateDns = SafeNestVpnService.privateDnsState.get()
            chromePolicy = ManagedProtection.chromePolicyStatus(context)
            error = SafeNestVpnService.lastError.get()
            guardEnabled = GuardPreferences.isSelected(context)
            accessEnabled = GuardPreferences.isAccessibilityEnabled(context)
            guardConnected = GuardPreferences.isServiceConnected()
            unrestricted = GuardPreferences.isBatteryUnrestricted(context)
            notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            delay(1000)
        }
    }
    LaunchedEffect(isOwner, managedActive) {
        while (true) {
            val status = withContext(Dispatchers.IO) {
                ManagedProtection.policyStatus(context) to ManagedRecovery.configured(context)
            }
            managedPolicy = status.first
            recoveryConfigured = status.second
            delay(2500)
        }
    }
    LaunchedEffect(refreshApps, showPicker) {
        loadingApps = true
        try {
            val inventory = withContext(Dispatchers.IO) { AppCatalog.load(context) }
            apps = inventory
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            message = t("Android could not list apps. Close this screen and retry.", "Android অ্যাপের তালিকা দেখাতে পারেনি। স্ক্রিন বন্ধ করে আবার চেষ্টা করুন।")
        } finally { loadingApps = false }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t("SET UP SAFENEST", "SAFENEST সেটআপ"), fontSize = 10.sp, color = SetupMuted)
        Text(t("Protection you can check.", "সুরক্ষা নিজেই যাচাই করুন।"), fontSize = 25.sp, color = SetupInk, fontWeight = FontWeight.Bold)
        Text(t("Complete each permission yourself. DNS filtering and app guard work independently.", "প্রতিটি অনুমতি নিজে দিন। DNS ফিল্টার ও অ্যাপ গার্ড আলাদাভাবে কাজ করে।"), fontSize = 12.sp, color = SetupMuted)

        SetupCard(t("Live protection status", "সুরক্ষার বর্তমান অবস্থা")) {
            StatusLine(t("DNS service", "DNS সেবা"), if (running) t("Running", "চালু") else t("Stopped", "বন্ধ"))
            if (vpnTakenOver) Text(t("Another VPN has taken over. SafeNest cannot filter its traffic on this personal phone. Restore SafeNest VPN permission and test again.", "অন্য VPN চালু হয়েছে। ব্যক্তিগত ফোনে SafeNest তার ট্রাফিক ফিল্টার করতে পারে না। SafeNest VPN অনুমতি ফিরিয়ে আবার পরীক্ষা করুন।"), fontSize = 12.sp, color = Color(0xFFB53A3A))
            StatusLine(t("Allowed-site DNS", "স্বাভাবিক সাইটের DNS"), when {
                lockdown -> t("Lockdown conflicts with this build", "Lockdown এই সংস্করণের সাথে সামঞ্জস্যপূর্ণ নয়")
                !running -> t("Not running", "চালু নেই")
                health == "ok" -> t("A lookup succeeded; test browsing", "ঠিকানা পাওয়া গেছে; ব্রাউজিং পরীক্ষা করুন")
                health == "failed" -> t("Failed — recovery available", "ব্যর্থ — পুনরুদ্ধার করুন")
                else -> t("Waiting for a website lookup", "ওয়েবসাইটের DNS পরীক্ষার অপেক্ষায়")
            })
            StatusLine(t("Last filtered DNS request", "সর্বশেষ ব্লক করা DNS অনুরোধ"),
                if (lastBlockedHost.isBlank()) t("None observed since service start", "সেবা চালুর পর কোনোটি দেখা যায়নি") else lastBlockedHost)
            StatusLine(t("App guard", "অ্যাপ গার্ড"), when {
                !guardEnabled -> t("Off", "বন্ধ")
                !accessEnabled -> t("Accessibility permission missing", "Accessibility অনুমতি প্রয়োজন")
                !guardConnected -> t("Waiting for Android to connect", "Android সংযোগের অপেক্ষায়")
                !ProtectionCommitment.isActive(context) -> t("Prepared; activate paid protection", "প্রস্তুত; পেইড সুরক্ষা চালু করুন")
                else -> t("Enabled and connected", "চালু ও সংযুক্ত")
            })
            StatusLine(t("VPN app blocking", "VPN অ্যাপ ব্লক"), when {
                !blockVpns -> t("Not selected", "নির্বাচিত নয়")
                !guardEnabled || !accessEnabled || !guardConnected -> t("Selected; app guard is not connected", "নির্বাচিত; অ্যাপ গার্ড সংযুক্ত নয়")
                else -> t("Active for visible detected apps", "দৃশ্যমান শনাক্ত অ্যাপে সক্রিয়")
            })
            if (guardEnabled && !running) Text(t("The DNS filter is off. The UI guard can recognize some blocked addresses, but network filtering is not running.", "DNS ফিল্টার বন্ধ। UI গার্ড কিছু ব্লক ঠিকানা চিনতে পারে, কিন্তু নেটওয়ার্ক ফিল্টার চলছে না।"), fontSize = 11.sp, color = Color(0xFF9A5500))
            if (lockdown) Text(t("Turn OFF “Block connections without VPN” in Android VPN settings. This version filters DNS and does not relay all internet traffic.", "Android VPN সেটিংসে “Block connections without VPN” বন্ধ করুন। এই সংস্করণ DNS ফিল্টার করে, সব ইন্টারনেট ট্রাফিক বহন করে না।"), fontSize = 12.sp, color = Color(0xFFB53A3A))
            if (error.isNotBlank()) Text(t("Diagnostic: ", "ত্রুটির তথ্য: ") + error, fontSize = 11.sp, color = Color(0xFFB53A3A))
        }

        SetupCard(t("1. Website filtering", "১. ওয়েবসাইট ফিল্টার")) {
            Text(t("Your domain lists include gambling, adult content and custom sites. Subdomains are included. Test normal browsing before enabling Always-on.", "জুয়া, প্রাপ্তবয়স্ক ও নিজের তালিকার ডোমেইন এবং সাবডোমেইন ব্লক হবে। Always-on চালুর আগে সাধারণ ব্রাউজিং পরীক্ষা করুন।"), fontSize = 12.sp)
            if (!running) Button(onClick = onStart) { Text(if (ProtectionCommitment.hasVerifiedAccess(context)) t("Start paid protection", "পেইড সুরক্ষা চালু করুন") else t("Verify paid access first", "আগে পেইড মেয়াদ যাচাই করুন")) }
            OutlinedButton(onClick = onTestInternet) { Text(t("Open example.com to test", "example.com খুলে পরীক্ষা করুন")) }
            Text(t("Add example.org to My list, then open it and check Last filtered DNS request above. A saved rule without a recorded request means the browser may be using cache or bypassing this DNS filter. Existing connections can keep working; close the tab or browser and retry. Confirm example.com still loads.", "আমার তালিকায় example.org যোগ করে খুলুন এবং উপরের সর্বশেষ ব্লক করা DNS অনুরোধ দেখুন। অনুরোধ না এলে ব্রাউজারের ক্যাশ বা ভিন্ন DNS পথ ব্যবহৃত হতে পারে। পুরনো সংযোগ চলতে পারে; ট্যাব বা ব্রাউজার বন্ধ করে আবার চেষ্টা করুন। example.com চালু থাকে কি না দেখুন।"), fontSize = 11.sp, color = SetupMuted)
        }

        SetupCard(t("Private DNS and browser settings", "Private DNS ও ব্রাউজার সেটিংস")) {
            StatusLine(t("Physical-network Private DNS", "মূল নেটওয়ার্কের Private DNS"), when (privateDns) {
                "off" -> t("Not active on the observed network", "পর্যবেক্ষিত নেটওয়ার্কে সক্রিয় নয়")
                "automatic-active" -> t("Automatic encrypted DNS active", "স্বয়ংক্রিয় এনক্রিপ্টেড DNS সক্রিয়")
                "strict-active" -> t("Custom encrypted DNS active", "নিজের নির্ধারিত এনক্রিপ্টেড DNS সক্রিয়")
                "strict-unvalidated" -> t("Custom provider is not validated", "নিজের DNS সেবাদাতা যাচাই হয়নি")
                else -> t("Unknown — start the filter and check settings", "অজানা — ফিল্টার চালু করে সেটিংস দেখুন")
            })
            Text(t("System Private DNS and a browser's Secure DNS are different. Browser settings can bypass DNS lists. Check both using normal and blocked test sites. On Android 9 this version cannot forward active Private DNS safely; review the diagnostic before continuing.", "সিস্টেমের Private DNS এবং ব্রাউজারের Secure DNS আলাদা। ব্রাউজারের সেটিংস DNS তালিকা এড়াতে পারে। সাধারণ ও ব্লক করা সাইট দিয়ে দুটিই পরীক্ষা করুন। Android 9-এ এই সংস্করণ সক্রিয় Private DNS নিরাপদে ফরওয়ার্ড করতে পারে না; ত্রুটির তথ্য দেখুন।"), fontSize = 12.sp)
            OutlinedButton(onClick = { openSettings(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) { Text(t("Open network settings", "নেটওয়ার্ক সেটিংস খুলুন")) }
        }

        SetupCard(t("2. App guard and Accessibility", "২. অ্যাপ গার্ড ও Accessibility")) {
            Text(appGuardDisclosure(language), fontSize = 12.sp)
            if (!guardEnabled) Button(enabled = ProtectionCommitment.hasVerifiedAccess(context), onClick = { showConsent = true }) { Text(t("Review and enable app guard", "বিস্তারিত পড়ে অ্যাপ গার্ড চালু করুন")) }
            if (guardEnabled && !accessEnabled) Button(onClick = { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(t("Grant Accessibility permission", "Accessibility অনুমতি দিন")) }
            Text(t("Detection depends on Android permissions and app visibility. This service does not decrypt VPN traffic or provide complete proxy coverage.", "শনাক্তকরণ Android-এর অনুমতি ও অ্যাপ দৃশ্যমানতার ওপর নির্ভর করে। এই সেবা VPN ট্রাফিকের এনক্রিপশন খোলে না এবং সব প্রক্সি নিয়ন্ত্রণ করে না।"), fontSize = 11.sp, color = SetupMuted)
        }

        SetupCard(t("Choose apps to block", "ব্লক করার অ্যাপ বাছুন")) {
            Text(t("Ten known gambling package IDs are included automatically while app guard is enabled, including Krikya and Baji. Other APK versions may use different IDs. These automatic entries cannot be removed individually.", "অ্যাপ গার্ড চালু থাকলে Krikya ও Baji-সহ দশটি পরিচিত জুয়ার প্যাকেজ স্বয়ংক্রিয়ভাবে ব্লক হবে। অন্য APK সংস্করণের ID আলাদা হতে পারে। স্বয়ংক্রিয় তালিকার এন্ট্রি আলাদাভাবে সরানো যায় না।"), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t("Block detected VPN apps", "শনাক্ত VPN অ্যাপ ব্লক করুন"), modifier = Modifier.weight(1f), fontSize = 12.sp)
                Switch(checked = blockVpns, enabled = ProtectionCommitment.hasVerifiedAccess(context) && !ProtectionCommitment.isActive(context) && !managedActive, onCheckedChange = { enabled ->
                    if (enabled) { enableVpnAfterConsent = true; showConsent = true }
                    else { GuardPreferences.setBlockVpnApps(context, false); blockVpns = false }
                })
            }
            Text(t("Android visibility restrictions can hide some VPN apps. A VPN started through system settings may still replace SafeNest on a personal phone.", "Android-এর সীমাবদ্ধতায় কিছু VPN অ্যাপ দেখা নাও যেতে পারে। ব্যক্তিগত ফোনে সেটিংস থেকে চালু VPN SafeNest-কে বদলে দিতে পারে।"), fontSize = 11.sp, color = SetupMuted)
            selected.sorted().forEach { pkg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(apps.firstOrNull { it.packageName == pkg }?.label ?: pkg, modifier = Modifier.weight(1f), fontSize = 12.sp)
                }
            }
            if (selected.isEmpty()) Text(t("No individual apps selected. Choose installed betting apps or another harmless app for your test.", "কোনো অ্যাপ বাছা হয়নি। ইনস্টল করা বেটিং অ্যাপ বা পরীক্ষার জন্য সাধারণ অ্যাপ বাছুন।"), fontSize = 12.sp)
            OutlinedButton(onClick = { showPicker = true; refreshApps++ }) { Text(t("Choose installed apps", "ইনস্টল করা অ্যাপ বাছুন")) }
        }

        if (BuildConfig.MANAGED_CONTROLS && !ProtectionCommitment.isActive(context)) SetupCard(t("Device Administrator", "Device Administrator")) {
            Text(t("Optional extra Android confirmation before removal. This is not Device Owner enrollment.", "সরানোর আগে Android-এর অতিরিক্ত নিশ্চিতকরণ। এটি Device Owner নিবন্ধন নয়।"), fontSize = 12.sp)
            if (!GuardPreferences.isAdminActive(context)) OutlinedButton(onClick = { openSettings(GuardPreferences.adminActivationIntent(context)) }) { Text(t("Enable Device Administrator", "Device Administrator চালু করুন")) }
        }

        SetupCard(t("3. Keep protection running", "৩. সুরক্ষা চালু রাখুন")) {
            StatusLine(t("Battery optimization", "ব্যাটারি অপ্টিমাইজেশন"), if (unrestricted) t("Unrestricted", "সীমাহীন") else t("Check Android battery settings", "Android ব্যাটারি সেটিংস দেখুন"))
            OutlinedButton(onClick = { openSettings(GuardPreferences.batterySettingsIntent(context)) }) { Text(t("Open battery settings", "ব্যাটারি সেটিংস খুলুন")) }
            if (Build.VERSION.SDK_INT >= 33 && !notifications) OutlinedButton(onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(t("Allow protection notifications", "সুরক্ষার বিজ্ঞপ্তি অনুমোদন করুন")) }
            StatusLine(t("Always-on VPN", "Always-on VPN"), if (Build.VERSION.SDK_INT < 29) t("Check Android VPN settings", "Android VPN সেটিংস দেখুন") else if (alwaysOn) t("On", "চালু") else t("Off", "বন্ধ"))
            Text(t("After both tests pass, enable Always-on in Android VPN settings and leave “Block connections without VPN” OFF.", "দুই পরীক্ষা সফল হলে Android VPN সেটিংসে Always-on চালু করুন এবং “Block connections without VPN” বন্ধ রাখুন।"), fontSize = 12.sp)
            OutlinedButton(onClick = onVpnSettings) { Text(t("Open VPN settings", "VPN সেটিংস খুলুন")) }
        }

        if (BuildConfig.MANAGED_CONTROLS) SetupCard(t("Managed-device enforcement", "পরিচালিত ডিভাইসের নিয়ন্ত্রণ")) {
            val status = managedPolicy
            Text(when {
                status == null -> t("Checking Android policies…", "Android নীতি পরীক্ষা হচ্ছে…")
                status.verified -> t("Android reports the required VPN controls are applied. Test the system Disconnect action to confirm behavior on this phone.", "Android অনুযায়ী প্রয়োজনীয় VPN নিয়ন্ত্রণ চালু। এই ফোনে সিস্টেমের Disconnect বোতাম দিয়ে আচরণ যাচাই করুন।")
                !status.isDeviceOwner && status.adminActive -> t("Device Administrator is active, but this is still a personal installation. Android can offer Deactivate and uninstall or Disconnect. Enroll an eligible test device as Device Owner, then apply and verify managed controls.", "Device Administrator চালু, কিন্তু এটি এখনো ব্যক্তিগত ইনস্টলেশন। Android নিষ্ক্রিয় করে আনইনস্টল বা VPN বন্ধ করার সুযোগ দিতে পারে। উপযুক্ত পরীক্ষার ডিভাইস Device Owner হিসেবে নিবন্ধন করে নিয়ন্ত্রণ প্রয়োগ ও যাচাই করুন।")
                !status.isDeviceOwner -> t("Personal mode: Android can disconnect or uninstall SafeNest. A payment or ordinary permission cannot grant Device Owner access.", "ব্যক্তিগত মোড: Android SafeNest বন্ধ বা আনইনস্টল করতে পারে। পেমেন্ট বা সাধারণ অনুমতি Device Owner অধিকার দেয় না।")
                else -> t("Enrollment exists, but required controls are incomplete. Review and apply or repair managed controls.", "নিবন্ধন আছে, তবে প্রয়োজনীয় নিয়ন্ত্রণ অসম্পূর্ণ। পরিচালিত নিয়ন্ত্রণ পর্যালোচনা করে প্রয়োগ বা মেরামত করুন।")
            }, fontSize = 12.sp)
            if (status != null) {
                StatusLine(t("Device Owner", "Device Owner"), if (status.isDeviceOwner) t("Enrolled", "নিবন্ধিত") else t("Not enrolled", "নিবন্ধিত নয়"))
                if (!status.isDeviceOwner) StatusLine(t("Ordinary Device Administrator", "সাধারণ Device Administrator"), if (status.adminActive) t("Active, but removable", "চালু, তবে সরানো যায়") else t("Not active", "চালু নয়"))
                StatusLine(t("SafeNest is managed Always-on VPN", "SafeNest পরিচালিত Always-on VPN"), if (status.alwaysOnSafeNest) t("Confirmed", "নিশ্চিত") else t("Not confirmed", "নিশ্চিত নয়"))
                StatusLine(t("VPN setting changes restricted", "VPN সেটিংস পরিবর্তন সীমিত"), if (status.vpnConfigRestricted) t("Confirmed", "নিশ্চিত") else t("Not confirmed", "নিশ্চিত নয়"))
                StatusLine(t("SafeNest uninstall restricted", "SafeNest আনইনস্টল সীমিত"), if (status.uninstallBlocked) t("Confirmed", "নিশ্চিত") else t("Not confirmed", "নিশ্চিত নয়"))
                StatusLine(t("Administrator recovery code", "প্রশাসকের পুনরুদ্ধার কোড"), if (recoveryConfigured) t("Configured", "নির্ধারিত") else t("Not configured", "নির্ধারিত নয়"))
                status.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
            }
            Text(t("Android can display SafeNest in VPN settings because website filtering uses Android VpnService. Device Owner controls may restrict changes after enrollment. Paid activation reads server-issued access; billing checkout remains pending.", "ওয়েবসাইট ফিল্টার Android VpnService ব্যবহার করে, তাই VPN সেটিংসে SafeNest দেখা যেতে পারে। Device Owner নিবন্ধনের পর সেটিংস পরিবর্তন সীমিত হতে পারে। পেমেন্ট চালু করা এখনো বাকি।"), fontSize = 11.sp, color = SetupMuted)
            OutlinedButton(onClick = { showEnrollment = true }) { Text(t("Device-owner setup guide", "Device Owner সেটআপ নির্দেশিকা")) }
            if (isOwner) OutlinedButton(onClick = onManaged) { Text(if (managedActive) t("Administrator support", "প্রশাসকের সহায়তা") else t("Apply managed controls", "পরিচালিত নিয়ন্ত্রণ প্রয়োগ")) }
        }
        if (BuildConfig.MANAGED_CONTROLS && isOwner && managedActive) SetupCard(t("Managed Chrome website rules", "পরিচালিত Chrome ওয়েবসাইট তালিকা")) {
            if (chromePolicy.omittedDomains > 0) Text(t("Some rules exceed Android's managed Chrome policy limit; see the warning below.", "কিছু নিয়ম Android-এর পরিচালিত Chrome নীতির সীমা ছাড়িয়েছে; নিচের সতর্কতা দেখুন।"), fontSize = 12.sp)
            Text(t("These rules cover navigation inside managed Chrome, independently of its DNS. They do not cover every browser, WebView, in-page request or already-open connection. Open chrome://policy in Chrome and verify URLBlocklist and DnsOverHttpsMode are applied.", "এই তালিকা পরিচালিত Chrome-এ ঠিকানা খোলার সময় DNS থেকে স্বাধীনভাবে কাজ করে। সব ব্রাউজার, WebView, পৃষ্ঠার ভেতরের অনুরোধ বা আগে খোলা সংযোগে প্রযোজ্য নয়। Chrome-এ chrome://policy খুলে URLBlocklist ও DnsOverHttpsMode যাচাই করুন।"), fontSize = 11.sp)
            chromePolicy.warning?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            OutlinedButton(enabled = !policyBusy, onClick = {
                policyBusy = true
                scope.launch {
                    try { chromePolicy = withContext(Dispatchers.IO) { ManagedProtection.refreshDomainPolicy(context) } }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { message = t("Chrome policy could not be refreshed. Check enrollment and retry.", "Chrome নীতি হালনাগাদ হয়নি। নিবন্ধন দেখে আবার চেষ্টা করুন।") }
                    finally { policyBusy = false }
                }
            }) { Text(t("Refresh managed browser rules", "পরিচালিত ব্রাউজারের তালিকা হালনাগাদ")) }
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFB53A3A), fontSize = 12.sp)
        TextButton(onClick = onBack) { Text(t("Back to protection", "সুরক্ষায় ফিরুন")) }
    }

    if (showEnrollment) DeviceOwnerSetupDialog(language, onDismiss = { showEnrollment = false })

    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false; enableVpnAfterConsent = false },
        title = { Text(t("Allow SafeNest app guard?", "SafeNest অ্যাপ গার্ড অনুমোদন করবেন?")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(appGuardDisclosure(language))
            Text(t("These observations stay on your phone, are not recorded as history and are not sent to a server. SafeNest does not inspect page bodies, messages or passwords. Calls and unrelated settings remain usable.", "পর্যবেক্ষণ ফোনেই থাকে; ইতিহাস হিসেবে সংরক্ষণ বা সার্ভারে পাঠানো হয় না। পৃষ্ঠার বিষয়বস্তু, বার্তা বা পাসওয়ার্ড পরীক্ষা করা হয় না। কল ও অন্য সেটিংস ব্যবহার করা যায়।"))
            Text(t("This step prepares the permission only. Protection starts after paid verification and activation, has no in-app pause, and ends automatically at expiry. Detection depends on Android and the phone's interface. It is not root access or a promise of impossible removal.", "এই ধাপ শুধু অনুমতি প্রস্তুত করে। পেইড যাচাই ও চালুর পরে সুরক্ষা কাজ করে; অ্যাপে বিরতি নেই এবং মেয়াদ শেষে শেষ হয়। শনাক্তকরণ ফোন ও Android-এর ওপর নির্ভর করে। এটি root access নয় এবং সরানো অসম্ভবের প্রতিশ্রুতি নয়।"))
        } },
        confirmButton = { TextButton(onClick = {
            GuardPreferences.setEnabled(context, true)
            if (enableVpnAfterConsent) {
                GuardPreferences.setBlockVpnApps(context, true)
                blockVpns = true
            }
            enableVpnAfterConsent = false
            guardEnabled = true
            showConsent = false
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text(t("I agree — open settings", "সম্মত — সেটিংস খুলুন")) } },
        dismissButton = { TextButton(onClick = { showConsent = false; enableVpnAfterConsent = false }) { Text(t("Not now", "এখন নয়")) } }
    )

    if (showPicker) AppPickerDialog(language, apps, loadingApps, selected, onAdd = { pkg ->
        if (GuardPreferences.addBlockedPackage(context, pkg)) selected = GuardPreferences.getBlockedPackages(context)
    }, onDismiss = { showPicker = false })
}

@Composable private fun SetupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = SetupInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable private fun StatusLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = SetupMuted)
        Text(value, fontSize = 13.sp, color = SetupPurple, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun AppPickerDialog(language: String, apps: List<AppChoice>, loading: Boolean, selected: Set<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val t = { en: String, bn: String -> if (language == "bn") bn else en }
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(t("Choose installed apps", "ইনস্টল করা অ্যাপ বাছুন")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, onValueChange = { query = it }, label = { Text(t("Search apps", "অ্যাপ খুঁজুন")) }, singleLine = true)
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) Text(t("Reading visible installed apps…", "দৃশ্যমান ইনস্টল অ্যাপ পড়া হচ্ছে…"))
                else if (filtered.isEmpty()) Text(t("No matching apps are visible to SafeNest.", "মিল থাকা কোনো অ্যাপ SafeNest দেখতে পাচ্ছে না।"))
                filtered.forEach { app ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(app.packageName, fontSize = 9.sp)
                            if (app.isVpn) Text("VPN", fontSize = 10.sp, color = SetupPurple)
                        }
                        TextButton(onClick = { onAdd(app.packageName) }, enabled = app.packageName !in selected) { Text(if (app.packageName in selected) t("Added", "যোগ হয়েছে") else t("Block", "ব্লক")) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done", "সম্পন্ন")) } })
}
