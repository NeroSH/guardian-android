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

import android.content.Context
import androidx.datastore.core.Serializer
import com.shdev.guardian.data.crypto.CryptoDataStore
import com.shdev.guardian.data.crypto.CryptoDataStoreImpl
import com.shdev.guardian.data.crypto.EncryptedJsonSerializer
import com.shdev.guardian.data.tests.datastore.EncryptedDataStoreBenchmark
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload

/**
 * The implementation the app ships today: [CryptoDataStoreImpl] (AES-256-GCM, key held in the
 * AndroidKeyStore and in StrongBox where the device has it) behind [EncryptedJsonSerializer].
 *
 * This is the reference column — every candidate algorithm is reported as a delta against it.
 *
 * One [CryptoDataStore] instance is kept for the lifetime of the target, matching production, where
 * Koin provides it as a singleton. That matters for the numbers: the instance lazily caches the
 * KeyStore handle and the `SecureRandom`, so a per-call instance would charge every measurement for
 * a KeyStore load that the app pays once.
 */
class CryptoDataStoreTarget : EncryptedDataStoreBenchmark {

    override val id: String = "keystore_aes_gcm_json"

    override val displayName: String = "AndroidKeyStore AES-256-GCM"

    override val description: String =
        "CryptoDataStoreImpl + EncryptedJsonSerializer — kotlinx.serialization JSON, AES-256-GCM " +
                "with a random 12-byte IV per write, 128-bit auth tag, key in AndroidKeyStore " +
                "(StrongBox-backed when available), stored as Base64 of iv||ciphertext."

    override val isReference: Boolean = true

    @Volatile
    private var cachedCrypto: CryptoDataStore? = null

    override fun serializer(
        context: Context,
        defaultValue: BenchmarkPayload
    ): Serializer<BenchmarkPayload> =
        EncryptedJsonSerializer(
            crypto = crypto(context),
            kSerializer = BenchmarkPayload.serializer(),
            defaultValue = defaultValue,
            json = json,
        )

    override fun rawCodec(context: Context): EncryptedDataStoreBenchmark.RawCodec {
        val crypto = crypto(context)
        return object : EncryptedDataStoreBenchmark.RawCodec {
            override fun encrypt(plain: String): ByteArray = crypto.encrypt(plain)
            override fun decrypt(bytes: ByteArray): String? = crypto.decrypt(bytes)
        }
    }

    private fun crypto(context: Context): CryptoDataStore =
        cachedCrypto ?: synchronized(this) {
            cachedCrypto ?: CryptoDataStoreImpl(context.applicationContext).also {
                cachedCrypto = it
            }
        }
}
