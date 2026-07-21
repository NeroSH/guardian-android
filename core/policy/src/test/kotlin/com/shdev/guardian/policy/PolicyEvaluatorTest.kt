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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PolicyEvaluatorTest {

    private val evaluator = PolicyEvaluator(warnThresholdMs = 5 * 60_000L)

    private fun ctx(
        pkg: String? = "com.game",
        minuteOfDay: Int = 12 * 60,
        dayBit: Int = 0,
        used: Long = 0,
        catUsed: Long = 0,
        category: String? = null,
        snapshot: PolicySnapshot = PolicySnapshot.EMPTY,
    ) = EvalContext(
        foregroundPkg = pkg,
        nowMillis = 0L,
        localMinuteOfDay = minuteOfDay,
        dayOfWeekBit = dayBit,
        category = category,
        usedTodayMs = used,
        categoryUsedTodayMs = catUsed,
        snapshot = snapshot,
    )

    @Test
    fun `no foreground app is allowed`() {
        assertIs<Decision.Allow>(evaluator.evaluate(ctx(pkg = null)))
    }

    @Test
    fun `whitelist beats device pause`() {
        val snap = PolicySnapshot.EMPTY.copy(devicePaused = true, whitelist = setOf("com.dialer"))
        assertIs<Decision.Allow>(evaluator.evaluate(ctx(pkg = "com.dialer", snapshot = snap)))
    }

    @Test
    fun `device pause blocks a normal app`() {
        val snap = PolicySnapshot.EMPTY.copy(devicePaused = true)
        assertIs<Decision.Block>(evaluator.evaluate(ctx(snapshot = snap)))
    }

    @Test
    fun `bedtime crossing midnight blocks at 2am`() {
        val bedtime = Schedule(id = "s", dayMask = 0x7F, startMin = 21 * 60, endMin = 7 * 60)
        val snap = PolicySnapshot.EMPTY.copy(schedules = listOf(bedtime))
        assertIs<Decision.Block>(evaluator.evaluate(ctx(minuteOfDay = 2 * 60, snapshot = snap)))
    }

    @Test
    fun `bedtime crossing midnight allows at noon`() {
        val bedtime = Schedule(id = "s", dayMask = 0x7F, startMin = 21 * 60, endMin = 7 * 60)
        val snap = PolicySnapshot.EMPTY.copy(schedules = listOf(bedtime))
        assertIs<Decision.Allow>(evaluator.evaluate(ctx(minuteOfDay = 12 * 60, snapshot = snap)))
    }

    @Test
    fun `quota exhausted blocks`() {
        val snap = PolicySnapshot.EMPTY.copy(
            appRules = mapOf("com.game" to AppRule("com.game", dailyQuotaMs = 60 * 60_000L)),
        )
        assertIs<Decision.Block>(evaluator.evaluate(ctx(used = 60 * 60_000L, snapshot = snap)))
    }

    @Test
    fun `bonus time extends the quota ceiling`() {
        val snap = PolicySnapshot.EMPTY.copy(
            appRules = mapOf("com.game" to AppRule("com.game", dailyQuotaMs = 60 * 60_000L)),
            bonusMs = mapOf("com.game" to 30 * 60_000L),
        )
        // 60 min used against 60+30 min ceiling -> 30 min left -> Allow
        assertIs<Decision.Allow>(evaluator.evaluate(ctx(used = 60 * 60_000L, snapshot = snap)))
    }

    @Test
    fun `warn fires just under threshold`() {
        val snap = PolicySnapshot.EMPTY.copy(
            appRules = mapOf("com.game" to AppRule("com.game", dailyQuotaMs = 60 * 60_000L)),
        )
        val d = evaluator.evaluate(ctx(used = 56 * 60_000L, snapshot = snap))
        assertIs<Decision.Warn>(d)
        assertEquals(4 * 60_000L, d.remainingMs)
    }

    @Test
    fun `stacking keeps the more restrictive of app and category`() {
        // App still has time, but the category is exhausted -> Block wins.
        val snap = PolicySnapshot.EMPTY.copy(
            appRules = mapOf("com.game" to AppRule("com.game", dailyQuotaMs = 120 * 60_000L)),
            categoryRules = mapOf("games" to CategoryRule("games", dailyQuotaMs = 30 * 60_000L)),
        )
        val d = evaluator.evaluate(
            ctx(
                used = 10 * 60_000L,
                catUsed = 30 * 60_000L,
                category = "games",
                snapshot = snap
            )
        )
        assertIs<Decision.Block>(d)
    }
}
