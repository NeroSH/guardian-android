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
package com.shdev.guardian.data.crypto

import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore [Serializer] that persists a data class as JSON encrypted at rest by [CryptoDataStore]
 * (AES-256-GCM, AndroidKeyStore/StrongBox-backed). The bytes on disk are `iv || ciphertext`; a raw
 * read recovers nothing.
 *
 * Fails soft: an empty file, an undecryptable blob (rotated key, corruption), or malformed JSON all
 * fall back to [defaultValue] rather than throwing — the caller then treats it as "no value" and
 * re-authenticates, instead of the DataStore crashing the read.
 *
 * @param crypto the encrypt/decrypt engine, injected so stores and serializers share one instance.
 */
class EncryptedJsonSerializer<T>(
    private val crypto: CryptoDataStore,
    private val kSerializer: KSerializer<T>,
    override val defaultValue: T,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        val plain = crypto.decrypt(bytes) ?: return defaultValue
        return runCatching { json.decodeFromString(kSerializer, plain) }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plain = json.encodeToString(kSerializer, t)
        withContext(Dispatchers.IO) {
            output.write(crypto.encrypt(plain))
        }
    }
}
