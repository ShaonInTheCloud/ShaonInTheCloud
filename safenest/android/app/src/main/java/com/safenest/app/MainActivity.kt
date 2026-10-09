package com.safenest.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF28111F)
private val Violet = Color(0xFF982957)
private val Sunshine = Color(0xD9F2B4CE)
private val Lilac = Color(0xD9F6BBD2)
private val Paper = Color(0xFFC26187)
private val SoftText = Color(0xFF2D081C)
private val Mint = Color(0xD9ECB0CA)
private val Glass = Color(0xD9F6BBD2)
private val BarInk = Color(0xFFFFE3EF)
private val Bar = Color(0xFF661431)
private val FooterBar = Color(0xFF490B24)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        noteRestoreRequest(intent)
        setContent { SafeNestTheme { SafeNestStartup() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteRestoreRequest(intent)
    }

    /** Tapping a "protection is off" alert asks the running UI to restart protection. */
    private fun noteRestoreRequest(intent: Intent?) {
        if (intent?.action == ProtectionAlerts.ACTION_RESTORE) {
            getSharedPreferences("safenest_app", MODE_PRIVATE).edit().putBoolean("restore_requested", true).apply()
        }
    }
}

@Composable
private fun SafeNestStartup() {
    val context = LocalContext.current.applicationContext
    var ready by remember { mutableStateOf(false) }
    var introFinished by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        try {
            withContext(Dispatchers.IO) {
                CatalogStore.status(context)
                RuleCategory.entries.forEach { RulesStore.get(context, it) }
                BundledGamblingRules.load(context)
            }
            ready = true
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    if (!introFinished) SafeNestBrandIntro(onFinished = { introFinished = true })
    else if (ready) SafeNestApp()
    else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (failed) {
            Text("SafeNest could not load its saved rules. Retry before changing protection settings.")
            TextButton(onClick = { attempt++ }) { Text("Retry / আবার চেষ্টা করুন") }
        } else {
            CircularProgressIndicator()
            Text("Loading verified rules… / যাচাইকৃত তালিকা পড়া হচ্ছে…", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun SafeNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Violet, onPrimary = BarInk, primaryContainer = Color(0xFFF2B4CE), onPrimaryContainer = Ink,
        secondary = Bar, onSecondary = BarInk, secondaryContainer = Color(0xFFEBA5C3), onSecondaryContainer = Ink,
        background = Paper, onBackground = Ink, surface = Color(0xFFF6BBD2), onSurface = Ink,
        surfaceVariant = Color(0xFFEBA5C3), onSurfaceVariant = SoftText, outline = Color(0xFF9E4369)
    ), content = content)
}

@Composable
private fun SafeNestApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("safenest_app", Activity.MODE_PRIVATE) }
    var page by remember { mutableStateOf("home") }
    var language by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }
    var category by remember { mutableStateOf(RuleCategory.GAMBLING) }
    var protectionOn by remember { mutableStateOf(SafeNestVpnService.isRunning.get() && VpnService.prepare(context) == null) }
    var testActive by remember { mutableStateOf(LocalTestSession.isActive(context)) }
    var paidAccess by remember { mutableStateOf(ProtectionCommitment.hasVerifiedAccess(context)) }
    var checkins by remember { mutableIntStateOf(prefs.getInt("checkins", 0)) }
    var showAdd by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showActivationConfirm by remember { mutableStateOf(false) }
    var showManagedConfirm by remember { mutableStateOf(false) }
    var isOwner by remember { mutableStateOf(ManagedProtection.isDeviceOwner(context)) }
    var managedActive by remember { mutableStateOf(ManagedProtection.isConfigured(context)) }
    var showReset by remember { mutableStateOf(false) }
    var dnsState by remember { mutableStateOf(SafeNestVpnService.dnsHealth.get()) }
    var lockdown by remember { mutableStateOf(SafeNestVpnService.lockdownEnabled.get()) }
    var showCheckin by remember { mutableStateOf(false) }
    var newDomain by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf("") }
    fun syncManagedBrowser() {
        scope.launch {
            try { withContext(Dispatchers.IO) { ManagedProtection.refreshDomainPolicy(context) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { toast = s(language, "Rules saved; check managed Chrome policy status in Setup.", "তালিকা সংরক্ষিত; সেটআপে পরিচালিত Chrome-এর নীতির অবস্থা দেখুন।") }
        }
    }
    fun refreshRules() {
        syncManagedBrowser()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Android can replace this app with another VPN from system settings.
                protectionOn = SafeNestVpnService.isRunning.get() && VpnService.prepare(context) == null
                testActive = LocalTestSession.isActive(context)
                paidAccess = ProtectionCommitment.hasVerifiedAccess(context)
                prefs.edit().putBoolean("protection_on", protectionOn).apply()
                isOwner = ManagedProtection.isDeviceOwner(context)
                managedActive = ManagedProtection.isConfigured(context)
                refreshRules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Service state is authoritative; a permission dialog or start request is not success.
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                protectionOn = SafeNestVpnService.isRunning.get() && VpnService.prepare(context) == null
                testActive = LocalTestSession.isActive(context)
                paidAccess = ProtectionCommitment.hasVerifiedAccess(context)
                ProtectionCommitment.checkpoint(context)
                dnsState = SafeNestVpnService.dnsHealth.get()
                lockdown = SafeNestVpnService.lockdownEnabled.get()
                isOwner = ManagedProtection.isDeviceOwner(context)
                managedActive = ManagedProtection.isConfigured(context)
            }
            delay(750)
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (!ProtectionCommitment.begin(context)) {
                page = if (LocalTestSession.enabled) "setup" else "account"
                toast = if (LocalTestSession.enabled) s(language, "Complete Accessibility setup and retry the test.", "Accessibility সেটআপ শেষ করে আবার চেষ্টা করুন।")
                    else s(language, "Verify paid access and complete app guard setup before activation.", "চালুর আগে পেইড মেয়াদ যাচাই ও অ্যাপ গার্ড সেটআপ সম্পূর্ণ করুন।")
                return@rememberLauncherForActivityResult
            }
            val start = Intent(context, SafeNestVpnService::class.java).setAction(SafeNestVpnService.ACTION_START)
            try {
                ContextCompat.startForegroundService(context, start)
                page = "setup"
                toast = s(language, "Starting DNS filter. Check its live status and test browsing.", "DNS ফিল্টার চালু হচ্ছে। অবস্থা দেখুন ও ব্রাউজিং পরীক্ষা করুন।")
            } catch (_: Exception) {
                toast = s(language, "Android could not start the filter. Check setup and retry.", "Android ফিল্টার চালু করতে পারেনি। সেটআপ দেখে আবার চেষ্টা করুন।")
            }
        } else toast = s(language, "VPN permission was not granted.", "VPN অনুমতি দেওয়া হয়নি।")
    }

    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) {
            importing = true
            scope.launch {
                try {
                    val added = withContext(Dispatchers.IO) {
                        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(out.size() + count <= 2 * 1024 * 1024) { "List exceeds 2 MiB." }
                                out.write(buffer, 0, count)
                            }
                            out.toString("UTF-8")
                        } ?: error("Cannot open list.")
                        val rows = org.json.JSONObject(raw).getJSONArray("domains")
                        require(rows.length() <= 25000) { "List exceeds 25,000 entries." }
                        val grouped = RuleCategory.entries.associateWith { mutableListOf<String>() }
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            val cat = when (row.optString("category")) {
                                "adult" -> RuleCategory.ADULT
                                "personal", "custom" -> RuleCategory.PERSONAL
                                else -> RuleCategory.GAMBLING
                            }
                            grouped.getValue(cat).add(row.optString("domain"))
                        }
                        grouped.entries.sumOf { (cat, names) -> RulesStore.addAll(context, cat, names) }
                    }
                    refreshRules()
                    toast = s(language, "$added domains imported.", "${added}টি ডোমেইন আমদানি হয়েছে।")
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    toast = s(language, "Import failed. Use SafeNest JSON, at most 2 MiB and 25,000 entries.", "আমদানি হয়নি। সর্বোচ্চ ২ MiB ও ২৫,০০০ এন্ট্রির SafeNest JSON ব্যবহার করুন।")
                } finally { importing = false }
            }
        }
    }

    fun startProtection() {
        if (protectionOn) return
        if (!ProtectionCommitment.hasVerifiedAccess(context)) { page = "account"; return }
        if (LocalTestSession.enabled && !GuardPreferences.isTestSetupReady(context)) {
            page = "setup"
            toast = s(language, "First review the test guard, grant Accessibility, then return and start the test.", "আগে টেস্ট গার্ডে সম্মতি ও Accessibility অনুমতি দিন, তারপর ফিরে পরীক্ষা চালু করুন।")
            return
        }
        if (!LocalTestSession.enabled && (!GuardPreferences.isSelected(context) || !GuardPreferences.isAccessibilityEnabled(context))) {
            page = "setup"
            toast = s(language, "Complete the app guard consent and Accessibility step first.", "আগে অ্যাপ গার্ডের সম্মতি ও Accessibility ধাপ শেষ করুন।")
            return
        }
        val prepare = VpnService.prepare(context)
        if (prepare == null) {
            if (!ProtectionCommitment.begin(context)) { page = if (LocalTestSession.enabled) "setup" else "account"; return }
            try {
                ContextCompat.startForegroundService(context, Intent(context, SafeNestVpnService::class.java).setAction(SafeNestVpnService.ACTION_START))
                page = "setup"
            } catch (_: Exception) {
                toast = s(language, "Android could not start the filter. Check setup and retry.", "Android ফিল্টার চালু করতে পারেনি। সেটআপ দেখে আবার চেষ্টা করুন।")
            }
        } else vpnPermission.launch(prepare)
    }

    // Alert tap or in-app banner: restart protection during an active period.
    var interruption by remember { mutableStateOf(ProtectionAlerts.lastInterruption(context)) }
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val committed = ProtectionCommitment.isActive(context)
                interruption = if (committed && !protectionOn) ProtectionAlerts.lastInterruption(context) ?: ProtectionAlerts.Interruption(0L, false) else null
                if (prefs.getBoolean("restore_requested", false)) {
                    prefs.edit().putBoolean("restore_requested", false).apply()
                    if (committed && !protectionOn) startProtection()
                }
            }
            delay(1000)
        }
    }

    val t = { english: String, bangla: String -> s(language, english, bangla) }
    fun requestActivation() {
        if (LocalTestSession.enabled && !GuardPreferences.isTestSetupReady(context)) {
            page = "setup"
            toast = s(language, "Review and enable the test guard first. Grant Accessibility, then return here.", "আগে টেস্ট গার্ডে সম্মতি দিন ও Accessibility চালু করুন, তারপর ফিরে আসুন।")
        } else if (ProtectionCommitment.hasVerifiedAccess(context)) showActivationConfirm = true else page = "account"
    }

    var metalMotion by remember { mutableStateOf(prefs.getBoolean("metal_motion", true)) }
    Box(Modifier.fillMaxSize().background(Paper)) {
    LiquidMetalBackground(animate = metalMotion, modifier = Modifier.fillMaxSize())
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(FooterBar, Color(0xFF711B3F), FooterBar)))) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                listOf("home", "protect", "recover", "insights").forEach { destination ->
                    val selected = page == destination
                    NavigationBarItem(selected = selected, onClick = { page = destination },
                        icon = { Icon(when(destination){"home"->Icons.Rounded.Dashboard;"protect"->Icons.Rounded.Shield;"recover"->Icons.Rounded.Favorite;else->Icons.Rounded.ShowChart}, null) },
                        label = { Text(when(destination){"home"->t("Home","হোম");"protect"->t("Protect","সুরক্ষা");"recover"->t("Recover","পুনরুদ্ধার");else->t("Insights","অগ্রগতি")}, fontSize=10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor=Ink, selectedTextColor=BarInk, indicatorColor=Color(0xFFF6BBD2), unselectedIconColor=BarInk, unselectedTextColor=BarInk))
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom=4.dp), horizontalArrangement=Arrangement.Center) {
                listOf(Triple("Privacy", "গোপনীয়তা", "privacy.html"), Triple("Terms", "শর্তাবলি", "terms.html"), Triple("Support", "সহায়তা", "contact.html")).forEach { (english, bangla, route) ->
                    TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mysafenestbd.com/$route"))) }}) {
                        Text(t(english, bangla), color=BarInk, fontSize=10.sp)
                    }
                }
            }
            }
        },
        topBar = {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Bar, Color(0xFF842347), Color(0xFF58112E)))).statusBarsPadding().heightIn(min=60.dp).padding(horizontal=12.dp,vertical=4.dp), verticalAlignment=Alignment.CenterVertically) {
                Image(painterResource(R.drawable.safenest_brand), null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { Image(painterResource(R.drawable.safenest_wordmark), "SafeNest", Modifier.width(105.dp).height(25.dp).clip(RoundedCornerShape(4.dp))) }
                TextButton(onClick={language=if(language=="en")"bn" else "en";prefs.edit().putString("language",language).apply()}) { Text(if(language=="en")"বাংলা" else "EN", color=BarInk,fontSize=11.sp) }
                IconButton(onClick={page="account"}) { Icon(if(LocalTestSession.enabled)Icons.Rounded.Science else Icons.Rounded.AccountCircle,if(LocalTestSession.enabled)t("Test tools","পরীক্ষার টুল")else t("Account","অ্যাকাউন্ট"),tint=BarInk) }
                IconButton(onClick={page="settings"}) { Icon(Icons.Rounded.Settings,t("Settings","সেটিংস"),tint=BarInk) }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding=PaddingValues(start=18.dp,end=18.dp,top=10.dp,bottom=20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (LocalTestSession.enabled) TestBuildBanner(language, testActive,
                    onStart={requestActivation()}, onStop={LocalTestSession.stop(context);testActive=false;protectionOn=false},
                    onTools={page="account"})
                interruption?.let { stopped -> ProtectionInterruptedBanner(language, stopped, onRestore = { startProtection() }) }
                if (page == "settings") {
                    OutlinedButton(onClick={metalMotion=!metalMotion;prefs.edit().putBoolean("metal_motion",metalMotion).apply()}, modifier=Modifier.fillMaxWidth()) {
                        Icon(if(metalMotion)Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if(metalMotion)t("Pause background animation","ব্যাকগ্রাউন্ড অ্যানিমেশন থামান")else t("Play background animation","ব্যাকগ্রাউন্ড অ্যানিমেশন চালু করুন"))
                    }
                }
                when(page) {
                    "home" -> HomeScreen(language, protectionOn, paidAccess, dnsState, lockdown, checkins, onToggle={if(protectionOn)page="protect" else requestActivation()}, onReset={showReset=true}, onCheckin={showCheckin=true}, onProtect={page="protect"}, onRecover={page="recover"})
                    "protect" -> ProtectionScreen(language, protectionOn, paidAccess, category, onToggle={if(protectionOn)page="setup" else requestActivation()}, onCategory={category=it;refreshRules()}, onAdd={newDomain="";showAdd=true}, onSetup={page="setup"},onCatalog={page="catalog"},onImport={if(!importing)importRules.launch(arrayOf("application/json","text/json"))})
                    "account" -> if(LocalTestSession.enabled) TestLabScreen(language,
                        onStart={requestActivation()}, onSetup={page="setup"}, onRulesChanged={refreshRules()})
                        else SubscriptionScreen(language, onVerified={paidAccess=true;page="setup"})
                    "catalog" -> CatalogScreen(language,onChanged={refreshRules()},onBack={page="protect";refreshRules()})
                    "recover" -> RecoveryScreen(language, onReset={showReset=true}, onCheckin={showCheckin=true}, checkins=checkins)
                    "insights" -> InsightsScreen(language,checkins)
                    "setup" -> ProtectionSetupScreen(language,protectionOn,isOwner,managedActive,onStart={requestActivation()},onTestInternet={try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")))}catch(_:Exception){toast=t("No browser is available.","ব্রাউজার পাওয়া যায়নি।")}},onVpnSettings={try{context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))}catch(_:Exception){toast=t("Open Android Settings, then search for VPN.","Android সেটিংস খুলে VPN খুঁজুন।")}},onManaged={showManagedConfirm=true},onBack={page="protect"})
                    else -> SettingsScreen(language,protectionOn,isOwner,managedActive,onLanguage={language=if(language=="en")"bn" else "en";prefs.edit().putString("language",language).apply()},onVpnSettings={try{context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))}catch(_:Exception){page="setup"}},onSetup={page="setup"},onManaged={if(isOwner)showManagedConfirm=true else page="setup"})
                }
                }
            }
        }
    }

    } // Decorative background and scaffold

    if (showActivationConfirm) AlertDialog(
        onDismissRequest={showActivationConfirm=false},
        title={Text(if(LocalTestSession.enabled)t("Start a local test?", "ফোনে পরীক্ষা চালু করবেন?")else t("Commit to your paid protection period?", "পেইড মেয়াদের সুরক্ষায় সম্মত?"))},
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            if(LocalTestSession.enabled) {
                Text(t("No login or payment is needed. Start a 60-minute local test; Stop test ends it at any time. The app guard consent is already complete. Android will ask separately for VPN permission.", "লগইন বা পেমেন্ট লাগবে না। ফোনে ৬০ মিনিটের পরীক্ষা চলবে; Stop test যেকোনো সময় পরীক্ষা বন্ধ করে। অ্যাপ গার্ডের সম্মতি সম্পূর্ণ। Android আলাদাভাবে VPN অনুমতি চাইবে।"), fontSize=12.sp)
                Spacer(Modifier.height(10.dp))
            }
            Text(dnsFilterDisclosure(language), fontSize=12.sp)
            if(!LocalTestSession.enabled) {
                Spacer(Modifier.height(10.dp))
                Text(t("There is no in-app pause until the verified period expires. Android controls remain available in the Play build. Your period ends at ", "যাচাইকৃত মেয়াদ পর্যন্ত অ্যাপে বিরতি নেই। Play সংস্করণে Android-এর নিয়ন্ত্রণ ব্যবহার করা যায়। মেয়াদ শেষ: ")+java.time.Instant.ofEpochMilli(ProtectionCommitment.endsAt(context)).toString(), fontSize=12.sp)
            }
        }},
        confirmButton={TextButton(onClick={showActivationConfirm=false;startProtection()}){Text(t("I agree — start protection","সম্মত — সুরক্ষা চালু করুন"))}},
        dismissButton={TextButton(onClick={showActivationConfirm=false}){Text(t("Not now","এখন নয়"))}}
    )
    if (showAdd) AlertDialog(onDismissRequest={showAdd=false}, title={Text(t("Add a domain","ডোমেইন যোগ করুন"),fontWeight=FontWeight.Bold)}, text={Column { Text(t("Add a site to this device’s local list. Enter a domain such as example.com.","এই ডিভাইসের স্থানীয় তালিকায় সাইট যোগ করুন। example.com এর মতো ডোমেইন লিখুন।"),color=SoftText,fontSize=12.sp); Spacer(Modifier.height(12.dp)); OutlinedTextField(value=newDomain,onValueChange={newDomain=it},label={Text(t("Domain","ডোমেইন"))},singleLine=true) }}, confirmButton={TextButton(onClick={
        val normalized = RulesStore.normalize(newDomain)
        val added = RulesStore.add(context, category, newDomain)
        if (added && normalized != null) {
            showAdd = false
            refreshRules()
            toast = if (RulesStore.isBlocked(context, normalized) && SafeNestVpnService.isRunning.get())
                t("Saved for new DNS lookups. Reload Chrome; cached or open connections may remain until closed.", "নতুন DNS অনুরোধের জন্য সংরক্ষিত। Chrome রিলোড করুন; আগের সংযোগ বন্ধ না হওয়া পর্যন্ত চলতে পারে।")
            else t("Rule saved. Start website filtering and check its live status.", "নিয়ম সংরক্ষিত। ওয়েবসাইট ফিল্টার চালু করে অবস্থা দেখুন।")
        } else toast = t("Enter a valid, new domain.", "নতুন সঠিক ডোমেইন লিখুন।")
    }){Text(t("Add","যোগ করুন"))}}, dismissButton={TextButton(onClick={showAdd=false}){Text(t("Cancel","বাতিল"))}})
    if (BuildConfig.MANAGED_CONTROLS && showManagedConfirm) ManagedControlsDialog(
        language = language,
        protectionRunning = protectionOn,
        dnsHealthy = dnsState == "ok",
        lockdown = lockdown,
        onDismiss = { showManagedConfirm = false },
        onResult = { result ->
            managedActive = result.managed
            isOwner = ManagedProtection.isDeviceOwner(context)
            page = "setup"
            toast = result.message ?: t("Managed controls submitted. Check the live policy status and test Disconnect.", "পরিচালিত নিয়ন্ত্রণ জমা হয়েছে। বর্তমান নীতির অবস্থা ও Disconnect পরীক্ষা করুন।")
        }
    )
    if (showReset) GroundingResetDialog(language, onDismiss={showReset=false}, onComplete={showReset=false;toast=t("You made space before choosing.","সিদ্ধান্তের আগে একটু সময় নিয়েছেন।")})
    if (showCheckin) AlertDialog(onDismissRequest={showCheckin=false},title={Text(t("How are you, really?","সত্যি করে বলুন, কেমন আছেন?"),fontWeight=FontWeight.Bold)},text={Column { Text(t("No score, no judgement. Choose the closest feeling.","কোনো নম্বর বা বিচার নেই। কাছাকাছি অনুভূতিটি বেছে নিন।"),color=SoftText,fontSize=12.sp); Spacer(Modifier.height(10.dp)); listOf("Low" to "মন খারাপ","On edge" to "উদ্বিগ্ন","Okay" to "মোটামুটি","Hopeful" to "আশাবাদী").forEach { pair -> FilterChip(selected=mood==pair.first,onClick={mood=pair.first},label={Text(t(pair.first,pair.second))},modifier=Modifier.fillMaxWidth()) } }},confirmButton={TextButton(onClick={if(mood.isNotBlank()){checkins=checkins+1;prefs.edit().putInt("checkins",checkins).apply();showCheckin=false;mood="";toast=t("Check-in saved on this device.","চেক-ইন এই ডিভাইসে সংরক্ষিত হয়েছে.")}}){Text(t("Save check-in","চেক-ইন সংরক্ষণ করুন"))}},dismissButton={TextButton(onClick={showCheckin=false}){Text(t("Cancel","বাতিল"))}})

    AnimatedVisibility(visible=toast.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(bottom=156.dp)) { Snackbar(modifier=Modifier.padding(horizontal=18.dp),action={TextButton(onClick={toast=""}){Text("OK",color=BarInk)}}){Text(toast)} }
}

