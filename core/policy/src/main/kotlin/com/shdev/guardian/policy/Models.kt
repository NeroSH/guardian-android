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
package com.shdev.guardian.policy

/**
 * Pure-Kotlin policy models. No Android dependencies so the evaluator is unit-testable
 * on the JVM with plain data in / data out (no Robolectric).
 */

/** A single app's per-day quota. [dailyQuotaMs] is the base; bonus is folded in at eval time. */
data class AppRule(
    val packageName: String,
    val dailyQuotaMs: Long,
    val hardBlocked: Boolean = false,
)

/** Category-level quota. Package→category mapping is resolved before eval (see [EvalContext]). */
data class CategoryRule(
    val category: String,
    val dailyQuotaMs: Long,
    val hardBlocked: Boolean = false,
)

/**
 * A recurring block window (bedtime, school). [dayMask] is a 7-bit set, bit0 = Monday.
 * [startMin]/[endMin] are minutes-from-local-midnight. When start > end the window crosses
 * midnight (e.g. 21:00 -> 07:00).
 */
data class Schedule(
    val id: String,
    val dayMask: Int,
    val startMin: Int,
    val endMin: Int,
)

/**
 * The authoritative, cached policy the child device enforces. Written only by SyncWorker.
 * [version] is the server-assigned monotonic version — a snapshot with version <= cached is rejected.
 */
data class PolicySnapshot(
    val version: Long,
    val devicePaused: Boolean,
    val appRules: Map<String, AppRule>,
    val categoryRules: Map<String, CategoryRule>,
    val schedules: List<Schedule>,
    val bonusMs: Map<String, Long> = emptyMap(),
    /** Packages that are always allowed regardless of any rule: dialer, launcher, settings, us. */
    val whitelist: Set<String> = emptySet(),
    /** Server-provided package -> category map, used to resolve a foreground app's category. */
    val categories: Map<String, String> = emptyMap(),
) {
    /** All packages sharing [category] — used to sum category usage across apps. */
    fun packagesInCategory(category: String): List<String> =
        categories.filterValues { it == category }.keys.toList()

    companion object {
        val EMPTY = PolicySnapshot(
            version = 0,
            devicePaused = false,
            appRules = emptyMap(),
            categoryRules = emptyMap(),
            schedules = emptyList(),
        )
    }
}

/**
 * Everything the evaluator needs at a single tick. The clock is injected — the evaluator never
 * calls System.currentTimeMillis() — which is what makes time-boundary tests deterministic.
 */
data class EvalContext(
    val foregroundPkg: String?,
    val nowMillis: Long,
    /** Minutes-from-local-midnight in the device time zone, and the day-of-week bit (0 = Monday). */
    val localMinuteOfDay: Int,
    val dayOfWeekBit: Int,
    /** Category of [foregroundPkg], or null if uncategorised. Resolved from the synced category table. */
    val category: String?,
    /** Effective usage today for [foregroundPkg]: persisted totals + current live-session elapsed. */
    val usedTodayMs: Long,
    /** Effective usage today for [category]. */
    val categoryUsedTodayMs: Long,
    val snapshot: PolicySnapshot,
)

/** The evaluator's verdict. [Warn] carries remaining time so the UI can count down. */
sealed interface Decision {
    data object None : Decision
    data object Allow : Decision
    data class Warn(val remainingMs: Long, val reason: String) : Decision
    data class Block(val reason: String) : Decision
}
