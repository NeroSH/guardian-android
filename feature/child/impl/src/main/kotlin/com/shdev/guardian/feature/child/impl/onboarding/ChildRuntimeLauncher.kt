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
package com.shdev.guardian.feature.child.impl.onboarding

import android.content.Context
import androidx.core.content.ContextCompat
import com.shdev.guardian.data.config.DeviceRole
import com.shdev.guardian.data.config.RoleStore
import com.shdev.guardian.feature.child.impl.service.MonitorService
import com.shdev.guardian.feature.child.impl.work.SyncWorker
import com.shdev.guardian.feature.child.impl.work.WatchdogScheduler

/**
 * Finishes child onboarding: marks the device as CHILD (so boot/watchdog receivers act), starts the
 * always-on MonitorService, and arms the resurrection + sync schedules. Call once the required
 * permissions are granted and pairing is complete.
 *
 * [roleStore] property is typed as Lazy<RoleStore> in order to minimize initialization time
 */
class ChildRuntimeLauncher(private val roleStore: Lazy<RoleStore>) {

    suspend fun start(context: Context) {
        roleStore.value.setRole(DeviceRole.CHILD)
        ContextCompat.startForegroundService(context, MonitorService.intent(context))
        WatchdogScheduler.ensureScheduled(context)
        SyncWorker.ensurePeriodic(context)
    }
}
