/*
 * Copyright 2026 NeroSH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shdev.guardian.data.tests.datastore

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.shdev.guardian.data.tests.datastore.BenchmarkResultStore.BASELINE_ASSET
import com.shdev.guardian.data.tests.datastore.BenchmarkResultStore.publishDirectory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

const val BENCHMARK_LOG_TAG = "DataStoreBenchmark"

/** One measured (implementation × scenario × dataset) cell. */
@Serializable
data class BenchmarkRecord(
    val implementationId: String,
    val implementationName: String,
    val implementationDescription: String,
    val isReference: Boolean,
    val scenario: String,
    val scenarioLabel: String,
    val scenarioGroup: String,
    val dataset: String,
    val payloadJsonBytes: Long,
    val diskBytes: Long = -1L,
    val iterations: Int = 0,
    val minNs: Long = 0L,
    val medianNs: Long = 0L,
    val meanNs: Double = 0.0,
    val p90Ns: Long = 0L,
    val maxNs: Long = 0L,
    val stdDevNs: Double = 0.0,
    val memoryIterations: Int = 0,
    val medianAllocatedBytes: Long = 0L,
    val maxAllocatedBytes: Long = 0L,
    val retainedHeapDeltaBytes: Long = 0L,
    val pssDeltaKb: Long = 0L,
    val gcCount: Long = 0L,
    val notes: String? = null,
    val recordedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    val key: String get() = "$implementationId|$scenario|$dataset"
}

/**
 * The answer to "does changing one field rewrite the whole encrypted file?", with the evidence that
 * produced it rather than just a verdict.
 */
@Serializable
data class PartialUpdateFinding(
    val implementationId: String,
    val implementationName: String,
    val dataset: String,
    val payloadJsonBytes: Long,
    val fileBytesBefore: Long,
    val fileBytesAfter: Long,
    /** Fraction of overlapping bytes that differ after changing a single Long field. */
    val changedByteRatio: Double,
    /** Bytes at the head of the file that survived unchanged. */
    val identicalPrefixBytes: Long,
    /** A new inode means the file was replaced wholesale (temp file + rename), not patched. */
    val inodeChanged: Boolean,
    val singleFieldUpdateMedianNs: Long,
    val fullReplaceMedianNs: Long,
    val noOpUpdateMedianNs: Long,
    val singleFieldAllocatedBytes: Long,
    /** What an update that writes nothing still allocates — the read half of `updateData`. */
    val noOpUpdateAllocatedBytes: Long = 0L,
    val rewritesWholeFile: Boolean,
    val verdict: String,
    val recordedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    val key: String get() = "$implementationId|$dataset"
}

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val sdkInt: Int,
    val abi: String,
    val isEmulator: Boolean,
    val maxHeapBytes: Long,
) {
    companion object {
        fun current(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            sdkInt = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            isEmulator = Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk"),
            maxHeapBytes = Runtime.getRuntime().maxMemory(),
        )
    }
}

@Serializable
data class BenchmarkReportData(
    val device: DeviceInfo,
    val generatedAtEpochMillis: Long,
    val records: List<BenchmarkRecord>,
    val findings: List<PartialUpdateFinding>,
)

/**
 * Collects results and keeps `report.html` on disk current.
 *
 * Results are persisted and merged by (implementation, scenario, dataset) instead of being held in
 * memory for the length of a run. That is what makes the side-by-side comparison work in practice:
 * a future algorithm can be benchmarked on its own, months later, in a run that executes only its
 * own test class, and its column simply appears next to the existing ones. Re-running an
 * implementation replaces its own rows and leaves every other column alone.
 *
 * The report is rewritten on every record rather than at the end of the suite, so a run that is
 * interrupted still leaves a readable report of everything measured up to that point.
 *
 * Start from scratch with `-e datastore.bench.reset true`.
 */
object BenchmarkResultStore {

    private const val RESULTS_FILE = "results.json"
    private const val REPORT_FILE = "report.html"

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val records = LinkedHashMap<String, BenchmarkRecord>()
    private val findings = LinkedHashMap<String, PartialUpdateFinding>()
    private var loaded = false

