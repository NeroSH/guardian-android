plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    // No Kotlin plugin — AGP 9 built-in Kotlin compiles src/main/kotlin.
}

android {
    namespace = "com.shdev.guardian.macrobenchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // Gradle Managed Device for headless baseline-profile generation.
    // AGP 9: `managedDevices.devices` was removed — use `localDevices`.
    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp" // Rootable, GMS-less — required for profile capture.
                }
            }
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices =
        true // Generation runs on the GMD; benchmarks still use connected devices.
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
