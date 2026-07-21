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

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Phase 0 permission funnel. Each step shows prominent disclosure BEFORE launching the system prompt.
 * Required steps gate the "Start protection" button; the OEM autostart step is recommended (its state
 * can't be read, so it's advisory). On completion, starts the child runtime (MonitorService + schedules).
 */
@Composable
fun PermissionFunnelScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcher: ChildRuntimeLauncher = koinInject()
    val scope = rememberCoroutineScope()

    // Bump on resume so permission states re-read after the user returns from a settings screen.
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey++ }

    var oemAcknowledged by remember { mutableStateOf(false) }

    // Recomputed whenever refreshKey changes.
    val usage = remember(refreshKey) { PermissionChecks.hasUsageAccess(context) }
    val overlay = remember(refreshKey) { PermissionChecks.hasOverlay(context) }
    val notifications = remember(refreshKey) { PermissionChecks.hasNotifications(context) }
    val battery = remember(refreshKey) { PermissionChecks.isIgnoringBatteryOptimizations(context) }

    val requiredGranted = usage && overlay && notifications && battery

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Finish setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Guardian needs these to supervise this device. We only use them to enforce the limits " +
                    "you set — nothing else.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        StepCard(
            title = "Usage access",
            rationale = "Lets Guardian measure how long each app is used, so time limits work.",
            granted = usage,
            actionLabel = "Grant",
            onAction = { context.startActivity(PermissionChecks.usageAccessIntent()) },
        )
        StepCard(
            title = "Display over other apps",
            rationale = "Lets Guardian show the block screen when a limit is reached.",
            granted = overlay,
            actionLabel = "Grant",
            onAction = { context.startActivity(PermissionChecks.overlayIntent(context)) },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StepCard(
                title = "Notifications",
                rationale = "Shows the ongoing 'supervision active' notice and time-left warnings.",
                granted = notifications,
                actionLabel = "Allow",
                onAction = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        StepCard(
            title = "Ignore battery optimizations",
            rationale = "Stops the system from killing Guardian in the background.",
            granted = battery,
            actionLabel = "Grant",
            onAction = { context.startActivity(PermissionChecks.batteryExemptionIntent(context)) },
        )
        if (OemSettingsRouter.isAggressiveOem()) {
            StepCard(
                title = "Auto-start / protected app (recommended)",
                rationale = "Your device brand aggressively closes background apps. Enable auto-start " +
                        "so Guardian keeps running and restarts after reboot.",
                granted = oemAcknowledged,
                actionLabel = "Open settings",
                onAction = {
                    OemSettingsRouter.openAutostartSettings(context)
                    oemAcknowledged = true
                },
            )
        }

        Spacer(Modifier.padding(8.dp))
        Button(
            onClick = { scope.launch { launcher.start(context); onComplete() } },
            enabled = requiredGranted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(if (requiredGranted) "Start protection" else "Grant required permissions to continue")
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    rationale: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    rationale,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
