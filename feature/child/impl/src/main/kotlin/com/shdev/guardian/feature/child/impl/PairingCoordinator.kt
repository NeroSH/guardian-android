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
package com.shdev.guardian.feature.child.impl

import com.shdev.guardian.data.auth.PairingRepository
import com.shdev.guardian.feature.child.impl.fcm.FcmTokenSyncer
import com.shdev.guardian.feature.child.impl.fcm.FirebaseInitializer

/**
 * Onboarding entry point for pairing, and the owner of Firebase's first-run initialization.
 *
 * Ordering matters and is the whole point of this class:
 *  1. [prepareForPairing] initializes Firebase before the QR screen is shown. Without it there is no
 *     FirebaseApp, so no FCM token can be minted.
 *  2. [pairWithScannedCode] fetches that token and redeems the scanned code in one request, so the
 *     backend can address this device for pokes from the moment it is registered.
 *  3. On success the token is re-registered through [FcmTokenSyncer], which covers the case where the
 *     token was not yet available at step 2 (a fresh install often mints one asynchronously).
 */
class PairingCoordinator(
    private val pairingRepository: PairingRepository,
    private val firebaseInitializer: FirebaseInitializer,
    private val fcmTokenSyncer: FcmTokenSyncer,
) {

    /**
     * Call immediately before presenting the QR pairing screen. Idempotent, so re-entering the screen
     * (rotation, back-and-forward) costs nothing.
     */
    suspend fun prepareForPairing(): Boolean = firebaseInitializer.initializeForPairing()

    suspend fun pairWithScannedCode(code: String): PairingRepository.Result {
        // Defensive: the screen calls prepareForPairing() first, but a deep link or a restored process
        // could reach here without it, and pairing without a token would leave the device unpokeable.
        if (!firebaseInitializer.isInitialized) firebaseInitializer.initializeForPairing()

        val fcmToken = runCatching { fcmTokenSyncer.currentToken() }.getOrNull()
        val result = pairingRepository.pair(code, fcmToken)

        if (result is PairingRepository.Result.Success) {
            // Registers a token parked by onNewToken during pairing, or requests one if the pair
            // request went out with fcmToken = null. Failure is non-fatal — pairing already succeeded,
            // and the token stays parked for the next attempt — so pokes just start late.
            runCatching { fcmTokenSyncer.flushPendingToken() }
        }
        return result
    }
}
