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

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shdev.guardian.feature.child.impl.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * FCM data messages are a POKE, not a payload. On "limits changed / pause now / grant bonus", we
 * trigger an authoritative fetch over TLS — never trust the message body as policy (FCM is
 * best-effort, reorderable, replayable, and size-limited). The fetch is the single source of truth.
 *
 * Token handling is gated: this service can be reached as soon as Firebase initializes, which now
 * happens at the START of pairing, so a token can legitimately arrive while the device is still
 * unpaired. Uploading then would be both pointless (no session to authenticate the call) and wrong
 * (registering a device the backend has no record of), so [FcmTokenSyncer] parks it and replays it
 * once pairing completes.
 */
class GuardianFcmService : FirebaseMessagingService(), KoinComponent {

    private val fcmTokenSyncer: FcmTokenSyncer by inject()

    // Service-scoped rather than a bare CoroutineScope per call, so work is cancelled with the service
    // instead of leaking past onDestroy.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "onMessageReceived: ${getStringFromRemoteMessageIntent(message.toIntent())}")
        when (message.data["type"]) {
            "policy_stale",
            "pause_now",
            "grant_bonus" -> SyncWorker.poke(applicationContext)
        }
    }

    /**
     * Fires on first token generation and on every rotation. The syncer decides whether it may leave
     * the device — an unpaired device parks it instead of transmitting.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            val uploaded = fcmTokenSyncer.onTokenReceived(token)
            Log.d(
                TAG, if (uploaded) "FCM token registered"
                else "FCM token parked (not yet paired)"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun getStringFromRemoteMessageIntent(intent: Intent) = (intent.extras?.keySet()
        ?.map {
            if (it == "data") "\n\t\"$it\": ${intent.extras?.get(it)}"
            else "\n\t\"$it\": \"${intent.extras?.get(it)}\""
        } ?: emptyList())
        .joinToString(prefix = "\n{", postfix = "\n}") { it }

    private companion object {
        const val TAG = "Firebase"
    }
}