    /**
     * The accumulated history of every implementation ever benchmarked, shipped inside the test APK.
     *
     * Nothing on the device survives a run: AGP clears `additionalTestOutputDir` before each
     * connected run, and it re-installs the test APK, which takes the app's internal *and* external
     * directories with it. So the history lives in the repository instead —
     * `src/androidTest/assets/[BASELINE_ASSET]`, refreshed by `:data:pullDataStoreBenchmarkReport`
     * and committed like any other file.
     *
     * That is what makes the side-by-side comparison work across time: a run that executes only a
     * new algorithm's test class still renders every previously measured column beside it, and the
     * history is reviewable in a diff rather than trapped on one developer's phone.
     */
    private const val BASELINE_ASSET = "datastore_benchmark_baseline.json"

    /** Run output. Published copies also go to [publishDirectory] for Gradle to collect. */
    private val stateDirectory: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "datastore_benchmark").apply { mkdirs() }
    }

    /**
     * Where a copy is published for collection. AGP sets `additionalTestOutputDir` when it is going
     * to pull device files into `build/outputs/` after the run, so the report ends up next to the
     * other build artifacts. Null when the run was not launched by Gradle.
     */
    private val publishDirectory: File? by lazy {
        InstrumentationRegistry.getArguments()
            .getString("additionalTestOutputDir")
            ?.let { File(it, "datastore_benchmark") }
            ?.takeIf { it.exists() || it.mkdirs() }
    }

    val resultsFile: File get() = File(stateDirectory, RESULTS_FILE)
    val reportFile: File get() = File(stateDirectory, REPORT_FILE)

    @Synchronized
    fun record(record: BenchmarkRecord) {
        ensureLoaded()
        records[record.key] = record
        flush()
    }

    @Synchronized
    fun record(finding: PartialUpdateFinding) {
        ensureLoaded()
        findings[finding.key] = finding
        flush()
    }

    @Synchronized
    fun snapshot(): BenchmarkReportData {
        ensureLoaded()
        return BenchmarkReportData(
            device = DeviceInfo.current(),
            generatedAtEpochMillis = System.currentTimeMillis(),
            records = records.values.toList(),
            findings = findings.values.toList(),
        )
    }

    /** Logs where the report is; call from a test's `@AfterClass`. */
    @Synchronized
    fun logLocation() {
        Log.i(BENCHMARK_LOG_TAG, "Accumulated results: ${resultsFile.absolutePath}")
        Log.i(BENCHMARK_LOG_TAG, "HTML report: ${reportFile.absolutePath}")
        publishDirectory?.let { Log.i(BENCHMARK_LOG_TAG, "Published for collection: $it") }
        Log.i(
            BENCHMARK_LOG_TAG,
            "Collect with: ./gradlew :data:pullDataStoreBenchmarkReport — it also refreshes " +
                    "src/androidTest/assets/$BASELINE_ASSET, which is what carries these results into " +
                    "the next run's report.",
        )
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (resetRequested()) return
        // The test APK's own assets, not the target app's.
        val baseline = runCatching {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(BASELINE_ASSET)
                .use { it.readBytes().toString(Charsets.UTF_8) }
                .let { json.decodeFromString(BenchmarkReportData.serializer(), it) }
        }.getOrElse { failure ->
            // Absent on the very first run, and after a deliberate reset — not an error.
            Log.i(BENCHMARK_LOG_TAG, "No baseline history ($BASELINE_ASSET): ${failure.message}")
            return
        }
        baseline.records.forEach { records[it.key] = it }
        baseline.findings.forEach { findings[it.key] = it }
        Log.i(
            BENCHMARK_LOG_TAG,
            "Loaded ${baseline.records.size} records from $BASELINE_ASSET; this run's " +
                    "measurements are merged over them by implementation, scenario and dataset.",
        )
    }

    private fun flush() {
        val data = BenchmarkReportData(
            device = DeviceInfo.current(),
            generatedAtEpochMillis = System.currentTimeMillis(),
            records = records.values.toList(),
            findings = findings.values.toList(),
        )
        runCatching {
            val results = json.encodeToString(BenchmarkReportData.serializer(), data)
            val report = HtmlReportGenerator.render(data)
            resultsFile.writeText(results)
            reportFile.writeText(report)
            publishDirectory?.let { directory ->
                File(directory, RESULTS_FILE).writeText(results)
                File(directory, REPORT_FILE).writeText(report)
            }
        }.onFailure { Log.e(BENCHMARK_LOG_TAG, "Failed to write benchmark report", it) }
    }

    private fun resetRequested(): Boolean =
        InstrumentationRegistry.getArguments().getString("datastore.bench.reset")
            ?.toBoolean() == true
}
