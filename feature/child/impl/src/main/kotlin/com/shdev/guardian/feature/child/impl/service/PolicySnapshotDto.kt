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
package com.shdev.guardian.feature.child.impl.service

import com.shdev.guardian.policy.AppRule
import com.shdev.guardian.policy.CategoryRule
import com.shdev.guardian.policy.PolicySnapshot
import com.shdev.guardian.policy.Schedule
import kotlinx.serialization.Serializable

/** Wire form of the authoritative policy. Server assigns [version]; client rejects version <= cached. */
@Serializable
data class PolicySnapshotDto(
    val version: Long,
    val devicePaused: Boolean = false,
    val appRules: List<AppRuleDto> = emptyList(),
    val categoryRules: List<CategoryRuleDto> = emptyList(),
    val schedules: List<ScheduleDto> = emptyList(),
    val bonusMs: Map<String, Long> = emptyMap(),
    val whitelist: List<String> = emptyList(),
    val categories: Map<String, String> = emptyMap(),
) {
    fun toDomain() = PolicySnapshot(
        version = version,
        devicePaused = devicePaused,
        appRules = appRules.associate {
            it.packageName to AppRule(
                packageName = it.packageName,
                dailyQuotaMs = it.dailyQuotaMs,
                hardBlocked = it.hardBlocked
            )
        },
        categoryRules = categoryRules.associate {
            it.category to CategoryRule(
                category = it.category,
                dailyQuotaMs = it.dailyQuotaMs,
                hardBlocked = it.hardBlocked
            )
        },
        schedules = schedules.map {
            Schedule(
                id = it.id,
                dayMask = it.dayMask,
                startMin = it.startMin,
                endMin = it.endMin
            )
        },
        bonusMs = bonusMs,
        whitelist = whitelist.toSet(),
        categories = categories,
    )
}

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
data class ScheduleDto(val id: String, val dayMask: Int, val startMin: Int, val endMin: Int)
