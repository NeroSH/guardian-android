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

import com.shdev.guardian.data.config.DeviceRole
import com.shdev.guardian.data.config.RoleStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Thrown by the parent client's response validator when a call is still 401 after a token refresh. */
class UnauthorizedException : Exception("401 Unauthorized — session expired")

/**
 * Owns the parent session's lifecycle: when it begins, and the only two ways it ends.
 *
 * A 401 is deliberately NOT one of those ways. The parent Ktor client's Auth plugin answers a 401 by
 * refreshing the token pair and retrying, so a routine access-token expiry never reaches here. The
 * session is only torn down when the refresh itself fails ([onRefreshFailed] — the refresh token is
 * missing, revoked or rejected) or when the parent explicitly logs out ([logout]). Both clear the
 * encrypted session document, reset the role so the next launch doesn't route into the parent flow,
 * and broadcast [sessionExpired] for the UI to bounce back to role selection.
 */
class ParentSessionManager(
    private val store: ParentSessionStore,
    private val roleStore: RoleStore,
) {
    // Buffered + DROP_OLDEST so an event fired while the UI isn't collecting still lands the latest.
    private val _sessionExpired = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits once each time the session is torn down — refresh failure or explicit logout. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /**
     * Persist a freshly minted session and pin the device role to PARENT, so a cold start routes
     * straight to the parent dashboard instead of role selection.
     *
     * @param email the address the SERVER resolved this session to. For the verified-email flow this
     * must come from the server's response, never from a client-side SD-JWT parse.
     */
    suspend fun onAuthenticated(access: String, refresh: String, email: String? = null) {
        store.save(access, refresh, email)
        roleStore.setRole(DeviceRole.PARENT)
    }

    /**
     * The Auth plugin tried to refresh and could not. The refresh token is unusable, so this is a
     * genuine end-of-session — unlike the 401 that triggered the attempt.
     */
    suspend fun onRefreshFailed() = clearAndNotify()

    /** Explicit user-initiated logout from the account settings dialog. */
    suspend fun logout() = clearAndNotify()

    private suspend fun clearAndNotify() {
        store.clear()
        roleStore.setRole(DeviceRole.NONE)
        _sessionExpired.tryEmit(Unit)
    }
}
