import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kotzilla)
}
val localProperties = gradleLocalProperties(rootDir, providers)

android {
    namespace = "com.shdev.guardian"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shdev.guardian"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        create("release") {
            /**
             * Create following fields in local.properties
             *      `keyAlias`
             *      `keyPassword`
             *      `storeFile`
             *      `storePassword`
             */
            keyAlias = localProperties.getProperty("keyAlias")
            keyPassword = localProperties.getProperty("keyPassword")
            storeFile = file(localProperties.getProperty("storeFile"))
            storePassword = localProperties.getProperty("storePassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:policy"))
    implementation(project(":core:navigation"))
    implementation(project(":data"))
    implementation(project(":feature:child:api"))
    implementation(project(":feature:child:impl"))
    implementation(project(":feature:parent:api"))
    implementation(project(":feature:parent:impl"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Pins work-runtime to the catalog version (koin-workmanager otherwise pulls an uncached one).
    implementation(libs.androidx.work.runtime)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.workmanager)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Installs & AOT-compiles the packaged baseline profile at runtime; enables CompilationMode.Partial in benchmarks.
    implementation(libs.androidx.profileinstaller)
    // Consumes profiles produced by the :macrobenchmark test module (merged into the release ART profile).
    "baselineProfile"(project(":macrobenchmark"))
}

baselineProfile {
    // Write generated profiles into app/src/release/generated/baselineProfiles/ (commit them).
    saveInSrc = true
    // Keep generation an explicit ./gradlew :app:generateBaselineProfile task, not part of every release build.
    automaticGenerationDuringBuild = false
    // R8 startup-profile-driven dex layout (release is minified, so this composes correctly).
    dexLayoutOptimization = true
}

kotzilla {
    enabled = true                      // Master switch (default: true)
    uploadMappingFile = true            // ProGuard mapping upload (default: true)
    composeInstrumentation = true       // Compose NavHost tracking (default: true)
    obfuscateGeneratedConfig =
        true     // Encodes API key to avoid visibility in bundle (default: true)
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
