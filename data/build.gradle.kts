import com.android.build.api.variant.BuildConfigField
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotzilla)
}
val localProperties = gradleLocalProperties(rootDir, providers)

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

android {
    namespace = "com.shdev.guardian.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        // AndroidBenchmarkRunner extends AndroidJUnitRunner, so plain instrumented tests still run;
        // it additionally puts BenchmarkRule tests in a foreground isolation activity.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // androidTest of a library variant is always debuggable and is usually run on an emulator,
        // both of which BenchmarkRule treats as hard errors. Suppressing them keeps the suite
        // runnable everywhere; absolute numbers from a debuggable/emulated run are only
        // comparable against other rows of the same report, never against production timings.
        // The full suppressible set of androidx.benchmark 1.5 (see androidx.benchmark.Errors).
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "ACTIVITY-MISSING,CODE-COVERAGE,DEBUGGABLE,DEVICE-MIRRORING,EMULATOR,ENG-BUILD," +
                    "JIT-ENABLED,LOW-BATTERY,NOT-AOT-COMPILED,SHELL-ACCESS-DENIED,SIMPLEPERF,UNLOCKED," +
                    "UNSUSTAINED-ACTIVITY-MISSING"
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

    // DataStore encryption benchmark suite (src/androidTest/kotlin/.../tests/datastore).
    // Declared explicitly: androidTestImplementation does not inherit the module's
    // `implementation` dependencies.
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.datastore)
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Copies the DataStore encryption benchmark report into the test source tree, so
 * `tests/datastore/report.html` is the artifact reviewers open.
 *
 * The suite writes to the instrumentation's `additionalTestOutputDir`, which AGP already pulls off
 * the device into `build/outputs/connected_android_test_additional_output/` after a connected run —
 * so this only has to pick the newest one up. Run it after `:data:connectedDebugAndroidTest`.
 */
tasks.register("pullDataStoreBenchmarkReport") {
    group = "verification"
    description = "Copy the DataStore encryption benchmark report into src/androidTest."

    val benchmarkPackage = "src/androidTest/kotlin/com/shdev/guardian/data/tests/datastore"
    val additionalOutputs =
        layout.buildDirectory.dir("outputs/connected_android_test_additional_output")
    val reportDestination = layout.projectDirectory.file("$benchmarkPackage/report.html").asFile
    // The suite reads this back as an asset on the next run, so a later run of one implementation
    // still reports every other column. Commit it along with the report.
    val historyDestination =
        layout.projectDirectory.file("src/androidTest/assets/datastore_benchmark_baseline.json").asFile

    inputs.dir(additionalOutputs).optional()
    outputs.files(reportDestination, historyDestination)

    doLast {
        val root = additionalOutputs.get().asFile
        val newest = root.walkTopDown()
            .filter { it.isFile && (it.name == "report.html" || it.name == "results.json") }
            .groupBy { it.name }
            .mapValues { (_, files) -> files.maxByOrNull { it.lastModified() } }

        val report = newest["report.html"]
        val results = newest["results.json"]
        if (report == null || results == null) {
            logger.warn("No benchmark output under $root — run :data:connectedDebugAndroidTest first.")
            return@doLast
        }
        report.copyTo(reportDestination, overwrite = true)
        historyDestination.parentFile.mkdirs()
        results.copyTo(historyDestination, overwrite = true)
        logger.lifecycle("Benchmark report:  ${reportDestination.absolutePath}")
        logger.lifecycle("Benchmark history: ${historyDestination.absolutePath}")
    }
}