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

import android.content.Context
import android.system.Os
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.shdev.guardian.data.tests.datastore.models.BenchmarkStoreFactory
import com.shdev.guardian.data.tests.datastore.models.ManagedBenchmarkStore
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkDataSets
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkDataset
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload
import com.shdev.guardian.data.tests.datastore.schema.DatasetSize
import com.shdev.guardian.data.tests.datastore.schema.withNextRevision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The whole benchmark, written once against [EncryptedDataStoreBenchmark].
 *
 * A new encryption implementation joins the comparison by subclassing this and overriding [target] —
 * nothing else. Every scenario, dataset, statistic and report row comes for free, and because the
 * results are keyed by implementation id, the new column lands next to the existing ones even when
 * it is benchmarked in a completely separate run.
 *
 * Each test opens its stores through a [BenchmarkStoreFactory], which hands out a unique file name
 * per store. DataStore caches documents in memory keyed by file, so a shared name would let one
 * scenario's cache serve another's "cold" read and quietly delete the decryption cost from the
 * results.
 */
abstract class AbstractEncryptedDataStoreBenchmarkTest {

    /** The implementation under test. */
    protected abstract val target: EncryptedDataStoreBenchmark

    protected val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var factory: BenchmarkStoreFactory

    /** Keeps results of measured expressions reachable so nothing is optimized away. */
    @Volatile
    private var sink: Any? = null

    /**
     * The default value every benchmark store is opened with.
     *
     * Deliberately not `BenchmarkPayload()`: DataStore compares the new value against the current
     * one and skips the write when they match, so a store whose default equalled the Empty dataset
     * would turn that dataset's "create" into a no-op and report a write that never happened. A
     * sentinel no dataset can produce makes the first write real for all three sizes.
     */
    private val absentDefault = BenchmarkPayload(schemaVersion = Int.MIN_VALUE)

    private fun openFresh(tag: String): ManagedBenchmarkStore =
        factory.openFresh(tag, absentDefault)

    private fun open(file: File): ManagedBenchmarkStore = factory.open(file, absentDefault)

    @Before
    fun openFactory() {
        factory = BenchmarkStoreFactory(context, target)
    }

    @After
    fun closeFactory() {
        factory.cleanUp()
    }

    // ---- create ---------------------------------------------------------------------------------

    @Test
    fun create_empty() = measureCreate(DatasetSize.EMPTY)

    @Test
    fun create_medium() = measureCreate(DatasetSize.MEDIUM)

    @Test
    fun create_large() = measureCreate(DatasetSize.LARGE)

    // ---- read -----------------------------------------------------------------------------------

    @Test
    fun coldRead_empty() = measureColdRead(DatasetSize.EMPTY)

    @Test
    fun coldRead_medium() = measureColdRead(DatasetSize.MEDIUM)

    @Test
    fun coldRead_large() = measureColdRead(DatasetSize.LARGE)

    @Test
    fun warmRead_empty() = measureWarmRead(DatasetSize.EMPTY)

    @Test
    fun warmRead_medium() = measureWarmRead(DatasetSize.MEDIUM)

    @Test
    fun warmRead_large() = measureWarmRead(DatasetSize.LARGE)

    // ---- update ---------------------------------------------------------------------------------

    @Test
    fun updateFullDocument_empty() = measureFullUpdate(DatasetSize.EMPTY)

    @Test
    fun updateFullDocument_medium() = measureFullUpdate(DatasetSize.MEDIUM)

    @Test
    fun updateFullDocument_large() = measureFullUpdate(DatasetSize.LARGE)

    @Test
    fun updateSingleField_empty() = measureSingleFieldUpdate(DatasetSize.EMPTY)

    @Test
    fun updateSingleField_medium() = measureSingleFieldUpdate(DatasetSize.MEDIUM)

    @Test
    fun updateSingleField_large() = measureSingleFieldUpdate(DatasetSize.LARGE)

    @Test
    fun noOpUpdate_empty() = measureNoOpUpdate(DatasetSize.EMPTY)

    @Test
    fun noOpUpdate_medium() = measureNoOpUpdate(DatasetSize.MEDIUM)

    @Test
    fun noOpUpdate_large() = measureNoOpUpdate(DatasetSize.LARGE)

    // ---- delete ---------------------------------------------------------------------------------

    @Test
    fun deleteByReset_empty() = measureResetDelete(DatasetSize.EMPTY)

    @Test
    fun deleteByReset_medium() = measureResetDelete(DatasetSize.MEDIUM)

