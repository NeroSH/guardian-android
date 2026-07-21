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

import android.os.Build
import com.shdev.guardian.data.firebase.FirebaseStateStore

/**
 * Drives the child pairing exchange: scanned code -> device token pair -> persisted securely.
 * After this succeeds, [TokenStore.isPaired] is true and the Ktor Auth plugin can authenticate sync.
 */
class PairingRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val firebaseState: FirebaseStateStore,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun pair(scannedCode: String, fcmToken: String?): Result = runCatching {
        val resp = authApi.redeemPairingCode(
            RegisterDeviceRequest(
                pairingCode = scannedCode.trim(),
                model = Build.MODEL,
                oem = Build.MANUFACTURER,
                fcmToken = fcmToken,
            ),
        )
        tokenStore.save(resp.deviceId, resp.accessToken, resp.refreshToken, resp.serverPublicKey)
        // Set only AFTER the tokens are durably persisted — this flag is what lets the next cold start
        // initialize Firebase and what unblocks FCM token upload, so it must never run ahead of the
        // encrypted state it mirrors.
        firebaseState.setIsPaired(true)
        Result.Success
    }.getOrElse {
        Result.Failure(it.message ?: "Pairing failed")
    }
}
