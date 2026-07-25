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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shdev.guardian.data.tests.datastore.models.BenchmarkStoreFactory
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkDataSets
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload
import com.shdev.guardian.data.tests.datastore.schema.DatasetSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the benchmark against measuring the wrong thing.
 *
 * [com.shdev.guardian.data.crypto.EncryptedJsonSerializer] fails soft by design: an unreadable blob
 * yields the default value instead of throwing. That is right for production and dangerous for a
 * benchmark — a silently broken decrypt would still let every timing test pass, while reporting the
 * cost of returning a default instead of the cost of decrypting a megabyte. These tests assert the
 * data actually survives the round trip before any number in the report is believed.
 */
@RunWith(AndroidJUnit4::class)
class CryptoDataStoreCorrectnessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val target = CryptoDataStoreTarget()
    private lateinit var factory: BenchmarkStoreFactory

    /** Never equal to a dataset, so a "read" that silently returns the default is detectable. */
    private val absentDefault = BenchmarkPayload(schemaVersion = Int.MIN_VALUE)

    @Before
    fun setUp() {
        factory = BenchmarkStoreFactory(context, target)
    }

    @After
    fun tearDown() {
        factory.cleanUp()
    }

    @Test
    fun everyDatasetSurvivesAWriteCloseReopenCycle() {
        DatasetSize.entries.forEach { size ->
            val dataset = BenchmarkDataSets.of(size)
            val writer = factory.openFresh("roundtrip_${size.name}", absentDefault)
            runBlocking { writer.dataStore.updateData { dataset.primary } }
            factory.close(writer)

            val reader = factory.open(
                factory.copyOf(writer.file, "roundtrip_read_${size.name}"),
                absentDefault
            )
            assertEquals(
                "${size.label} dataset did not survive the encrypted round trip",
                dataset.primary,
                runBlocking { reader.dataStore.data.first() },
            )
            factory.discard(reader)
        }
    }

    @Test
    fun documentIsUnreadableOnDisk() {
        val dataset = BenchmarkDataSets.of(DatasetSize.MEDIUM)
        val store = factory.openFresh("opaque", absentDefault)
        runBlocking { store.dataStore.updateData { dataset.primary } }
        factory.close(store)

        val onDisk = store.file.readBytes()
        val asText = String(onDisk, Charsets.UTF_8)
        assertFalse("Field names are readable on disk", asText.contains("profileId"))
        assertFalse("Field values are readable on disk", asText.contains(dataset.primary.profileId))

        // ...and the same bytes do decrypt back to the document with the key.
        val plain = target.rawCodec(context).decrypt(onDisk)
        assertTrue("Stored blob did not decrypt", plain?.contains("profileId") == true)
    }

    @Test
    fun writingTheSameDocumentTwiceProducesDifferentCiphertext() {
        val dataset = BenchmarkDataSets.of(DatasetSize.MEDIUM)
        val first = factory.openFresh("iv_a", absentDefault)
        val second = factory.openFresh("iv_b", absentDefault)
        runBlocking {
            first.dataStore.updateData { dataset.primary }
            second.dataStore.updateData { dataset.primary }
        }
        factory.close(first)
        factory.close(second)

        assertNotEquals(
            "Identical documents encrypted to identical bytes — the IV is not being randomized",
            String(first.file.readBytes(), Charsets.UTF_8),
            String(second.file.readBytes(), Charsets.UTF_8),
        )
    }

    @Test
    fun emptyFileReadsAsTheDefaultValue() {
        val file = factory.uniqueFile("empty_file")
        file.createNewFile()
        val store = factory.open(file, absentDefault)
        assertEquals(absentDefault, runBlocking { store.dataStore.data.first() })
        factory.discard(store)
    }

    @Test
    fun corruptedFileReadsAsTheDefaultValueInsteadOfThrowing() {
        val dataset = BenchmarkDataSets.of(DatasetSize.MEDIUM)
        val writer = factory.openFresh("corrupt", absentDefault)
        runBlocking { writer.dataStore.updateData { dataset.primary } }
        factory.close(writer)

        val corrupted = factory.copyOf(writer.file, "corrupt_copy")
        corrupted.writeBytes(corrupted.readBytes().also { bytes ->
            // Damage the authentication tag region; GCM must reject the whole blob.
            for (index in bytes.size - 32 until bytes.size) bytes[index] = 'A'.code.toByte()
        })

        val reader = factory.open(corrupted, absentDefault)
        assertEquals(
            "A corrupted blob should fall back to the default value, not surface as data",
            absentDefault,
            runBlocking { reader.dataStore.data.first() },
        )
        factory.discard(reader)
    }
}
