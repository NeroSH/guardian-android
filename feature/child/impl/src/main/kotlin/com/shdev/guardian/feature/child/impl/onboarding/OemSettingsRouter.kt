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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Deep-links to OEM autostart / protected-app screens. None of these grants is queryable via a public
 * API and the component names change per OS version, so every candidate is tried behind a
 * resolveActivity() check and we fall back to the app-details screen. Success is verified BEHAVIOURALLY
 * later (the post-onboarding heartbeat "survival check"), never assumed from a successful launch.
 */
object OemSettingsRouter {

    /** True when the current device is from a manufacturer known to aggressively kill background apps. */
    fun isAggressiveOem(): Boolean = manufacturer() in AGGRESSIVE_OEMS

    /**
     * Launches the best-matching OEM autostart screen, or the app-details settings as a fallback.
     * Returns true if some settings screen was opened.
     */
    fun openAutostartSettings(context: Context): Boolean {
        for (component in candidatesFor(manufacturer())) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                return runCatching { context.startActivity(intent); true }.getOrDefault(false)
            }
        }
        return openAppDetails(context)
    }

    private fun openAppDetails(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun manufacturer(): String = android.os.Build.MANUFACTURER.lowercase()

    private fun candidatesFor(oem: String): List<ComponentName> = when {
        oem.contains("xiaomi") || oem.contains("redmi") || oem.contains("poco") -> listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            ),
        )

        oem.contains("samsung") -> listOf(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
        )

        oem.contains("oppo") || oem.contains("realme") -> listOf(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
        )

        oem.contains("vivo") -> listOf(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
        )

        oem.contains("huawei") || oem.contains("honor") -> listOf(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
        )

        else -> emptyList()
    }

    private val AGGRESSIVE_OEMS = setOf(
        "xiaomi", "redmi", "poco", "samsung", "oppo", "realme", "vivo", "huawei", "honor",
    )
}
