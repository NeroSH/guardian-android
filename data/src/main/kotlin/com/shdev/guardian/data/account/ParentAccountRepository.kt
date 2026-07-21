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
package com.shdev.guardian.data.account

import com.shdev.guardian.data.auth.ParentDevicesApi
import com.shdev.guardian.data.auth.ParentSessionManager
import com.shdev.guardian.data.auth.ParentSessionStore

/**
 * Read model + logout for the signed-in parent's account, backing the settings dialog. Keeps the
 * presentation layer off raw APIs and datastores: the ViewModel asks for an email and a device
 * count, not for a token store and an HTTP client.
 */
class ParentAccountRepository(
    private val session: ParentSessionStore,
    private val devicesApi: ParentDevicesApi,
    private val sessionManager: ParentSessionManager,
) {
    /** The address the server resolved this session to, as persisted at authentication. */
    suspend fun email(): String? = session.email()

    /**
     * Number of child devices paired to this parent. Wrapped in [Result] because the call is a live
     * network read — it can 401-then-refresh, or fail outright — and the dialog renders the rest of
     * the account regardless.
     */
    suspend fun connectedDeviceCount(): Result<Int> = runCatching { devicesApi.list().size }

    /**
     * Clears the session and role, then broadcasts so the UI resets to role selection. This is the
     * only user-facing way to end a session — token expiry is handled by silent refresh.
     */
    suspend fun logout() = sessionManager.logout()
}