/** Shown on every page while a paid period is active but protection is not running. */
@Composable private fun ProtectionInterruptedBanner(lang: String, stopped: ProtectionAlerts.Interruption, onRestore: () -> Unit) {
    val t = { en: String, bn: String -> s(lang, en, bn) }
    val time = if (stopped.at > 0) java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(stopped.at)) else null
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF7A0F2E), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = BarInk)
                Spacer(Modifier.width(8.dp))
                Text(if (stopped.otherVpn) t("Another VPN turned SafeNest off", "অন্য একটি VPN SafeNest বন্ধ করেছে")
                    else t("SafeNest protection is off", "SafeNest সুরক্ষা বন্ধ আছে"),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(
                t("Gambling sites aren't blocked right now", "এখন জুয়ার সাইট ব্লক হচ্ছে না") +
                    (time?.let { t(" (since $it).", " ($it থেকে)।") } ?: ".") +
                    (if (stopped.otherVpn) t(" Turn off the other VPN, then turn SafeNest back on.", " অন্য VPN বন্ধ করে SafeNest আবার চালু করুন।") else ""),
                color = BarInk, fontSize = 12.sp, lineHeight = 17.sp
            )
            Button(onClick = onRestore, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF7A0F2E)),
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(t("Turn protection back on", "সুরক্ষা আবার চালু করুন"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable private fun HomeScreen(lang:String,active:Boolean,paid:Boolean,dnsState:String,lockdown:Boolean,checkins:Int,onToggle:()->Unit,onReset:()->Unit,onCheckin:()->Unit,onProtect:()->Unit,onRecover:()->Unit) {
    val t={en:String,bn:String->s(lang,en,bn)}
    Text(t("YOUR SAFENEST","আপনার SAFENEST"),fontSize=9.sp,letterSpacing=1.1.sp,color=SoftText,fontWeight=FontWeight.Bold)
    Text(t("A little space to breathe.","একটু স্বস্তির জায়গা।"),fontSize=29.sp,fontWeight=FontWeight.Bold,color=Ink,letterSpacing=(-1).sp,modifier=Modifier.padding(top=4.dp))
    Text(t("Small steps count. Showing up is one.","ছোট পদক্ষেপও গুরুত্বপূর্ণ। এখানে আসাই একটি পদক্ষেপ।"),fontSize=13.sp,color=SoftText,modifier=Modifier.padding(top=4.dp))
    Spacer(Modifier.height(3.dp))
    Surface(shape=RoundedCornerShape(22.dp),color=Ink,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Shield,null,tint=Color(0xFFFFB7D4),modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(when { !active -> t("DNS FILTER IS OFF","DNS ফিল্টার বন্ধ"); lockdown -> t("CHECK VPN LOCKDOWN","VPN LOCKDOWN পরীক্ষা করুন"); dnsState == "failed" -> t("DNS NEEDS ATTENTION","DNS-এ সমস্যা আছে"); dnsState == "ok" -> t("DNS LOOKUP SUCCEEDED","DNS ঠিকানা পাওয়া গেছে"); else -> t("DNS AWAITING TEST","DNS পরীক্ষার অপেক্ষায়") },color=Color.White,fontSize=9.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Box(Modifier.size(8.dp).clip(CircleShape).background(if(active && dnsState=="ok" && !lockdown)Color(0xFF7DD1AA) else Color(0xFFFFC185))) }
        Spacer(Modifier.height(12.dp))
        Text(t("Breathe. You're choosing a safer moment.","শ্বাস নিন। নিরাপদ মুহূর্ত বেছে নিচ্ছেন।"), color=Color.White,fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold)
        Text(t("Your protection, one day at a time.","আপনার সুরক্ষা, প্রতিদিন একটু করে।"),color=Color(0xFFFFDDE8),fontSize=13.sp,lineHeight=18.sp,modifier=Modifier.padding(top=9.dp,bottom=16.dp))
        TextButton(onClick=onProtect){Text(t("Protection details ↗","সুরক্ষার বিস্তারিত ↗"),color=Color(0xFFFFDDE8),fontSize=11.sp)}
        Text(t("One day at a time. Keep going.","একদিন করে এগিয়ে চলুন।"),fontSize=9.sp,color=Color(0xFFBDBBCB))
        Spacer(Modifier.height(16.dp)); Button(onClick=onToggle,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Glass,contentColor=Ink)){Text(if(active)t("Protection settings","সুরক্ষা সেটিংস")else if(paid)t("Start protection","সুরক্ষা চালু করুন")else t("Verify paid access","পেইড মেয়াদ যাচাই করুন"),fontWeight=FontWeight.Bold,fontSize=11.sp)}
    } }
    StatCard(t("CHECK-INS","চেক-ইন"),"$checkins",t("Saved on this device","এই ডিভাইসে সংরক্ষিত"),Lilac,Modifier.fillMaxWidth())
    Surface(shape=RoundedCornerShape(18.dp),color=Mint,modifier=Modifier.fillMaxWidth().clickable{onReset()}){Column(Modifier.padding(18.dp)){Text(t("YOUR MOMENT","আপনার মুহূর্ত"),fontSize=9.sp,color=Violet,letterSpacing=1.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(t("The urge will pass. Give it a minute.","তাড়না চলে যাবে। একটু সময় দিন।"),fontSize=19.sp,fontWeight=FontWeight.Bold,color=Ink);Spacer(Modifier.height(10.dp));Button(onClick=onReset,shape=RoundedCornerShape(11.dp),colors=ButtonDefaults.buttonColors(containerColor=Glass,contentColor=Ink)){Text(t("Start a 60-second reset ↗","৬০ সেকেন্ডের বিরতি নিন ↗"),fontSize=10.sp)} } }
    Button(onClick=onCheckin,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink)){Text(t("＋  Log a check-in","＋  চেক-ইন লিখুন"),fontSize=11.sp)}
    RecoveryDisclaimer(lang)
}

