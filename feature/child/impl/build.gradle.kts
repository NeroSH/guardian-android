plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotzilla)
}

android {
    namespace = "com.shdev.guardian.feature.child.impl"
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
    implementation(project(":feature:child:api"))
    implementation(project(":data"))
    implementation(project(":core:policy"))

    // Enforcement runtime
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation 3 (entry DSL for nav registration)
    implementation(libs.androidx.navigation3.runtime)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Camera + barcode (child scans the pairing QR)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // FCM token grabbed during pairing so the backend can address this device
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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