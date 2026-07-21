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
package com.shdev.guardian.feature.child.impl.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.shdev.guardian.feature.child.impl.service.TamperReporter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Detects wall-clock manipulation (a child rolling the clock back to dodge a bedtime schedule).
 * Cross-checks the reported wall clock against the monotonic elapsedRealtime anchor captured at the
 * last sync; a mismatch beyond threshold is a tamper signal reported to the backend.
 */
class TimeChangedReceiver : BroadcastReceiver(), KoinComponent {

    private val tamperReporter: TamperReporter by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                tamperReporter.onClockChanged(
                    wallNow = System.currentTimeMillis(),
                    elapsedNow = SystemClock.elapsedRealtime(),
                )
            }
        }
    }
}
