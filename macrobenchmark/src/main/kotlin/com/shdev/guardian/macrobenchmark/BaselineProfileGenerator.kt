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

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline (and startup) profile for the critical launch path.
 *
 * Run headlessly on the Managed Device: `./gradlew :app:generateBaselineProfile`
 * → writes app/src/release/generated/baselineProfiles/{baseline-prof.txt, startup-prof.txt}.
 *
 * Journey covers startup (Koin/Firebase/DataStore/Compose first frame) plus the parent-role
 * Navigation3 NavDisplay transition. The child path is deliberately excluded: it routes to the QR
 * scanner, which triggers the CAMERA runtime dialog + CameraX/ML Kit init — flaky on a GMS-less GMD.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true, // Feeds dexLayoutOptimization (startup profile).
        maxIterations = 15,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text(ROLE_SCREEN_ANCHOR)), UI_TIMEOUT_MS)

        // Parent entry path: NavDisplay transition + parent pairing screen shell, then back.
        device.findObject(By.text(PARENT_BUTTON))?.let { parent ->
            parent.click()
            device.waitForIdle()
            device.pressBack()
            device.wait(Until.hasObject(By.text(ROLE_SCREEN_ANCHOR)), UI_TIMEOUT_MS)
        }
    }
}
