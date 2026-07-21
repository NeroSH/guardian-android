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

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.shdev.guardian.feature.child.impl.service.TamperReporter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Device admin (NOT device owner). Deactivation requires a deliberate settings flow; we use the
 * warning hook to alert the parent that supervision is being removed. This raises the bar for a
 * standard-install teardown and — more importantly — guarantees the parent is told.
 */
class SupervisorDeviceAdminReceiver : DeviceAdminReceiver(), KoinComponent {

    private val tamperReporter: TamperReporter by inject()

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        tamperReporter.onAdminDisableRequested()
        return "Removing Guardian will notify your parent and stop supervision."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        tamperReporter.onAdminDisabled()
    }
}
