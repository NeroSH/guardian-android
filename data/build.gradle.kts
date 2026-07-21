import com.android.build.api.variant.BuildConfigField
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotzilla)
}
val localProperties = gradleLocalProperties(rootDir, providers)

android {
    namespace = "com.shdev.guardian.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Export Room schemas so MigrationTestHelper can verify migrations.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        buildConfig = true
    }
}

androidComponents.onVariants {
    it.buildConfigFields?.put(
        "BASE_URL",
        BuildConfigField(
            type = "String",
            value = "\"${localProperties.getProperty("baseUrl")}\"",
            comment = "Base API URL for network calls"
        )
    )
}

dependencies {
    implementation(project(":core:policy"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.logging)
    implementation(libs.slf4j.android)

    implementation(libs.androidx.datastore.preferences)

    // Firebase is initialized programmatically from here (FirebaseInitProvider is removed from the
    // manifest), and the FCM token is fetched here before being registered with the backend.
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