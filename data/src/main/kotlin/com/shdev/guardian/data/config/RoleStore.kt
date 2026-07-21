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
package com.shdev.guardian.data.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.dataStoreFile
import com.shdev.guardian.data.crypto.CryptoDataStore
import com.shdev.guardian.data.crypto.EncryptedJsonSerializer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceRole { NONE, PARENT, CHILD }

/** Role document persisted encrypted; a data class so the store never touches raw preference keys. */
@Serializable
data class RoleData(val role: DeviceRole = DeviceRole.NONE)

/**
 * Persists this install's role in a typed, multi-process DataStore encrypted via [CryptoDataStore],
 * on device-protected storage. Read by the boot/watchdog receivers so enforcement machinery only runs
 * on a child device — a parent device must never start the MonitorService.
 *
 * WARNING — direct boot: the file lives in device-protected storage (readable before first unlock),
 * but the value is Keystore-encrypted, and the Keystore is LOCKED until first unlock. A read at
 * LOCKED_BOOT_COMPLETED therefore decrypts to [DeviceRole.NONE]; enforcement only resurrects from
 * BOOT_COMPLETED (post-unlock). If pre-unlock resurrection is required, this store must be plaintext.
 */
class RoleStore(context: Context, crypto: CryptoDataStore) {

    private val dataStore = roleDataStore(context.applicationContext, crypto)

    suspend fun role(): DeviceRole = dataStore.data.firstOrNull()?.role ?: DeviceRole.NONE

    suspend fun setRole(value: DeviceRole) {
        dataStore.updateData { it.copy(role = value) }
    }

    suspend fun isChild(): Boolean = role() == DeviceRole.CHILD

    /**
     * True once a parent has authenticated on this install, and until they explicitly log out. Note
     * this says nothing about token validity — an expired access token is refreshed silently, so the
     * launch router pairs this with a [ParentSessionStore] token check rather than trusting it alone.
     */
    suspend fun isParent(): Boolean = role() == DeviceRole.PARENT
}

private const val ROLE_STORE_NAME = "guardian_role"
private val roleStoreLock = Any()

@Volatile
private var roleStoreInstance: DataStore<RoleData>? = null

/**
 * Single process-wide DataStore for the role, on device-protected storage. Must be a singleton per
 * file — the boot/watchdog receivers construct [RoleStore] directly (not via Koin), and two DataStores
 * over the same file would crash.
 */
private fun roleDataStore(appContext: Context, crypto: CryptoDataStore): DataStore<RoleData> =
    roleStoreInstance ?: synchronized(roleStoreLock) {
        roleStoreInstance ?: run {
            val protectedContext = appContext.createDeviceProtectedStorageContext()
            MultiProcessDataStoreFactory.create(
                serializer = EncryptedJsonSerializer(crypto, RoleData.serializer(), RoleData()),
                produceFile = { protectedContext.dataStoreFile(ROLE_STORE_NAME) },
            ).also { roleStoreInstance = it }
        }
    }
