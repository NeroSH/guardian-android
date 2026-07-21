plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotzilla)
}

android {
    namespace = "com.shdev.guardian.feature.parent.impl"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":feature:parent:api"))
    implementation(project(":data"))
    // NavigationState/Navigator — the dashboard's bottom-nav tabs each keep their own back stack.
    implementation(project(":core:navigation"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    // activity-compose supplies LocalActivity — Credential Manager needs the host Activity.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation 3 — entry DSL for nav registration, plus NavDisplay + DialogSceneStrategy for the
    // dashboard's nested tab display and the account settings dialog.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Credential Manager — verified email (OTP-less sign-in). Call site is @RequiresApi(28) and
    // runtime-gated; module minSdk stays 26.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // QR encode (parent renders the pairing code)
    implementation(libs.zxing.core)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}

kotzilla {
    enabled = true                      // Master switch (default: true)
    versionName = "1.0.0"               // Auto-detected if omitted
    uploadMappingFile = true            // ProGuard mapping upload (default: true)
    composeInstrumentation = true       // Compose NavHost tracking (default: true)
    obfuscateGeneratedConfig =
        false     // Encodes API key to avoid visibility in bundle (default: true)
    autoInjectXcodeScript = true        // iOS dSYM script (default: true)
    autoAddDependencies = true          // Auto-add SDK deps (default: true)
    displayLogs =
        false                 // Debug logging (default: false) — also enables runtime logs, see below
    skipBuildReportFailure = true       // Don't fail the build on a FAIL report (default: true)
    buildReport = true                  // Fetch the build report after assemble (default: true)

    // Consent — see the Privacy & user consent page
    consentRequired = false             // Gate all telemetry behind user consent (default: false)

    // Automatic start — see the Automatic start (early start) page
    earlyStart = true                   // Auto-boot the SDK at process start (default: true)
    androidEarlyStart = true            // Per-platform overrides; inherit earlyStart if omitted
    iosEarlyStart = true
    jvmEarlyStart = true
}