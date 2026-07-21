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
package com.shdev.guardian.data.firebase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.dataStoreFile
import com.shdev.guardian.data.crypto.CryptoDataStore
import com.shdev.guardian.data.crypto.EncryptedJsonSerializer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

@Serializable
data class FirebaseData(
    val isPairingSuccessful: Boolean,
    val hasInitializedFirebase: Boolean,
    val pendingFcmToken: String?
) {
    companion object {
        val INIT = FirebaseData(
            isPairingSuccessful = false,
            hasInitializedFirebase = false,
            pendingFcmToken = null
        )
    }
}

/**
 * Firebase lifecycle flags
 *
 * Nothing here is a secret: two booleans and an FCM registration token, none of which grant access to
 * anything on their own. The device auth tokens stay in the encrypted [com.shdev.guardian.data.auth.TokenStore].
 *
 * [isPaired] is a plaintext MIRROR of `TokenStore.isPaired()`, not the source of truth. It
 * exists only so startup can branch without touching the Keystore (which is locked before first
 * unlock anyway). Anything making a security decision must consult TokenStore.
 */
class FirebaseStateStore(context: Context, crypto: CryptoDataStore) {
    private val dataStore by lazy {
        firebaseDataStore(context.applicationContext, crypto)
    }

    /**
     * True once the child device has redeemed a pairing code and persisted its token pair. Gates both
     * cold-start Firebase initialization and FCM token upload.
     */
    suspend fun isPaired(): Boolean = dataStore.data.firstOrNull()?.isPairingSuccessful ?: false
    suspend fun setIsPaired(isPaired: Boolean) {
        dataStore.updateData { it.copy(isPairingSuccessful = isPaired) }
    }

    /**
     * Whether this install has ever initialized Firebase. Diagnostic only — [FirebaseInitializer]
     * decides idempotency from `FirebaseApp.getApps()`, which reflects the CURRENT process rather than
     * a persisted flag. A persisted flag cannot answer "is Firebase up right now", so never gate
     * initialization on this.
     */
    suspend fun hasInitialized(): Boolean =
        dataStore.data.firstOrNull()?.hasInitializedFirebase ?: false

    suspend fun setHasInitialized(hasInitialized: Boolean) {
        dataStore.updateData { it.copy(hasInitializedFirebase = hasInitialized) }
    }


    /**
     * An FCM token that arrived before pairing completed, held until it can be registered. Cleared
     * once uploaded. Only ever one — tokens supersede each other, so the newest wins.
     */
    suspend fun getPendingFcmToken(): String? = dataStore.data.firstOrNull()?.pendingFcmToken
    suspend fun setPendingFcmToken(token: String?) {
        dataStore.updateData { it.copy(pendingFcmToken = token) }
    }

    /** Wipes every flag. Called when the device is unpaired/reset so a reinstall-like state remains. */
    suspend fun clear() = dataStore.updateData { FirebaseData.INIT }
}

private const val FCM_STORE_NAME = "guardian_fcm"
private val firebaseStoreLock = Any()

@Volatile
private var firebaseStoreInstance: DataStore<FirebaseData>? = null

private fun firebaseDataStore(
    appContext: Context,
    crypto: CryptoDataStore
): DataStore<FirebaseData> =
    firebaseStoreInstance ?: synchronized(firebaseStoreLock) {
        firebaseStoreInstance ?: run {
            val protectedContext = appContext.createDeviceProtectedStorageContext()
            MultiProcessDataStoreFactory.create(
                serializer = EncryptedJsonSerializer(
                    crypto,
                    FirebaseData.serializer(),
                    FirebaseData.INIT
                ),
                produceFile = { protectedContext.dataStoreFile(FCM_STORE_NAME) },
            ).also { firebaseStoreInstance = it }
        }
    }