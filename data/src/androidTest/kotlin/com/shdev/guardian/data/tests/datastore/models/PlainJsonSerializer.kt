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
package com.shdev.guardian.data.tests.datastore.models

import androidx.datastore.core.Serializer
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * The control group: [com.shdev.guardian.data.crypto.EncryptedJsonSerializer] with the encryption
 * removed and nothing else changed — same JSON, same stream handling, same `Dispatchers.IO` hop on
 * write, same fail-soft read.
 *
 * Its column in the report is the floor: the difference between it and an encrypting column is the
 * price of encryption, separated from the price of DataStore and kotlinx.serialization.
 */
class PlainJsonSerializer(
    override val defaultValue: BenchmarkPayload,
    private val json: Json,
) : Serializer<BenchmarkPayload> {

    private val kSerializer = BenchmarkPayload.serializer()

    override suspend fun readFrom(input: InputStream): BenchmarkPayload {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return runCatching {
            json.decodeFromString(kSerializer, String(bytes, Charsets.UTF_8))
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: BenchmarkPayload, output: OutputStream) {
        val plain = json.encodeToString(kSerializer, t)
        withContext(Dispatchers.IO) {
            output.write(plain.toByteArray(Charsets.UTF_8))
        }
    }
}
