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
 * Stateless, deterministic policy decision function. Lives in :core:policy with no Android deps.
 *
 * Precedence (strongest override first):
 *   1. no foreground app (launcher/home)        -> Allow
 *   2. whitelist (dialer, launcher, us, ...)     -> Allow, beats everything
 *   3. devicePaused (parent "pause now")         -> Block
 *   4. active schedule (bedtime/school)          -> Block
 *   5. hard block flag on app/category           -> Block
 *   6. per-app / category quota                  -> Block if remaining <= 0, else Warn under threshold, else Allow
 *
 * Stacking: per-app and category quotas STACK — the more restrictive of the two wins. A blocked
 * category cannot be escaped by an unset per-app rule, and vice versa.
 *
 * Orchestration state (last-warned app, overlay already shown) does NOT live here — it belongs to
 * the service — so this stays a pure function.
 */
class PolicyEvaluator(
    /** Warn the user when remaining quota drops below this (default 5 min). */
    private val warnThresholdMs: Long = 5 * 60_000L,
) {

    fun evaluate(ctx: EvalContext): Decision {
        val pkg = ctx.foregroundPkg ?: return Decision.Allow            // 1
        val snap = ctx.snapshot

        if (pkg in snap.whitelist) return Decision.Allow                 // 2
        if (snap.devicePaused) return Decision.Block("Paused by parent") // 3

        activeSchedule(ctx)?.let {                                       // 4
            return Decision.Block("Scheduled downtime")
        }

        val appRule = snap.appRules[pkg]
        val catRule = ctx.category?.let { snap.categoryRules[it] }

        if (appRule?.hardBlocked == true || catRule?.hardBlocked == true) { // 5
            return Decision.Block("App is blocked")
        }

        // 6 — evaluate both quotas, keep the more restrictive verdict.
        val appDecision = appRule?.let {
            quotaDecision(
                quotaMs = it.dailyQuotaMs + (snap.bonusMs[pkg] ?: 0L),
                usedMs = ctx.usedTodayMs,
                reason = "Daily limit reached",
            )
        }
        val catDecision = catRule?.let {
            quotaDecision(
                quotaMs = it.dailyQuotaMs,
                usedMs = ctx.categoryUsedTodayMs,
                reason = "Category limit reached",
            )
        }

        return mostRestrictive(appDecision, catDecision) ?: Decision.Allow
    }

    /** True when a schedule bans the current package right now. Handles midnight-crossing windows. */
    private fun activeSchedule(ctx: EvalContext): Schedule? =
        ctx.snapshot.schedules.firstOrNull { s ->
            (s.dayMask and (1 shl ctx.dayOfWeekBit)) != 0 && inWindow(ctx.localMinuteOfDay, s)
        }

    private fun inWindow(t: Int, s: Schedule): Boolean =
        if (s.startMin <= s.endMin) t in s.startMin until s.endMin
        else t >= s.startMin || t < s.endMin        // crosses midnight, e.g. 21:00 -> 07:00

    private fun quotaDecision(quotaMs: Long, usedMs: Long, reason: String): Decision {
        val remaining = quotaMs - usedMs
        return when {
            remaining <= 0L -> Decision.Block(reason)
            remaining <= warnThresholdMs -> Decision.Warn(remaining, "Almost out of time")
            else -> Decision.Allow
        }
    }

    /** Block > Warn(least remaining) > Allow. Null inputs (no rule) are ignored. */
    private fun mostRestrictive(a: Decision?, b: Decision?): Decision? = when {
        a is Decision.Block -> a
        b is Decision.Block -> b
        a is Decision.Warn && b is Decision.Warn -> if (a.remainingMs <= b.remainingMs) a else b
        a is Decision.Warn -> a
        b is Decision.Warn -> b
        a is Decision.Allow || b is Decision.Allow -> Decision.Allow
        else -> null
    }
}
