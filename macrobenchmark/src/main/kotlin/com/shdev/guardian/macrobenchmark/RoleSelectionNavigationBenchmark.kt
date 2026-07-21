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
package com.shdev.guardian.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the Role → Parent Navigation3 (NavDisplay) transition.
 *
 * Smoke-level signal only: the source screen is two buttons and the parent pairing screen may render
 * an error state with no backend — jank signal is near-noise, but the transition frames are real and
 * this exercises the NavDisplay path for the baseline profile / regression tripwire.
 *
 * REQUIRES a target install with no persisted parent session: once a parent authenticates, the role
 * is pinned to PARENT and launch routes straight to the dashboard, so [ROLE_SCREEN_ANCHOR] never
 * appears and setupBlock times out. Uninstall or clear app data before running.
 */
@RunWith(AndroidJUnit4::class)
class RoleSelectionNavigationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun parentNavigation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = null,
        iterations = 10,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text(ROLE_SCREEN_ANCHOR)), UI_TIMEOUT_MS)
        },
    ) {
        device.findObject(By.text(PARENT_BUTTON)).click()
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.text(ROLE_SCREEN_ANCHOR)), UI_TIMEOUT_MS)
    }
}
