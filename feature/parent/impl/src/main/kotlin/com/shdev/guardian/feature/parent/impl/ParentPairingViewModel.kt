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
package com.shdev.guardian.feature.parent.impl

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shdev.guardian.data.auth.ParentApi
import com.shdev.guardian.data.auth.ParentSessionManager
import com.shdev.guardian.data.auth.ParentSessionStore
import com.shdev.guardian.data.auth.SdJwtParser
import com.shdev.guardian.data.auth.TokenResponse
import com.shdev.guardian.data.auth.VerifiedEmailApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
sealed interface ParentPairingState {
    data object NeedsAuth : ParentPairingState
    data object Loading : ParentPairingState
    data class ShowCode(val code: String) : ParentPairingState
    data class Error(val message: String) : ParentPairingState
}

/**
 * Parent onboarding: authenticate (password, or OTP-less verified email), then mint a pairing code to
 * render as a QR. If a session already exists, skips straight to code generation.
 *
 * Authenticating pins the device role to PARENT via [ParentSessionManager], which is what makes the
 * next cold start route to the dashboard instead of role selection.
 */
class ParentPairingViewModel(
    private val _parentApi: Lazy<ParentApi>,
    private val session: ParentSessionStore,
    private val sessionManager: ParentSessionManager,
    private val _verifiedEmailApi: Lazy<VerifiedEmailApi>,
    private val _digitalCredentialClient: Lazy<DigitalCredentialClient>,
) : ViewModel() {

    private val parentApi: ParentApi
        get() = _parentApi.value

    private val verifiedEmailApi: VerifiedEmailApi
        get() = _verifiedEmailApi.value

    private val _state = MutableStateFlow<ParentPairingState>(ParentPairingState.Loading)
    val state: StateFlow<ParentPairingState> = _state.asStateFlow()

    // Fires once per successful sign-in so the host can promote the parent into the dashboard.
    private val _authenticated = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val authenticated: SharedFlow<Unit> = _authenticated.asSharedFlow()

    /** Display-only greeting from the verified credential; never used for a trust decision. */
    private val _verifiedName = MutableStateFlow<String?>(null)
    val verifiedName: StateFlow<String?> = _verifiedName.asStateFlow()

    val isVerifiedEmailSupported: Boolean get() = DigitalCredentialClient.isSupported

    init {
        viewModelScope.launch {
            if (session.isLoggedIn()) doGenerateCode()
            else _state.value = ParentPairingState.NeedsAuth
        }
    }

    fun authenticate(email: String, password: String, register: Boolean) {
        _state.value = ParentPairingState.Loading
        viewModelScope.launch {
            runCatching {
                if (register) parentApi.register(
                    email = email,
                    password = password
                ) else parentApi.login(
                    email = email,
                    password = password
                )
            }.onSuccess { tokens ->
                onSessionMinted(tokens = tokens, email = email)
            }.onFailure {
                _state.value = ParentPairingState.Error(it.message ?: "Authentication failed")
            }
        }
    }

    /**
     * OTP-less sign-in: fetch a server-issued nonce, obtain a verified email credential through
     * Credential Manager, and hand the untouched presentation to the backend to verify and exchange
     * for a session. Only reachable when [isVerifiedEmailSupported].
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun authenticateWithVerifiedEmail(activity: Activity) {
        _state.value = ParentPairingState.Loading
        viewModelScope.launch {
            runCatching {
                val nonce = verifiedEmailApi.challenge().nonce
                val responseJson =
                    _digitalCredentialClient.value.requestVerifiedEmail(
                        activity = activity,
                        nonce = nonce
                    )
                // Display only — the authoritative claims come back from the server below.
                _verifiedName.value = SdJwtParser.parseForDisplay(responseJson)?.displayName
                verifiedEmailApi.authenticate(responseJson = responseJson, nonce = nonce)
            }.onSuccess { tokens ->
                // Email comes from the server's response, never from the on-device parse.
                onSessionMinted(tokens = tokens, email = null)
            }.onFailure { error ->
                // Dismissing the sheet, or having no eligible account, is not an error — let the
                // parent fall back to the password form.
                _state.value = if (error is GetCredentialCancellationException) {
                    _verifiedName.value = null
                    ParentPairingState.NeedsAuth
                } else {
                    ParentPairingState.Error(error.message ?: "Verified email sign-in failed")
                }
            }
        }
    }

    fun generateCode() {
        viewModelScope.launch { doGenerateCode() }
    }

    private suspend fun onSessionMinted(tokens: TokenResponse, email: String?) {
        sessionManager.onAuthenticated(tokens.accessToken, tokens.refreshToken, email)
        _authenticated.tryEmit(Unit)
        doGenerateCode()
    }

    private suspend fun doGenerateCode() {
        if (!session.isLoggedIn()) {
            _state.value = ParentPairingState.NeedsAuth
            return
        }
        _state.value = ParentPairingState.Loading
        // No token argument — the parent client attaches and refreshes it.
        runCatching { parentApi.createPairingCode() }
            .onSuccess { _state.value = ParentPairingState.ShowCode(it.code) }
            .onFailure {
                _state.value = ParentPairingState.Error(it.message ?: "Could not create code")
            }
    }
}
