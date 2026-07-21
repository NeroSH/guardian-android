plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.shdev.guardian.core.navigation"
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
    // Compose runtime + saveable — the navigation state survives config change and process death.
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)

    // Navigation 3 runtime is part of this module's public surface (NavKey, NavEntry, NavBackStack).
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.kotlinx.serialization.json)
}
