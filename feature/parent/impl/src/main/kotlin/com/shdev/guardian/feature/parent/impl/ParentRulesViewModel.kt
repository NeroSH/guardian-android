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

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shdev.guardian.data.auth.ParentSessionStore
import com.shdev.guardian.data.rules.AppRuleDto
import com.shdev.guardian.data.rules.CategoryRuleDto
import com.shdev.guardian.data.rules.ParentRulesApi
import com.shdev.guardian.data.rules.PolicyDto
import com.shdev.guardian.data.rules.SaveResult
import com.shdev.guardian.data.rules.ScheduleDto
import com.shdev.guardian.data.rules.toEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@Stable
sealed interface RulesState {
    data object Loading : RulesState
    data class Ready(
        val policy: PolicyDto,
        val saving: Boolean = false,
        val message: String? = null
    ) : RulesState

    data class Error(val message: String) : RulesState
}

/**
 * Loads the family policy, applies local edits to a working copy, and saves the full document back.
 * Version conflicts (another parent wrote first) replace the working copy with the server's and ask
 * the parent to reapply — no silent field-merge (whole-document LWW).
 */
class ParentRulesViewModel(
    private val api: ParentRulesApi,
    private val session: ParentSessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<RulesState>(RulesState.Loading)
    val state: StateFlow<RulesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (!session.isLoggedIn()) {
                _state.value = RulesState.Error("Not logged in"); return@launch
            }
            _state.value = RulesState.Loading
            // No token argument — the parent client attaches and refreshes it.
            runCatching { api.getPolicy() }
                .onSuccess { _state.value = RulesState.Ready(it) }
                .onFailure { _state.value = RulesState.Error(it.message ?: "Failed to load rules") }
        }
    }

    private fun edit(block: (PolicyDto) -> PolicyDto) {
        val current = _state.value as? RulesState.Ready ?: return
        _state.value = current.copy(policy = block(current.policy), message = null)
    }

    fun setPaused(paused: Boolean) = edit { it.copy(devicePaused = paused) }

    fun addAppLimit(packageName: String, minutes: Int) = edit { p ->
        val without = p.appRules.filterNot { it.packageName == packageName }
        p.copy(appRules = without + AppRuleDto(packageName, minutes * 60_000L))
    }

    fun removeAppLimit(packageName: String) = edit { p ->
        p.copy(appRules = p.appRules.filterNot { it.packageName == packageName })
    }

    fun addCategoryLimit(category: String, minutes: Int) = edit { p ->
        val without = p.categoryRules.filterNot { it.category == category }
        p.copy(categoryRules = without + CategoryRuleDto(category, minutes * 60_000L))
    }

    fun removeCategoryLimit(category: String) = edit { p ->
        p.copy(categoryRules = p.categoryRules.filterNot { it.category == category })
    }

    fun addSchedule(dayMask: Int, startMin: Int, endMin: Int) = edit { p ->
        p.copy(
            schedules = p.schedules + ScheduleDto(
                UUID.randomUUID().toString(),
                dayMask,
                startMin,
                endMin
            )
        )
    }

    fun removeSchedule(id: String) = edit { p ->
        p.copy(schedules = p.schedules.filterNot { it.id == id })
    }

    fun save() {
        val ready = _state.value as? RulesState.Ready ?: return
        viewModelScope.launch {
            if (!session.isLoggedIn()) {
                _state.value = RulesState.Error("Not logged in"); return@launch
            }
            _state.value = ready.copy(saving = true, message = null)
            runCatching {
                api.updatePolicy(
                    expectedVersion = ready.policy.version,
                    edit = ready.policy.toEdit()
                )
            }.onSuccess { result ->
                _state.value = when (result) {
                    is SaveResult.Saved -> RulesState.Ready(result.policy, message = "Saved")
                    is SaveResult.Conflict -> RulesState.Ready(
                        result.server,
                        message = "Someone else changed the rules. Reloaded — reapply your change.",
                    )
                }
            }.onFailure {
                _state.value = ready.copy(saving = false, message = it.message ?: "Save failed")
            }
        }
    }
}
