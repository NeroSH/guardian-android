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
import com.shdev.guardian.feature.child.impl.service.MonitorService
import com.shdev.guardian.feature.child.impl.work.SyncWorker
import com.shdev.guardian.feature.child.impl.work.WatchdogScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Restarts the MonitorService after boot, self-update, and OEM fast-boot. Registered for:
 *   BOOT_COMPLETED, LOCKED_BOOT_COMPLETED (direct-boot aware), QUICKBOOT_POWERON (HTC/older Samsung),
 *   and MY_PACKAGE_REPLACED (an app update silently kills the service).
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val roleStore: RoleStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // The role read hits an encrypted DataStore (suspend) — keep the receiver alive with goAsync().
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (!roleStore.isChild()) return@launch // parent device: inert
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_LOCKED_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    ACTION_QUICKBOOT,
                    ACTION_HTC_QUICKBOOT -> {
                        ContextCompat.startForegroundService(
                            context,
                            MonitorService.intent(context)
                        )
                        WatchdogScheduler.ensureScheduled(context)
                        SyncWorker.ensurePeriodic(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