    @Test
    fun deleteByReset_large() = measureResetDelete(DatasetSize.LARGE)

    @Test
    fun deleteFile_empty() = measureFileDelete(DatasetSize.EMPTY)

    @Test
    fun deleteFile_medium() = measureFileDelete(DatasetSize.MEDIUM)

    @Test
    fun deleteFile_large() = measureFileDelete(DatasetSize.LARGE)

    // ---- codec and serialization in isolation ---------------------------------------------------

    @Test
    fun rawCodec_empty() = measureRawCodec(DatasetSize.EMPTY)

    @Test
    fun rawCodec_medium() = measureRawCodec(DatasetSize.MEDIUM)

    @Test
    fun rawCodec_large() = measureRawCodec(DatasetSize.LARGE)

    @Test
    fun jsonOnly_empty() = measureJsonOnly(DatasetSize.EMPTY)

    @Test
    fun jsonOnly_medium() = measureJsonOnly(DatasetSize.MEDIUM)

    @Test
    fun jsonOnly_large() = measureJsonOnly(DatasetSize.LARGE)

    // ---- footprint and partial-update analysis --------------------------------------------------

    @Test
    fun diskFootprint_empty() = measureDiskFootprint(DatasetSize.EMPTY)

    @Test
    fun diskFootprint_medium() = measureDiskFootprint(DatasetSize.MEDIUM)

    @Test
    fun diskFootprint_large() = measureDiskFootprint(DatasetSize.LARGE)

    @Test
    fun partialUpdateOverhead_medium() = analyzePartialUpdate(DatasetSize.MEDIUM)

    @Test
    fun partialUpdateOverhead_large() = analyzePartialUpdate(DatasetSize.LARGE)

    // ---- scenarios ------------------------------------------------------------------------------

    private fun measureCreate(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        var store: ManagedBenchmarkStore? = null
        var diskBytes = -1L
        val measured = harnessFor(size).measure(
            setup = { store = openFresh("create_${size.name}") },
            block = { store!!.dataStore.updateData { dataset.primary } },
            teardown = {
                diskBytes = store!!.diskBytes()
                factory.discard(store)
            },
        )
        record(BenchmarkScenario.CREATE, dataset, measured, diskBytes)
    }

    /**
     * A cold read is the only path that pays for decryption, so it gets a fresh DataStore over a
     * fresh copy of the populated file every iteration — a second instance over the same path would
     * either hit the process-wide cache or collide with the still-closing previous one.
     */
    private fun measureColdRead(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val source = populatedFile(dataset, "cold_source_${size.name}")

        // Correctness gate: if decryption silently failed, the serializer would hand back the
        // default value and every timing below would be measuring nothing.
        val verification = open(factory.copyOf(source, "cold_verify_${size.name}"))
        assertEquals(
            "Cold read did not round-trip the document for ${target.id}",
            dataset.primary,
            runBlocking { verification.dataStore.data.first() },
        )
        factory.discard(verification)

        var store: ManagedBenchmarkStore? = null
        val measured = harnessFor(size).measure(
            setup = { store = open(factory.copyOf(source, "cold_${size.name}")) },
            block = { sink = store!!.dataStore.data.first() },
            teardown = { factory.discard(store!!) },
        )
        factory.deleteFiles(source)
        record(BenchmarkScenario.COLD_READ, dataset, measured)
    }

    private fun measureWarmRead(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("warm_${size.name}")
        runBlocking { store.dataStore.updateData { dataset.primary } }
        val measured = harnessFor(size).measure(
            block = { sink = store.dataStore.data.first() },
        )
        factory.discard(store)
        record(BenchmarkScenario.WARM_READ, dataset, measured)
    }

    private fun measureFullUpdate(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("update_full_${size.name}")
        runBlocking { store.dataStore.updateData { dataset.primary } }
        // A monotonic flip, not the iteration index: warmup and the timed pass both start at 0, and
        // writing the same value twice in a row would be skipped by DataStore's equality check.
        var flip = 0
        val measured = harnessFor(size).measure(
            block = {
                val next = if (flip++ % 2 == 0) dataset.alternate else dataset.primary
                store.dataStore.updateData { next }
            },
        )
        factory.discard(store)
        record(BenchmarkScenario.UPDATE_FULL, dataset, measured)
    }

