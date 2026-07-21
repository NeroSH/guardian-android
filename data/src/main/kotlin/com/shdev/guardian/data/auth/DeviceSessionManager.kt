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
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.shdev.guardian.data.firebase.FirebaseStateStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Owns child-device session teardown, mirroring [ParentSessionManager] on the parent side.
 *
 * Tearing the device session down means more than dropping the auth tokens. Three pieces of state
 * describe "this device is paired", and leaving any of them behind produces a device that is
 * half-unpaired:
 *
 *  - [TokenStore] — the encrypted auth tokens, the authoritative pairing state.
 *  - [FirebaseStateStore] — the plaintext mirror that drives cold-start Firebase initialization.
 *    Left stale, the next launch initializes Firebase for a device that is no longer paired, and the
 *    mirror starts lying about the encrypted state it is supposed to reflect.
 *  - The FCM registration token — the backend may still hold it, so an unpaired device would stay
 *    addressable for pokes. Revoking it locally forces a fresh token to be minted on re-pairing.
 */
class DeviceSessionManager(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val firebaseState: FirebaseStateStore,
) {

    /**
     * Called when the device session ends — currently a 401 on an authenticated device call that
     * survived the Auth plugin's refresh attempt, meaning the backend has revoked or forgotten this
     * device.
     *
     * Order matters: the FCM token is revoked FIRST, while [FirebaseStateStore.hasInitialized] still
     * records that Firebase has been up on this install. Clearing that flag first would make the
     * revoke look unnecessary and skip it.
     */
    suspend fun onDeviceUnpaired() {
        revokeFcmToken()
        tokenStore.clear()
        firebaseState.clear()
        Log.d(TAG, "Device session cleared: tokens, Firebase state and FCM token revoked")
    }

    /**
     * Deletes this install's FCM registration token so the device stops being addressable.
     *
     * [FirebaseStateStore.hasInitialized] is the precondition that makes this safe to attempt: if
     * Firebase has never been initialized on this install then no token was ever minted, and there is
     * nothing to revoke. The second check covers the current process specifically — the persisted flag
     * says Firebase came up at some point in the past, which is not the same as it being up right now,
     * and `FirebaseMessaging.getInstance()` throws when it is not.
     *
     * Failure is non-fatal. The local session is being destroyed either way; a token the backend still
     * holds simply points at a device that will reject the poke.
     */
    private suspend fun revokeFcmToken() {
        if (!firebaseState.hasInitialized()) {
            Log.d(TAG, "Firebase never initialized on this install — no FCM token to revoke")
            return
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.d(TAG, "Firebase not up in this process — skipping FCM token revoke")
            return
        }
        runCatching { deleteFcmToken() }
            .onFailure { Log.w(TAG, "FCM token revoke failed — continuing with teardown", it) }
    }

    private suspend fun deleteFcmToken(): Unit = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().deleteToken()
            .addOnCompleteListener { cont.resume(Unit) }
    }

    private companion object {
        const val TAG = "DeviceSession"
    }
}
