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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shdev.guardian.data.alerts.AlertDto
import com.shdev.guardian.data.alerts.AlertsApi
import com.shdev.guardian.data.auth.ParentSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AlertsState {
    data object Loading : AlertsState
    data class Ready(val alerts: List<AlertDto>, val message: String? = null) : AlertsState
    data class Error(val message: String) : AlertsState
}

/** Loads the family's tamper alerts and acknowledges them. */
class ParentAlertsViewModel(
    private val api: AlertsApi,
    private val session: ParentSessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<AlertsState>(AlertsState.Loading)
    val state: StateFlow<AlertsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (!session.isLoggedIn()) {
                _state.value = AlertsState.Error("Not logged in"); return@launch
            }
            _state.value = AlertsState.Loading
            // No token argument — the parent client attaches and refreshes it.
            runCatching { api.list() }
                .onSuccess {
                    _state.value = AlertsState.Ready(it.sortedByDescending { a -> a.createdAt })
                }
                .onFailure {
                    _state.value = AlertsState.Error(it.message ?: "Failed to load alerts")
                }
        }
    }

    fun acknowledge(id: String) {
        viewModelScope.launch {
            if (!session.isLoggedIn()) return@launch
            runCatching { api.acknowledge(id) }
                .onSuccess { load() }
                .onFailure { current ->
                    (_state.value as? AlertsState.Ready)?.let {
                        _state.value = it.copy(message = "Couldn't dismiss: ${current.message}")
                    }
                }
        }
    }
}