@Composable private fun ProtectionScreen(lang:String,active:Boolean,paid:Boolean,category:RuleCategory,onToggle:()->Unit,onCategory:(RuleCategory)->Unit,onAdd:()->Unit,onSetup:()->Unit,onCatalog:()->Unit,onImport:()->Unit){
    val t={en:String,bn:String->s(lang,en,bn)}
    Text(t("YOUR DIGITAL GUARDRAILS","আপনার ডিজিটাল সুরক্ষা"),fontSize=9.sp,letterSpacing=1.sp,color=SoftText,fontWeight=FontWeight.Bold)
    Text(t("Protection, your way.","আপনার মতো করে সুরক্ষা।"),fontSize=24.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.padding(top=4.dp))
    Text(t("Website rules run quietly in the background.","ওয়েবসাইটের নিয়ম ব্যাকগ্রাউন্ডে কাজ করে।"),fontSize=11.sp,color=SoftText,modifier=Modifier.padding(top=4.dp,bottom=6.dp))
    Surface(shape=RoundedCornerShape(14.dp),color=Lilac){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.Top){Icon(Icons.Rounded.Info,null,tint=Violet,modifier=Modifier.size(18.dp));Spacer(Modifier.width(9.dp));Text(t("Android allows one active VPN. SafeNest can use Always-on, but this DNS-only prototype should not use Lockdown because it does not carry all internet traffic.","Android-এ একটি VPN সক্রিয় থাকে। SafeNest Always-on ব্যবহার করতে পারে, তবে DNS-only prototype সব internet traffic বহন করে না বলে Lockdown ব্যবহার করবেন না।"),fontSize=10.sp,color=SoftText,lineHeight=15.sp)} }
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { RuleCategory.entries.forEach { item -> FilterChip(selected=category==item,onClick={onCategory(item)},label={Text(when(item){RuleCategory.GAMBLING->t("Gambling","জুয়া");RuleCategory.ADULT->t("Adult","প্রাপ্তবয়স্ক");RuleCategory.PERSONAL->t("My list","আমার তালিকা")},fontSize=9.sp)},shape=RoundedCornerShape(10.dp)) } }
    Surface(shape=RoundedCornerShape(18.dp),color=Color.White,tonalElevation=0.dp,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(15.dp)){
        Text(t("Website filtering", "ওয়েবসাইট ফিল্টার"),fontWeight=FontWeight.Bold,fontSize=13.sp)
        Spacer(Modifier.height(8.dp))
        Text(t("The built-in website list is hidden. Filtering stays enabled, and you can add your own websites below.", "অন্তর্ভুক্ত ওয়েবসাইটের তালিকা লুকানো। ফিল্টার কাজ করে; নিচে নিজের ওয়েবসাইট যোগ করতে পারেন।"),color=SoftText,fontSize=11.sp)
        TextButton(onClick=onAdd,modifier=Modifier.fillMaxWidth()){Text(t("＋  Add a website","＋  ওয়েবসাইট যোগ করুন"),fontSize=10.sp)}
    } }
    Button(onClick=onToggle,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=if(active)Ink else Violet)){Text(if(active)t("DNS service on · Check setup","DNS সেবা চালু · সেটআপ দেখুন")else if(paid)t("Start device protection","ডিভাইস সুরক্ষা চালু করুন")else t("Verify paid access","পেইড মেয়াদ যাচাই করুন"),fontSize=11.sp)}
    OutlinedButton(onClick=onCatalog,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text(t("Verified catalog and updates","যাচাইকৃত ক্যাটালগ ও আপডেট"),fontSize=10.sp)}
    OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text(t("Import a website list JSON","ওয়েবসাইট তালিকা JSON আমদানি করুন"),fontSize=10.sp)}
    OutlinedButton(onClick=onSetup,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text(t("Permissions and app guard setup","VPN সুরক্ষা সেটআপ নির্দেশিকা"),fontSize=10.sp)}
    Text(t("Starter domain examples are limited and not an up-to-date universal list. Custom DNS, encrypted DNS, direct IPs, in-app content and mirrors can reduce coverage. No browsing history is uploaded by this prototype.","প্রাথমিক ডোমেইনের তালিকা সীমিত এবং হালনাগাদ সর্বজনীন তালিকা নয়। Custom DNS, encrypted DNS, সরাসরি IP, অ্যাপের ভেতরের কনটেন্ট ও mirror সাইট সুরক্ষার বাইরে থাকতে পারে। এই প্রোটোটাইপ ব্রাউজিং ইতিহাস আপলোড করে না।"),fontSize=9.sp,color=SoftText,lineHeight=14.sp)
}