    private fun measureSingleFieldUpdate(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("update_field_${size.name}")
        runBlocking { store.dataStore.updateData { dataset.primary } }
        val measured = harnessFor(size).measure(
            block = { store.dataStore.updateData { it.withNextRevision() } },
        )
        factory.discard(store)
        record(BenchmarkScenario.UPDATE_SINGLE_FIELD, dataset, measured)
    }

    private fun measureNoOpUpdate(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("update_noop_${size.name}")
        runBlocking { store.dataStore.updateData { dataset.primary } }
        val sizeBefore = store.diskBytes()
        val modifiedBefore = store.file.lastModified()
        val measured = harnessFor(size).measure(
            block = { store.dataStore.updateData { it } },
        )
        // Proves the row means what the report says it means: no bytes were written.
        assertEquals("A no-op update changed the file size", sizeBefore, store.diskBytes())
        assertEquals("A no-op update rewrote the file", modifiedBefore, store.file.lastModified())
        factory.discard(store)
        record(
            BenchmarkScenario.NO_OP_UPDATE,
            dataset,
            measured,
            notes = "File untouched (size and mtime asserted unchanged) — the write was skipped " +
                    "once equals() matched, but everything before it still ran.",
        )
    }

    private fun measureResetDelete(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("delete_reset_${size.name}")
        val measured = harnessFor(size).measure(
            // `alternate` rather than `primary`: for the empty dataset `primary` *is* the default,
            // and resetting to a value that already equals the current one would not write.
            setup = { store.dataStore.updateData { dataset.alternate } },
            block = { store.dataStore.updateData { BenchmarkPayload() } },
        )
        factory.discard(store)
        record(BenchmarkScenario.DELETE_RESET, dataset, measured)
    }

    private fun measureFileDelete(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        var file: File? = null
        val measured = harnessFor(size).measure(
            setup = {
                val store = openFresh("delete_file_${size.name}")
                store.dataStore.updateData { dataset.primary }
                factory.close(store)
                file = store.file
            },
            block = { file!!.delete() },
            teardown = { factory.deleteFiles(file!!) },
        )
        record(BenchmarkScenario.DELETE_FILE, dataset, measured)
    }

    private fun measureRawCodec(size: DatasetSize) {
        val codec = target.rawCodec(context)
        assumeTrue("${target.displayName} exposes no raw codec — nothing to measure", codec != null)
        requireNotNull(codec)

        val dataset = BenchmarkDataSets.of(size)
        val plain = dataset.primaryJson
        val encrypted = codec.encrypt(plain)
        assertEquals("Codec round trip failed for ${target.id}", plain, codec.decrypt(encrypted))

        val encryptResult = harnessFor(size).measure(block = { sink = codec.encrypt(plain) })
        record(
            BenchmarkScenario.ENCRYPT_RAW,
            dataset,
            encryptResult,
            diskBytes = encrypted.size.toLong(),
            notes = "Ciphertext ${encrypted.size} B for ${plain.length} B of JSON.",
        )

        val decryptResult = harnessFor(size).measure(block = { sink = codec.decrypt(encrypted) })
        record(BenchmarkScenario.DECRYPT_RAW, dataset, decryptResult)
    }

    private fun measureJsonOnly(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val serializer = BenchmarkPayload.serializer()
        val encodeResult = harnessFor(size).measure(
            block = { sink = target.json.encodeToString(serializer, dataset.primary) },
        )
        record(BenchmarkScenario.JSON_ENCODE, dataset, encodeResult)

        val encoded = dataset.primaryJson
        val decodeResult = harnessFor(size).measure(
            block = { sink = target.json.decodeFromString(serializer, encoded) },
        )
        record(BenchmarkScenario.JSON_DECODE, dataset, decodeResult)
    }

    private fun measureDiskFootprint(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("footprint_${size.name}")
        runBlocking { store.dataStore.updateData { dataset.primary } }
        val diskBytes = store.diskBytes()
        factory.discard(store)

        val overhead = diskBytes - dataset.primaryJsonBytes
        record(
            BenchmarkScenario.DISK_FOOTPRINT,
            dataset,
            measured = null,
            diskBytes = diskBytes,
            notes = "${overhead.withSign()} B against the plaintext JSON.",
        )
    }

