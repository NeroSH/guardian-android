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
package com.shdev.guardian.data.tests.datastore.crypto_test

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.datastore.core.Serializer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shdev.guardian.data.tests.datastore.EncryptedDataStoreBenchmark
import com.shdev.guardian.data.tests.datastore.models.BenchmarkStoreFactory
import com.shdev.guardian.data.tests.datastore.models.ManagedBenchmarkStore
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkDataSets
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload
import com.shdev.guardian.data.tests.datastore.schema.DatasetSize
import com.shdev.guardian.data.tests.datastore.schema.withNextRevision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The same operations as the main sweep, measured by [BenchmarkRule] instead of the suite's own
 * harness.
 *
 * The two exist for different questions. The harness answers "what does this cost in wall-clock and
 * in bytes allocated, side by side with the other implementations" and feeds `report.html`.
 * BenchmarkRule answers "what is the stable per-operation time on this device": it warms up until
 * the measurement converges, discards outliers, and reports through the standard AndroidX benchmark
 * output that CI and Android Studio already understand. Its results do not enter the HTML report.
 *
 * Note the whole module is run under `AndroidBenchmarkRunner` with the debuggable/emulator errors
 * suppressed (see `data/build.gradle.kts`). On a debuggable build the absolute numbers are inflated;
 * for anything quotable, run on a physical device with a non-debuggable build.
 */
@RunWith(AndroidJUnit4::class)
class CryptoDataStoreMicrobenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val target = CryptoDataStoreTarget()
    private lateinit var codec: EncryptedDataStoreBenchmark.RawCodec
    private lateinit var serializer: Serializer<BenchmarkPayload>
    private lateinit var factory: BenchmarkStoreFactory
    private lateinit var store: ManagedBenchmarkStore

    @Volatile
    private var sink: Any? = null

    @Before
    fun setUp() {
        codec = target.rawCodec(context)
        serializer = target.serializer(context, BenchmarkPayload(schemaVersion = Int.MIN_VALUE))
        factory = BenchmarkStoreFactory(context, target)
        store = factory.openFresh("micro", BenchmarkPayload(schemaVersion = Int.MIN_VALUE))
        runBlocking { store.dataStore.updateData { BenchmarkDataSets.of(DatasetSize.MEDIUM).primary } }
    }

    @After
    fun tearDown() {
        factory.cleanUp()
    }

    // ---- codec only -----------------------------------------------------------------------------

    @Test
    fun encrypt_empty() = benchmarkEncrypt(DatasetSize.EMPTY)

    @Test
    fun encrypt_medium() = benchmarkEncrypt(DatasetSize.MEDIUM)

    @Test
    fun encrypt_large() = benchmarkEncrypt(DatasetSize.LARGE)

    @Test
    fun decrypt_empty() = benchmarkDecrypt(DatasetSize.EMPTY)

    @Test
    fun decrypt_medium() = benchmarkDecrypt(DatasetSize.MEDIUM)

    @Test
    fun decrypt_large() = benchmarkDecrypt(DatasetSize.LARGE)

    // ---- serializer, no file I/O ------------------------------------------------------------------

    @Test
    fun serializerWrite_medium() = benchmarkSerializerWrite(DatasetSize.MEDIUM)

    @Test
    fun serializerWrite_large() = benchmarkSerializerWrite(DatasetSize.LARGE)

    @Test
    fun serializerRead_medium() = benchmarkSerializerRead(DatasetSize.MEDIUM)

    @Test
    fun serializerRead_large() = benchmarkSerializerRead(DatasetSize.LARGE)

    // ---- through DataStore, including the atomic file write ---------------------------------------

    @Test
    fun dataStoreSingleFieldUpdate_medium() {
        benchmarkRule.measureRepeated {
            runBlocking { store.dataStore.updateData { it.withNextRevision() } }
        }
    }

    private fun benchmarkEncrypt(size: DatasetSize) {
        val plain = BenchmarkDataSets.of(size).primaryJson
        benchmarkRule.measureRepeated { sink = codec.encrypt(plain) }
    }

    private fun benchmarkDecrypt(size: DatasetSize) {
        val encrypted = codec.encrypt(BenchmarkDataSets.of(size).primaryJson)
        benchmarkRule.measureRepeated { sink = codec.decrypt(encrypted) }
    }

    private fun benchmarkSerializerWrite(size: DatasetSize) {
        val payload = BenchmarkDataSets.of(size).primary
        benchmarkRule.measureRepeated {
            // The stream is allocated outside the measurement: what is being timed is
            // serialize + encrypt, not ByteArrayOutputStream growth.
            val output = runWithMeasurementDisabled {
                ByteArrayOutputStream(BenchmarkDataSets.of(size).primaryJsonBytes.toInt() * 2)
            }
            runBlocking { serializer.writeTo(payload, output) }
            sink = output
        }
    }

    private fun benchmarkSerializerRead(size: DatasetSize) {
        val payload = BenchmarkDataSets.of(size).primary
        val encrypted = ByteArrayOutputStream().also { output ->
            runBlocking { serializer.writeTo(payload, output) }
        }.toByteArray()
        benchmarkRule.measureRepeated {
            val input = runWithMeasurementDisabled { ByteArrayInputStream(encrypted) }
            sink = runBlocking { serializer.readFrom(input) }
        }
    }
}