@Composable private fun RecoveryScreen(lang:String,onReset:()->Unit,onCheckin:()->Unit,checkins:Int){ val t={en:String,bn:String->s(lang,en,bn)}
    val context= LocalContext.current
    val recoveryPrefs= remember { context.getSharedPreferences("safenest_recovery", Activity.MODE_PRIVATE) }
    var reflection by remember { mutableStateOf(recoveryPrefs.getString("reflection", "") ?: "") }
    var supportPlan by remember { mutableStateOf(recoveryPrefs.getString("support_plan", "") ?: "") }
    Text(t("SUPPORT FOR THE HUMAN SIDE","মানসিক সহায়তা"),fontSize=9.sp,letterSpacing=1.sp,color=SoftText,fontWeight=FontWeight.Bold)
    Text(t("Recovery isn’t a straight line.","পুনরুদ্ধার সবসময় সরল পথ নয়।"),fontSize=23.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.padding(top=4.dp))
    Text(t("No judgement. Just tools for the next moment.","কোনো বিচার নয়। শুধু পরবর্তী মুহূর্তের জন্য কিছু উপায়।"),fontSize=11.sp,color=SoftText,modifier=Modifier.padding(top=4.dp))
    Surface(shape=RoundedCornerShape(20.dp),color=Lilac){Column(Modifier.padding(19.dp)){Text(t("YOUR PLAN FOR A HARD MOMENT","কঠিন মুহূর্তের পরিকল্পনা"),fontSize=9.sp,color=Violet,letterSpacing=1.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Text(t("Pause. Name it. Choose your next step.","একটু থামুন। অনুভব করুন। পরের পদক্ষেপ বেছে নিন।"),fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink,lineHeight=27.sp);Spacer(Modifier.height(9.dp));Text(t("An urge is a feeling, not an instruction. Try a short reset or reach out to someone you trust.","তাড়না একটি অনুভূতি, নির্দেশ নয়। একটু বিরতি নিন বা বিশ্বাসের কাউকে জানান।"),fontSize=11.sp,color=SoftText,lineHeight=16.sp);Spacer(Modifier.height(14.dp));Button(onClick=onReset,shape=RoundedCornerShape(11.dp),colors=ButtonDefaults.buttonColors(containerColor=Glass,contentColor=Ink)){Text(t("Help me through this urge","এই তাড়না সামলাতে সাহায্য করুন"),fontSize=10.sp)} } }
    Surface(shape=RoundedCornerShape(18.dp),color=Glass){Column(Modifier.padding(17.dp)){Text(t("A QUICK CHECK-IN","একটি ছোট চেক-ইন"),fontSize=9.sp,color=SoftText,letterSpacing=1.sp,fontWeight=FontWeight.Bold);Text(t("How are you, really?","সত্যি করে বলুন, কেমন আছেন?"),fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp));Text(t("$checkins saved check-ins","${checkins}টি সংরক্ষিত চেক-ইন"),fontSize=10.sp,color=SoftText,modifier=Modifier.padding(top=4.dp));Button(onClick=onCheckin,modifier=Modifier.fillMaxWidth().padding(top=10.dp),shape=RoundedCornerShape(10.dp)){Text(t("Log a check-in","চেক-ইন লিখুন"),fontSize=10.sp)} } }
    Surface(shape=RoundedCornerShape(18.dp),color=Glass){Column(Modifier.padding(17.dp)){Text(t("A private reflection","একটি ব্যক্তিগত ভাবনা"),fontSize=14.sp,fontWeight=FontWeight.Bold);Text(t("Notice what was happening before the urge. No blame, just curiosity.","তাড়নার আগে কী ঘটছিল খেয়াল করুন। দোষ নয়, শুধু কৌতূহল।"),fontSize=10.sp,color=SoftText,modifier=Modifier.padding(top=5.dp));OutlinedTextField(value=reflection,onValueChange={reflection=it},modifier=Modifier.fillMaxWidth().padding(top=8.dp),minLines=3,label={Text(t("A note to future me","ভবিষ্যতের নিজের জন্য নোট"))});TextButton(onClick={recoveryPrefs.edit().putString("reflection",reflection).apply()}){Text(t("Save privately on this device","এই ডিভাইসে ব্যক্তিগতভাবে সংরক্ষণ করুন"),fontSize=10.sp)} } }
    Surface(shape=RoundedCornerShape(18.dp),color=Mint){Column(Modifier.padding(17.dp)){Text(t("My support plan","আমার সহায়তা পরিকল্পনা"),fontSize=14.sp,fontWeight=FontWeight.Bold);Text(t("Who could you contact, and what could you ask for?", "কাকে জানাতে পারেন, এবং কী সহায়তা চাইতে পারেন?"),fontSize=10.sp,color=SoftText,modifier=Modifier.padding(top=5.dp));OutlinedTextField(value=supportPlan,onValueChange={supportPlan=it},modifier=Modifier.fillMaxWidth().padding(top=8.dp),minLines=2,label={Text(t("A trusted person or service","বিশ্বাসের মানুষ বা পরিষেবা"))});TextButton(onClick={recoveryPrefs.edit().putString("support_plan",supportPlan).apply()}){Text(t("Save my plan on this device","আমার পরিকল্পনা এই ডিভাইসে সংরক্ষণ করুন"),fontSize=10.sp)} } }
    RecoveryDisclaimer(lang)
}