    /**
     * Answers, with evidence rather than assertion, what one changed field costs: how much of the
     * file the write touched, whether the file was replaced or patched, and how the cost compares
     * against replacing the whole document and against an update that changes nothing.
     */
    private fun analyzePartialUpdate(size: DatasetSize) {
        val dataset = BenchmarkDataSets.of(size)
        val store = openFresh("partial_${size.name}")
        runBlocking { store.dataStore.updateData { dataset.primary } }

        val before = store.file.readBytes()
        val inodeBefore = inodeOf(store.file)
        runBlocking { store.dataStore.updateData { it.withNextRevision() } }
        val after = store.file.readBytes()
        val inodeAfter = inodeOf(store.file)

        assertFalse(
            "Changing a field did not reach the file — the rest of this analysis would be meaningless",
            before.contentEquals(after),
        )

        val overlap = minOf(before.size, after.size)
        var identicalPrefix = 0
        while (identicalPrefix < overlap && before[identicalPrefix] == after[identicalPrefix]) {
            identicalPrefix++
        }
        var changedBytes = 0
        for (index in 0 until overlap) {
            if (before[index] != after[index]) changedBytes++
        }
        val changedRatio = if (overlap == 0) 1.0 else changedBytes.toDouble() / overlap

        val harness = harnessFor(size)
        val singleField =
            harness.measure(block = { store.dataStore.updateData { it.withNextRevision() } })
        var flip = 0
        val fullReplace = harness.measure(
            block = {
                val next = if (flip++ % 2 == 0) dataset.alternate else dataset.primary
                store.dataStore.updateData { next }
            },
        )
        val noOp = harness.measure(block = { store.dataStore.updateData { it } })

        assertTrue(
            "An update that changes nothing should be cheaper than one that does — DataStore's " +
                    "equality check appears to be gone",
            noOp.timing.medianNs < singleField.timing.medianNs,
        )

        val inodeChanged = inodeBefore != inodeAfter && inodeBefore >= 0 && inodeAfter >= 0
        val rewritesWholeFile = inodeChanged || changedRatio > 0.5
        val ratioAgainstFull = if (fullReplace.timing.medianNs > 0) {
            singleField.timing.medianNs.toDouble() / fullReplace.timing.medianNs
        } else {
            0.0
        }

        val verdict = buildString {
            append(
                String.format(
                    Locale.US,
                    "Changing one Long field of a %,d B document cost %.2f ms and allocated %,d B — " +
                            "%.2f× the cost of replacing the whole document. ",
                    dataset.primaryJsonBytes,
                    singleField.timing.medianNs / 1_000_000.0,
                    singleField.memory.medianAllocatedBytes,
                    ratioAgainstFull,
                ),
            )
            if (rewritesWholeFile) {
                append("The write was not partial: ")
                if (inodeChanged) {
                    append("the file was replaced wholesale (new inode — temp file, fsync, atomic rename)")
                }
                if (inodeChanged && changedRatio > 0.5) append(", and ")
                if (changedRatio > 0.5) {
                    append(String.format(Locale.US, "%.1f%%", changedRatio * 100))
                    append(" of its bytes differ (a fresh IV diffuses the whole ciphertext)")
                }
                append(
                    ". Jetpack DataStore has no delta path — updateData re-serializes the entire " +
                            "document (and re-encrypts it, where the implementation encrypts) on every " +
                            "mutation, so update cost scales with document size, not with the size of " +
                            "the change. ",
                )
            } else {
                append(String.format(Locale.US, "Only %.2f%%", changedRatio * 100))
                append(
                    " of the bytes changed and the file kept its inode, so this implementation " +
                            "does write partially. ",
                )
            }
            val noOpShare = if (singleField.timing.medianNs > 0) {
                noOp.timing.medianNs.toDouble() / singleField.timing.medianNs
            } else {
                0.0
            }
            if (noOpShare < NO_OP_IS_FREE_THRESHOLD) {
                append(
                    String.format(
                        Locale.US,
                        "An update that returns an unchanged instance is elided almost entirely " +
                                "after the equals() comparison, costing %.1f µs and %,d B.",
                        noOp.timing.medianNs / 1_000.0,
                        noOp.memory.medianAllocatedBytes,
                    ),
                )
            } else {
                append(
                    String.format(
                        Locale.US,
                        "Note what the equality check does and does not save: an update that " +
                                "returns an unchanged instance writes nothing — the file is asserted " +
                                "untouched — yet still costs %.2f ms and allocates %,d B, %.0f%% of a " +
                                "real update. Only the write is elided, not the read: this store uses " +
                                "MultiProcessDataStoreFactory, which re-reads the current value from " +
                                "disk under its file lock — a full decode, and a decryption too where " +
                                "the implementation encrypts — before running the transform. Budget " +
                                "every updateData at a full read plus a write, whether or not the data " +
                                "changed.",
                        noOp.timing.medianNs / 1_000_000.0,
                        noOp.memory.medianAllocatedBytes,
                        noOpShare * 100,
                    ),
                )
            }
        }

        val finding = PartialUpdateFinding(
            implementationId = target.id,
            implementationName = target.displayName,
            dataset = dataset.label,
            payloadJsonBytes = dataset.primaryJsonBytes,
            fileBytesBefore = before.size.toLong(),
            fileBytesAfter = after.size.toLong(),
            changedByteRatio = changedRatio,
            identicalPrefixBytes = identicalPrefix.toLong(),
            inodeChanged = inodeChanged,
            singleFieldUpdateMedianNs = singleField.timing.medianNs,
            fullReplaceMedianNs = fullReplace.timing.medianNs,
            noOpUpdateMedianNs = noOp.timing.medianNs,
            singleFieldAllocatedBytes = singleField.memory.medianAllocatedBytes,
            noOpUpdateAllocatedBytes = noOp.memory.medianAllocatedBytes,
            rewritesWholeFile = rewritesWholeFile,
            verdict = verdict,
        )
        BenchmarkResultStore.record(finding)
        Log.i(BENCHMARK_LOG_TAG, "[${target.id}] partial update / ${dataset.label}: $verdict")

        factory.discard(store)
    }

