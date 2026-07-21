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
package com.shdev.guardian.feature.parent.impl.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shdev.guardian.feature.parent.impl.R
import org.koin.androidx.compose.koinViewModel

/**
 * Account settings, shown as a Navigation 3 dialog scene over the current dashboard tab.
 *
 * Logging out does not navigate directly — it clears the session, which broadcasts through
 * ParentSessionManager, and the host resets to role selection. Same path a failed token refresh
 * takes, so there is exactly one way back to onboarding.
 */
@Composable
fun AccountSettingsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_avatar_placeholder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp),
            )

            Text(
                text = state.email ?: "Signed in",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            when {
                state.loadingDevices -> CircularProgressIndicator(Modifier.size(20.dp))

                state.deviceError != null -> {
                    Text(
                        text = state.deviceError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::loadDeviceCount) { Text("Retry") }
                }

                else -> Text(
                    text = "Connected Devices: ${state.connectedDevices ?: 0}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::logout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Log out")
            }

            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
