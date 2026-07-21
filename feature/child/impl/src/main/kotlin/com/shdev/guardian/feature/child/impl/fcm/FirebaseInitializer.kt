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

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.shdev.guardian.data.firebase.FirebaseStateStore

/**
 * Owns Firebase's initialization lifecycle now that `FirebaseInitProvider` is removed from the merged
 * manifest. Nothing initializes Firebase implicitly any more — every entry point is here.
 *
 * Two entry points, matching the two ways Firebase legitimately comes up:
 *
 *  - [initializeForPairing] — called immediately before the QR pairing screen is presented. Pairing
 *    needs an FCM token to send to the backend, and that token cannot exist until Firebase is up, so
 *    this is the latest possible moment to initialize on a first run.
 *  - [initializeIfPaired] — called synchronously from `Application.onCreate` so an already-paired
 *    device has Firebase up before anything asks for it, on every subsequent cold start.
 *
 * Both are idempotent and safe to call repeatedly.
 */
class FirebaseInitializer(
    private val context: Context,
    private val state: FirebaseStateStore,
) {

    /** Whether Firebase is live in THIS process right now. */
    val isInitialized: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Cold-start hook. Initializes only if a previous session completed pairing, so an unpaired
     * install stays completely Firebase-free until the user actually starts pairing.
     *
     * Synchronous by design and cheap: one SharedPreferences boolean, then `initializeApp` only when
     * it is actually warranted.
     *
     * @return true if Firebase is up when this returns.
     */
    suspend fun initializeIfPaired(): Boolean {
        if (!state.isPaired()) {
            Log.d(TAG, "Cold start: not paired — leaving Firebase uninitialized")
            return false
        }
        return initialize()
    }

    /**
     * Pairing hook. Call right before the QR scan/generation screen is shown so the FCM token is
     * obtainable by the time the pairing request is built.
     *
     * @return true if Firebase is up when this returns.
     */
    suspend fun initializeForPairing(): Boolean {
        Log.d(TAG, "Pairing starting — ensuring Firebase is initialized")
        return initialize()
    }

    /**
     * Idempotency comes from [FirebaseApp.getApps], not from the persisted flag — the flag records
     * that this install initialized at some point in the past, which says nothing about the current
     * process.
     *
     * Auto-init for messaging stays off (it is also false in the manifest): a token must only be
     * generated and registered deliberately, through [FcmTokenSyncer], once pairing has succeeded.
     */
    private suspend fun initialize(): Boolean {
        if (isInitialized) return true

        val app = FirebaseApp.initializeApp(context)
        if (app == null) {
            // Returns null when google-services.json is missing or unparseable for this build.
            Log.w(TAG, "FirebaseApp.initializeApp returned null — check google-services.json")
            return false
        }

        FirebaseMessaging.getInstance().isAutoInitEnabled = false
        state.setHasInitialized(true)
        Log.d(TAG, "Firebase initialized programmatically")
        return true
    }

    private companion object {
        const val TAG = "FirebaseInit"
    }
}