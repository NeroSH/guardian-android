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
package com.shdev.guardian.feature.child.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildScanScreen(
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChildPairingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    var pickError by remember { mutableStateOf<String?>(null) }
    // Photo Picker needs no storage permission; decode the chosen image with ML Kit.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scanQrFromImage(context, uri) { value ->
                if (value != null) viewModel.onCodeScanned(value)
                else pickError = "No QR code found in that image"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state) {
        if (state is ChildPairingState.Paired) onPaired()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is ChildPairingState.Scanning -> {
                if (hasCameraPermission) {
                    QrScannerView(
                        onScanned = viewModel::onCodeScanned,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PermissionRationale { permissionLauncher.launch(Manifest.permission.CAMERA) }
                }
                // Controls overlaid at the bottom — the gallery upload works even without camera.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (hasCameraPermission) {
                        Text(
                            "Point the camera at your parent's QR code",
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    pickError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            pickError = null
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Upload QR screenshot")
                    }
                }
            }

            is ChildPairingState.Pairing -> CircularProgressIndicator()

            is ChildPairingState.Paired ->
                Text("Device paired", style = MaterialTheme.typography.headlineSmall)

            is ChildPairingState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Text("Pairing failed", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = s.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(onClick = viewModel::retry) { Text("Scan again") }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onGrant: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp),
    ) {
        Text("Camera needed to scan the pairing code", textAlign = TextAlign.Center)
        Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) { Text("Allow camera") }
    }
}