@Composable private fun InsightsScreen(lang:String,checkins:Int) {
    val t={en:String,bn:String->s(lang,en,bn)}
    Text(t("PROGRESS WITHOUT PRESSURE","চাপ ছাড়া অগ্রগতি"),fontSize=10.sp,color=SoftText)
    Text(t("Your own steps forward.","আপনার এগিয়ে চলা।"),fontSize=24.sp,fontWeight=FontWeight.Bold,color=Ink)
    StatCard(t("CHECK-INS","চেক-ইন"),"$checkins",t("Saved on this device","এই ডিভাইসে সংরক্ষিত"),Lilac,Modifier.fillMaxWidth())
    Text(t("Your check-ins stay on this device. SafeNest does not estimate money saved or uninterrupted protection days.","চেক-ইন এই ডিভাইসেই থাকে। SafeNest সাশ্রয় বা অবিচ্ছিন্ন সুরক্ষার সময় অনুমান করে না।"),fontSize=12.sp,color=SoftText)
}

@Composable private fun SettingsScreen(lang:String,active:Boolean,isOwner:Boolean,managedActive:Boolean,onLanguage:()->Unit,onVpnSettings:()->Unit,onSetup:()->Unit,onManaged:()->Unit){val t={en:String,bn:String->s(lang,en,bn)}
    Text(t("YOUR SPACE, YOUR CHOICES","আপনার জায়গা, আপনার সিদ্ধান্ত"),fontSize=9.sp,letterSpacing=1.sp,color=SoftText,fontWeight=FontWeight.Bold)
    Text(t("Settings & privacy.","সেটিংস ও গোপনীয়তা।"),fontSize=24.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.padding(top=4.dp))
    Surface(shape=RoundedCornerShape(18.dp),color=Glass){Column(Modifier.padding(17.dp)){Text(t("Your preferences","আপনার পছন্দ"),fontSize=14.sp,fontWeight=FontWeight.Bold);HorizontalDivider(Modifier.padding(vertical=12.dp),color=Color(0xFFF0EFF3));Row(verticalAlignment=Alignment.CenterVertically){Text(t("Interface language","ইন্টারফেসের ভাষা"),modifier=Modifier.weight(1f),fontSize=11.sp);TextButton(onClick=onLanguage){Text(t("English · বাংলা","বাংলা · English"),fontSize=10.sp)}};HorizontalDivider(color=Color(0xFFF0EFF3));Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t("VPN status","VPN-এর অবস্থা"),fontSize=11.sp);Text(if(active)t("DNS service running — see setup for health","DNS সেবা চলছে — অবস্থা সেটআপে দেখুন")else t("Not connected","সংযুক্ত নয়"),fontSize=9.sp,color=SoftText)};TextButton(onClick=onVpnSettings){Text(t("Android VPN settings ↗","Android VPN সেটিংস ↗"),fontSize=10.sp)}} } }
    Surface(shape=RoundedCornerShape(18.dp),color=Glass){Column(Modifier.padding(17.dp)){Text(t("Device protection","ডিভাইস সুরক্ষা"),fontSize=14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(t("Personal mode: Android VPN settings can make SafeNest always-on, but this DNS-only build must not use Lockdown. Owner-managed mode can prevent VPN reconfiguration and suspend VPN apps Android lets it manage.","Personal mode: Android VPN settings দিয়ে SafeNest সবসময় চালু রাখা যায়, তবে এই DNS-only build-এ Lockdown ব্যবহার করা যাবে না। Owner-managed mode VPN settings পরিবর্তন ঠেকাতে ও Android অনুমোদিত VPN apps suspend করতে পারে।"),fontSize=10.sp,color=SoftText,lineHeight=15.sp);TextButton(onClick=onSetup,modifier=Modifier.align(Alignment.End)){Text(t("Permissions and app guard setup ↗","Permissions and app guard setup ↗"),fontSize=10.sp)} } }
    if (BuildConfig.MANAGED_CONTROLS) Surface(shape=RoundedCornerShape(18.dp),color=Mint){Column(Modifier.padding(17.dp)){Text(t("Strong lock (managed phone)","শক্ত লক (পরিচালিত ফোন)"),fontSize=14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));Text(if(isOwner)t(if(managedActive)"Managed controls have saved setup state. Open Setup to verify the actual Android policies and administrator recovery code." else "This device is enrolled, but managed protection is not applied.",if(managedActive)"পরিচালিত নিয়ন্ত্রণের সেটআপ সংরক্ষিত। Android-এর প্রকৃত নীতি ও প্রশাসকের পুনরুদ্ধার কোড যাচাই করতে Setup খুলুন।" else "এই device enrolled, কিন্তু managed protection চালু হয়নি।") else t("Locks VPN settings and blocks uninstalling SafeNest until your paid period ends. Needs a phone set up for SafeNest: factory reset, tap the welcome screen six times, and scan the SafeNest setup QR.","পেইড মেয়াদ শেষ না হওয়া পর্যন্ত VPN সেটিংস লক থাকে এবং SafeNest আনইনস্টল করা যায় না। এজন্য ফোনটি SafeNest-এর জন্য সেট আপ করতে হবে: ফ্যাক্টরি রিসেট করে স্বাগত স্ক্রিনে ছয়বার ট্যাপ করুন এবং SafeNest সেটআপ QR স্ক্যান করুন।"),fontSize=10.sp,color=SoftText,lineHeight=15.sp);TextButton(onClick=onManaged,modifier=Modifier.align(Alignment.End)){Text(t(if(managedActive)"Review managed status" else "Review managed controls","Managed control পর্যালোচনা"),fontSize=10.sp)} } }
    Surface(shape=RoundedCornerShape(18.dp),color=Mint){Column(Modifier.padding(17.dp)){Text(t("Private by design","গোপনীয়তা অগ্রাধিকার"),fontSize=14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));Text(t("Your lists and check-ins stay on this device. Paid access is verified with Supabase Auth and server-issued entitlements. Passwords, session tokens and Accessibility observations are not saved or uploaded as history. Billing checkout is still being connected.","তালিকা ও চেক-ইন এই ফোনেই থাকে। Supabase Auth ও সার্ভারের অনুমতি দিয়ে পেইড মেয়াদ যাচাই হয়। পাসওয়ার্ড, টোকেন ও Accessibility তথ্য ইতিহাস হিসেবে জমা বা পাঠানো হয় না। বিলিং সংযোগের কাজ বাকি।"),fontSize=10.sp,color=SoftText,lineHeight=15.sp)} }
}

@Composable private fun StatCard(title:String,value:String,caption:String,color:Color,modifier:Modifier){Surface(modifier,shape=RoundedCornerShape(16.dp),color=color){Column(Modifier.padding(14.dp)){Text(title,fontSize=8.sp,color=SoftText,letterSpacing=.5.sp,fontWeight=FontWeight.Bold);Text(value,fontSize=23.sp,color=Ink,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=7.dp));Text(caption,fontSize=9.sp,color=SoftText,modifier=Modifier.padding(top=3.dp))}}}
@Composable private fun RecoveryDisclaimer(lang:String){Text(s(lang,"SafeNest recovery tools are supportive prompts, not medical care. If you are in immediate danger, contact local emergency services.","SafeNest পুনরুদ্ধার টুল সহায়ক নির্দেশনা, চিকিৎসা নয়। তাৎক্ষণিক বিপদে স্থানীয় জরুরি পরিষেবায় যোগাযোগ করুন।"),fontSize=9.sp,color=SoftText,lineHeight=14.sp,modifier=Modifier.padding(horizontal=3.dp))}
private fun s(lang:String,en:String,bn:String)=if(lang=="bn")bn else en
