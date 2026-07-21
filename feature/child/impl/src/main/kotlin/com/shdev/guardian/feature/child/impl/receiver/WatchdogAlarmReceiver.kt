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
import androidx.core.content.ContextCompat
import com.shdev.guardian.data.config.RoleStore
import com.shdev.guardian.feature.child.impl.service.HeartbeatStore
import com.shdev.guardian.feature.child.impl.service.MonitorService
import com.shdev.guardian.feature.child.impl.service.Uptime
import com.shdev.guardian.feature.child.impl.work.WatchdogScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Fires from the exact-alarm chain. An alarm firing grants a temporary background-FGS-start window,
 * which is the legal path to restart the service after an OEM kill. Re-arms the alarm each time.
 */
class WatchdogAlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val heartbeat: HeartbeatStore by inject()
    private val roleStore: RoleStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        // The role read hits an encrypted DataStore (suspend) — keep the receiver alive with goAsync().
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (!roleStore.isChild()) return@launch
                val stale = Uptime.elapsed() - heartbeat.last() > STALE_MS
                if (stale) ContextCompat.startForegroundService(
                    context,
                    MonitorService.intent(context)
                )
                WatchdogScheduler.ensureAlarm(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val STALE_MS = 90_000L
    }
}
