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
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.shdev.guardian.data.tests.datastore.models.defaultMultiProcessStore
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkJson
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The seam between the benchmark suite and the encryption implementation it measures.
 *
 * Everything else in this package — the datasets, the CRUD scenarios, the memory probes, the HTML
 * report — is written against this interface only. Adding a candidate algorithm to the comparison is
 * therefore two files: an implementation of this interface, and a three-line subclass of
 * [AbstractEncryptedDataStoreBenchmarkTest] that points at it. See
 * `new_encryption_test/README.md`.
 *
 * Implementations must be stateless enough to be constructed once per test class and used
 * concurrently by nothing — the suite drives them from a single thread.
 */
interface EncryptedDataStoreBenchmark {

    /** Stable key used to match rows across runs in the persisted report. Keep it forever. */
    val id: String

    /** Column heading in the report. */
    val displayName: String

    /** One line under the heading: algorithm, key source, encoding, serializer. */
    val description: String

    /**
     * Marks the column every other column is compared against. Exactly one implementation in a
     * report should set this; if none does, the first column alphabetically becomes the reference.
     */
    val isReference: Boolean get() = false

    /** The Json instance the implementation serializes with — measured by the JSON-only scenarios. */
    val json: Json get() = BenchmarkJson

    /** The serializer under test, including whatever encryption it applies. */
    fun serializer(context: Context, defaultValue: BenchmarkPayload): Serializer<BenchmarkPayload>

    /**
     * Opens a store over exactly [file]. Overriding this is only necessary for an implementation
     * that does not go through a DataStore `Serializer` (an encrypted `StorageConnection`, say);
     * the default mirrors production, which uses [androidx.datastore.core.MultiProcessDataStoreFactory].
     */
    fun createStore(
        context: Context,
        file: File,
        defaultValue: BenchmarkPayload,
        scope: CoroutineScope,
    ): DataStore<BenchmarkPayload> = defaultMultiProcessStore(context, file, defaultValue, scope)

    /**
     * The raw encrypt/decrypt primitives, isolated from DataStore and JSON. `null` for an
     * implementation that does not encrypt (the plaintext control), which simply omits those rows.
     */
    fun rawCodec(context: Context): RawCodec?

    /** Encrypt/decrypt with no serialization or I/O attached. */
    interface RawCodec {
        fun encrypt(plain: String): ByteArray
        fun decrypt(bytes: ByteArray): String?
    }
}
