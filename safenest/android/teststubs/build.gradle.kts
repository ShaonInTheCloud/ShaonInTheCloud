// Emulator QA only: tiny stand-in apps with the package IDs / names SafeNest must block (or must leave alone).
// Never published. CI installs them before GuardDeviceFlowTest.
plugins { id("com.android.application") }

android {
    namespace = "com.safenest.teststub"
    compileSdk = 36
    defaultConfig { minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "qa" }
    flavorDimensions += "stub"
    productFlavors {
        fun stub(name: String, id: String, label: String) = create(name) {
            dimension = "stub"; applicationId = id; resValue("string", "stub_label", label)
        }
        stub("operabrowser", "com.opera.browser", "Opera")          // VPN browser: whole app blocked
        stub("krikya", "com.krikya.krikya", "Krikya")               // exact gambling package ID
        stub("luckycasino", "com.example.luckycasino", "Lucky Casino Slots") // gambling word in the app name
        stub("vpn", "com.example.stubvpn", "Stub VPN")              // declares a VpnService: detected VPN app
        stub("notes", "com.example.notes", "Notes")                 // ordinary app: must stay open
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
