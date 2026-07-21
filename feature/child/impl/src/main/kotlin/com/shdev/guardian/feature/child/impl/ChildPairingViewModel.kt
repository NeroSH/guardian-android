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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shdev.guardian.data.auth.PairingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChildPairingState {
    data object Scanning : ChildPairingState
    data object Pairing : ChildPairingState
    data object Paired : ChildPairingState
    data class Error(val message: String) : ChildPairingState
}

/**
 * Child onboarding: scan the parent's QR, redeem it, persist the device token. On [ChildPairingState
 * .Paired] the caller starts the MonitorService (after the remaining permission steps).
 */
class ChildPairingViewModel(
    private val coordinator: PairingCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow<ChildPairingState>(ChildPairingState.Scanning)
    val state: StateFlow<ChildPairingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Firebase comes up as the pairing screen is constructed — before the user can scan anything,
            // so the FCM token has time to be minted and is ready when the pair request is built. This is
            // the app's FIRST Firebase initialization on an unpaired install; every earlier launch ran
            // without it. Idempotent, so a ViewModel recreated by config change costs nothing.
            coordinator.prepareForPairing()
        }
    }

    fun onCodeScanned(code: String) {
        if (_state.value != ChildPairingState.Scanning) return // ignore extra frames
        _state.value = ChildPairingState.Pairing
        viewModelScope.launch {
            when (val result = coordinator.pairWithScannedCode(code)) {
                is PairingRepository.Result.Success -> _state.value = ChildPairingState.Paired
                is PairingRepository.Result.Failure -> _state.value =
                    ChildPairingState.Error(result.reason)
            }
        }
    }

    fun retry() {
        _state.value = ChildPairingState.Scanning
    }
}