    // ---- plumbing -------------------------------------------------------------------------------

    private fun harnessFor(size: DatasetSize) = BenchmarkHarness(BenchmarkConfig.forDataset(size))

    /** Writes [dataset] into a fresh file and releases the store, leaving the file on disk. */
    private fun populatedFile(dataset: BenchmarkDataset, tag: String): File {
        val store = openFresh(tag)
        runBlocking { store.dataStore.updateData { dataset.primary } }
        factory.close(store)
        return store.file
    }

    private fun inodeOf(file: File): Long =
        runCatching { Os.stat(file.absolutePath).st_ino }.getOrDefault(-1L)

    private fun record(
        scenario: BenchmarkScenario,
        dataset: BenchmarkDataset,
        measured: MeasuredScenario?,
        diskBytes: Long = -1L,
        notes: String? = null,
    ) {
        val record = BenchmarkRecord(
            implementationId = target.id,
            implementationName = target.displayName,
            implementationDescription = target.description,
            isReference = target.isReference,
            scenario = scenario.name,
            scenarioLabel = scenario.label,
            scenarioGroup = scenario.group.name,
            dataset = dataset.label,
            payloadJsonBytes = dataset.primaryJsonBytes,
            diskBytes = diskBytes,
            iterations = measured?.timing?.iterations ?: 0,
            minNs = measured?.timing?.minNs ?: 0L,
            medianNs = measured?.timing?.medianNs ?: 0L,
            meanNs = measured?.timing?.meanNs ?: 0.0,
            p90Ns = measured?.timing?.p90Ns ?: 0L,
            maxNs = measured?.timing?.maxNs ?: 0L,
            stdDevNs = measured?.timing?.stdDevNs ?: 0.0,
            memoryIterations = measured?.memory?.iterations ?: 0,
            medianAllocatedBytes = measured?.memory?.medianAllocatedBytes ?: 0L,
            maxAllocatedBytes = measured?.memory?.maxAllocatedBytes ?: 0L,
            retainedHeapDeltaBytes = measured?.memory?.retainedHeapDeltaBytes ?: 0L,
            pssDeltaKb = measured?.memory?.pssDeltaKb ?: 0L,
            gcCount = measured?.memory?.gcCount ?: 0L,
            notes = notes,
        )
        BenchmarkResultStore.record(record)
        Log.i(
            BENCHMARK_LOG_TAG,
            "[${target.id}] ${scenario.label} / ${dataset.label}: " +
                    "median=${record.medianNs / 1_000.0}µs alloc=${record.medianAllocatedBytes}B " +
                    "disk=${record.diskBytes}B",
        )
    }

    private fun Long.withSign(): String = if (this > 0) "+$this" else toString()

    companion object {
        /** Below this share of a real update, a no-op counts as having been optimized away. */
        private const val NO_OP_IS_FREE_THRESHOLD = 0.15

        /** Runs for every subclass — JUnit collects static @AfterClass methods up the hierarchy. */
        @JvmStatic
        @AfterClass
        fun logReportLocation() {
            BenchmarkResultStore.logLocation()
        }
    }
}
