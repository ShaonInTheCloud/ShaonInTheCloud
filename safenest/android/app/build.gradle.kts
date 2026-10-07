plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.safenest.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safenest.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "0.4.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "LOCAL_TEST_BUILD", "false")
        buildConfigField("String", "SUPABASE_URL", "\"https://kflenmeizngmafwnwhgv.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_jt2VeNCAATz3iEiebx2Kog_ZiZVL7Vl\"")
        // The production static host redirects .html to the extensionless route.
        buildConfigField("String", "AUTH_CHALLENGE_URL", "\"https://mysafenestbd.com/android-captcha\"")
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_SYSTEM_GUARD", "false")
            buildConfigField("boolean", "MANAGED_CONTROLS", "false")
        }
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "ALLOW_SYSTEM_GUARD", "true")
            buildConfigField("boolean", "MANAGED_CONTROLS", "true")
        }
        create("lab") {
            dimension = "distribution"
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-test"
            buildConfigField("boolean", "LOCAL_TEST_BUILD", "true")
            buildConfigField("boolean", "ALLOW_SYSTEM_GUARD", "false")
            buildConfigField("boolean", "MANAGED_CONTROLS", "false")
        }
    }
    val uploadKey = System.getenv("SAFENEST_UPLOAD_KEYSTORE")
    if (!uploadKey.isNullOrBlank()) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadKey)
                storePassword = System.getenv("SAFENEST_UPLOAD_STORE_PASSWORD") ?: error("Upload store password is required")
                keyAlias = System.getenv("SAFENEST_UPLOAD_KEY_ALIAS") ?: error("Upload key alias is required")
                keyPassword = System.getenv("SAFENEST_UPLOAD_KEY_PASSWORD") ?: error("Upload key password is required")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("upload")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

androidComponents {
    beforeVariants(selector().withFlavor("distribution" to "lab").withBuildType("release")) {
        it.enable = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("junit:junit:4.13.2")
}
