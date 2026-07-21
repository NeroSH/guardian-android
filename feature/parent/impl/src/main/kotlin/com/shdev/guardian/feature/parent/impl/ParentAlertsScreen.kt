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
package com.shdev.guardian.feature.parent.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shdev.guardian.data.alerts.AlertDto
import org.koin.androidx.compose.koinViewModel

@Composable
fun ParentAlertsScreen(
    modifier: Modifier = Modifier,
    viewModel: ParentAlertsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is AlertsState.Loading -> Centered(modifier) { CircularProgressIndicator() }
        is AlertsState.Error -> Centered(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load alerts", style = MaterialTheme.typography.titleMedium)
                Text(
                    s.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(onClick = viewModel::load) { Text("Retry") }
            }
        }

        is AlertsState.Ready -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Alerts",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::load) { Text("Refresh") }
            }
            s.message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (s.alerts.isEmpty()) {
                Text(
                    "No alerts. Supervision is running normally.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            s.alerts.forEach { alert ->
                AlertCard(alert, onDismiss = { viewModel.acknowledge(alert.id) })
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertDto, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(friendlyKind(alert.kind), style = MaterialTheme.typography.titleMedium)
                Text(
                    alert.createdAt.take(16).replace('T', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!alert.acknowledged) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            } else {
                Text("Dismissed", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

private fun friendlyKind(kind: String): String = when (kind) {
    "CLOCK_TAMPER" -> "Device clock was changed"
    "ADMIN_DISABLE_REQUESTED" -> "Someone is trying to remove supervision"
    "ADMIN_DISABLED" -> "Supervision was removed"
    "SERVICE_DOWN" -> "Guardian was stopped on the device"
    else -> kind
}
