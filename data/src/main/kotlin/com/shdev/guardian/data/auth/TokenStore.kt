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
package com.shdev.guardian.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.dataStoreFile
import com.shdev.guardian.data.crypto.CryptoDataStore
import com.shdev.guardian.data.crypto.EncryptedJsonSerializer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/** Device credentials persisted as one encrypted JSON document. */
@Serializable
data class TokenData(
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val serverPublicKey: String? = null,
)

/**
 * Device credentials at rest in a typed, multi-process DataStore whose serializer encrypts the whole
 * document via [CryptoDataStore]. A raw-disk read cannot recover the tokens. Holds the device id + the
 * rotating access/refresh pair obtained at pairing; the Ktor Auth plugin reads/refreshes through here.
 * Fully suspending — callers observe/await on the DataStore instead of blocking a thread.
 */
class TokenStore(context: Context, crypto: CryptoDataStore) {

    private val dataStore = tokenDataStore(context.applicationContext, crypto)

    suspend fun save(deviceId: String, access: String, refresh: String, serverPublicKey: String) {
        dataStore.updateData {
            it.copy(
                deviceId = deviceId,
                accessToken = access,
                refreshToken = refresh,
                serverPublicKey = serverPublicKey, // pinned at pairing (TOFU)
            )
        }
    }

    /** Update only the token pair after a refresh rotation; keeps the device id. */
    suspend fun updateTokens(access: String, refresh: String) {
        dataStore.updateData { it.copy(accessToken = access, refreshToken = refresh) }
    }

    suspend fun accessToken(): String? = read().accessToken
    suspend fun refreshToken(): String? = read().refreshToken
    suspend fun deviceId(): String? = read().deviceId
    suspend fun serverPublicKey(): String? = read().serverPublicKey
    suspend fun isPaired(): Boolean = accessToken() != null

    suspend fun clear() {
        dataStore.updateData { TokenData() }
    }

    private suspend fun read(): TokenData = dataStore.data.first()
}

private const val TOKEN_STORE_NAME = "guardian_secure_tokens.pb"
private val tokenStoreLock = Any()

@Volatile
private var tokenStoreInstance: DataStore<TokenData>? = null

/** Process-wide singleton — two DataStores over one file would crash. */
private fun tokenDataStore(appContext: Context, crypto: CryptoDataStore): DataStore<TokenData> =
    tokenStoreInstance ?: synchronized(tokenStoreLock) {
        tokenStoreInstance ?: MultiProcessDataStoreFactory.create(
            serializer = EncryptedJsonSerializer(crypto, TokenData.serializer(), TokenData()),
            produceFile = { appContext.dataStoreFile(TOKEN_STORE_NAME) },
        ).also { tokenStoreInstance = it }
    }
