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
package com.shdev.guardian.feature.child.impl.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.shdev.guardian.data.auth.DeviceApi
import com.shdev.guardian.data.auth.TokenStore
import com.shdev.guardian.data.firebase.FirebaseStateStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Single gate for FCM registration tokens leaving the device.
 *
 * A token may arrive (via `onNewToken`) before pairing finishes — Firebase comes up at the start of
 * the pairing flow, so there is a real window where a token exists but the device has no session.
 * Uploading then would fail anyway: `DeviceApi` rides the authenticated client, and an unpaired
 * device has no bearer token. Instead the token is parked in [com.shdev.guardian.data.firebase.FirebaseStateStore.getPendingFcmToken] and
 * replayed by [flushPendingToken] the moment pairing succeeds.
 *
 * Two independent conditions are checked before any upload, and they are not redundant:
 *  - [com.shdev.guardian.data.firebase.FirebaseStateStore.isPaired] — the fast plaintext mirror, readable anywhere.
 *  - [com.shdev.guardian.data.auth.TokenStore.isPaired] — the authoritative encrypted state. This is the one that decides.
 * The mirror can be stale (cleared data, restored backup); the encrypted store cannot.
 */
class FcmTokenSyncer(
    private val state: FirebaseStateStore,
    private val tokenStore: TokenStore,
    private val deviceApi: DeviceApi,
) {

    /**
     * Handle a token from `onNewToken`. Uploads it if the device is paired, otherwise parks it.
     *
     * @return true if the token reached the backend.
     */
    suspend fun onTokenReceived(token: String): Boolean {
        if (!state.isPaired() || !tokenStore.isPaired()) {
            Log.d(TAG, "Not paired — parking FCM token until pairing completes")
            state.setPendingFcmToken(token)
            return false
        }
        return upload(token)
    }

    /**
     * Called immediately after pairing succeeds. Registers whichever token we have — the parked one if
     * `onNewToken` already fired, otherwise a freshly requested one — so pokes start landing without
     * waiting for the next token rotation (which may be weeks away, or never).
     *
     * @return true if a token reached the backend.
     */
    suspend fun flushPendingToken(): Boolean {
        if (!tokenStore.isPaired()) {
            Log.w(TAG, "flushPendingToken called while unpaired — ignoring")
            return false
        }
        val token = state.getPendingFcmToken() ?: runCatching { currentToken() }.getOrNull()
        if (token == null) {
            Log.w(TAG, "No FCM token available to register")
            return false
        }
        return upload(token)
    }

    /**
     * Current registration token, requesting one if this install has none yet. Requires Firebase to be
     * initialized; callers on the pairing path run after [com.shdev.guardian.feature.child.impl.fcm.FirebaseInitializer.initializeForPairing].
     */
    suspend fun currentToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private suspend fun upload(token: String): Boolean =
        runCatching { deviceApi.updateFcmToken(token) }.fold(
            onSuccess = {
                // Only drop the parked copy once the backend has actually taken it, so a failed
                // upload is retried on the next trigger instead of being silently lost.
                state.setPendingFcmToken(null)
                Log.d(TAG, "FCM token registered with backend")
                true
            },
            onFailure = {
                Log.w(TAG, "FCM token upload failed — keeping it parked for retry", it)
                state.setPendingFcmToken(token)
                false
            },
        )

    private companion object {
        const val TAG = "FcmTokenSync"
    }
}