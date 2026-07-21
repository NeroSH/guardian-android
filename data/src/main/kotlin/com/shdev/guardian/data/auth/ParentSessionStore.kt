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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

/**
 * Parent's authenticated session, persisted as one encrypted JSON document.
 *
 * [email] is the address the backend resolved the session to — for the verified-email flow that is
 * the server's answer after it validated the SD-JWT presentation, never a claim parsed on-device.
 */
@Serializable
data class ParentSessionData(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val email: String? = null,
)

/**
 * Parent's authenticated session, encrypted at rest via [CryptoDataStore] in a typed, multi-process
 * DataStore. Separate store from the child device's [TokenStore]. Fully suspending.
 *
 * The token pair outlives the process: an expired access token is NOT a logout — the parent Ktor
 * client rotates it through [updateTokens] via the refresh token. Only [clear] ends a session.
 */
class ParentSessionStore(context: Context, crypto: CryptoDataStore) {

    private val dataStore by lazy {
        parentSessionDataStore(context.applicationContext, crypto)
    }

    suspend fun save(access: String, refresh: String, email: String? = null) {
        dataStore.updateData {
            it.copy(accessToken = access, refreshToken = refresh, email = email ?: it.email)
        }
    }

    /** Rotate only the token pair after a refresh; keeps the email. */
    suspend fun updateTokens(access: String, refresh: String) {
        dataStore.updateData { it.copy(accessToken = access, refreshToken = refresh) }
    }

    suspend fun accessToken(): String? = dataStore.data.firstOrNull()?.accessToken
    suspend fun refreshToken(): String? = dataStore.data.firstOrNull()?.refreshToken
    suspend fun email(): String? = dataStore.data.firstOrNull()?.email
    suspend fun isLoggedIn(): Boolean = accessToken() != null

    suspend fun clear() {
        dataStore.updateData { ParentSessionData() }
    }
}

private const val PARENT_SESSION_STORE_NAME = "guardian_parent_session.pb"
private val parentSessionStoreLock = Any()

@Volatile
private var parentSessionStoreInstance: DataStore<ParentSessionData>? = null

/** Process-wide singleton — two DataStores over one file would crash. */
private fun parentSessionDataStore(
    appContext: Context,
    crypto: CryptoDataStore
): DataStore<ParentSessionData> =
    parentSessionStoreInstance ?: synchronized(parentSessionStoreLock) {
        parentSessionStoreInstance ?: MultiProcessDataStoreFactory.create(
            serializer = EncryptedJsonSerializer(
                crypto,
                ParentSessionData.serializer(),
                ParentSessionData()
            ),
            produceFile = { appContext.dataStoreFile(PARENT_SESSION_STORE_NAME) },
        ).also { parentSessionStoreInstance = it }
    }
