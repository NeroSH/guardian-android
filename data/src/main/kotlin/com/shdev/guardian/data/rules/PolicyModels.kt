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
package com.shdev.guardian.data.rules

import kotlinx.serialization.Serializable

/** Client mirror of the backend policy document. Field names match the server JSON exactly. */
@Serializable
data class PolicyDto(
    val version: Long = 0,
    val devicePaused: Boolean = false,
    val appRules: List<AppRuleDto> = emptyList(),
    val categoryRules: List<CategoryRuleDto> = emptyList(),
    val schedules: List<ScheduleDto> = emptyList(),
    val bonusMs: Map<String, Long> = emptyMap(),
    val whitelist: List<String> = emptyList(),
)

@Serializable
data class AppRuleDto(
    val packageName: String,
    val dailyQuotaMs: Long,
    val hardBlocked: Boolean = false
)

@Serializable
data class CategoryRuleDto(
    val category: String,
    val dailyQuotaMs: Long,
    val hardBlocked: Boolean = false
)

@Serializable
data class ScheduleDto(val id: String = "", val dayMask: Int, val startMin: Int, val endMin: Int)

/** POST body — full desired state. The server replaces everything and bumps the version. */
@Serializable
data class PolicyEditDto(
    val appRules: List<AppRuleDto> = emptyList(),
    val categoryRules: List<CategoryRuleDto> = emptyList(),
    val schedules: List<ScheduleDto> = emptyList(),
    val devicePaused: Boolean? = null,
    val bonusMs: Map<String, Long> = emptyMap(),
)

fun PolicyDto.toEdit() = PolicyEditDto(
    appRules = appRules,
    categoryRules = categoryRules,
    schedules = schedules,
    devicePaused = devicePaused,
    bonusMs = bonusMs,
)
