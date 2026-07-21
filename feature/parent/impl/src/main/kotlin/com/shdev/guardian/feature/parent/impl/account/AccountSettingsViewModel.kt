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
package com.shdev.guardian.feature.parent.impl.account

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shdev.guardian.data.account.ParentAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
data class AccountState(
    val email: String? = null,
    /** null while the device count is still loading or failed to load. */
    val connectedDevices: Int? = null,
    val loadingDevices: Boolean = true,
    val deviceError: String? = null,
)

/**
 * Backs the account settings dialog. The email is a local read; the device count is a live network
 * call that can fail independently, so the rest of the dialog renders regardless.
 */
class AccountSettingsViewModel(
    private val repository: ParentAccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = _state.value.copy(email = repository.email()) }
        loadDeviceCount()
    }

    fun loadDeviceCount() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingDevices = true, deviceError = null)
            repository.connectedDeviceCount().fold(
                onSuccess = {
                    _state.value = _state.value.copy(connectedDevices = it, loadingDevices = false)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        loadingDevices = false,
                        deviceError = error.message ?: "Couldn't load devices",
                    )
                },
            )
        }
    }

    /**
     * Clears tokens and role, then broadcasts. The host collects that broadcast and resets to role
     * selection — the same path a failed token refresh takes, so there is only one way out.
     */
    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
