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

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold/warm/hot startup timing for [com.shdev.guardian.ui.OnboardingActivity].
 *
 * The app has no login wall, but the first frame is a [CircularProgressIndicator] shown while an
 * encrypted-DataStore role read (suspend) resolves. We wait for RoleSelectionScreen's headline so the
 * measured journey covers Koin start, Firebase init, the DataStore read, and Compose's first real UI.
 *
 * [coldStartupNoCompilation] vs [coldStartupBaselineProfile] is the profile-effectiveness A/B:
 * `Partial(Require)` fails loudly if no profile was packaged — that's intentional.
 *
 * Run on a physical device: `./gradlew :macrobenchmark:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun coldStartupBaselineProfile() =
        startup(StartupMode.COLD, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun warmStartup() = startup(StartupMode.WARM, CompilationMode.Partial())

    @Test
    fun hotStartup() = startup(StartupMode.HOT, CompilationMode.Partial())

    private fun startup(startupMode: StartupMode, compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.text(ROLE_SCREEN_ANCHOR)), UI_TIMEOUT_MS)
        }
}
