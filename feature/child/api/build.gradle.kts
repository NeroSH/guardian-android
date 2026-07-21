plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.shdev.guardian.feature.child.api"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Route keys are Navigation 3 NavKeys, serialized for process-death survival.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
