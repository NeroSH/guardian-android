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

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * @param onAuthenticated invoked once after a successful sign-in, so the host can promote the parent
 * into the dashboard. No-op when this screen is already showing as a dashboard tab.
 */
@Composable
fun ParentPairingScreen(
    modifier: Modifier = Modifier,
    onAuthenticated: () -> Unit = {},
    viewModel: ParentPairingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val verifiedName by viewModel.verifiedName.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(viewModel) {
        viewModel.authenticated.collect { onAuthenticated() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            is ParentPairingState.NeedsAuth -> {
                // Digital Credentials need API 28 + GMS 25.49; below that the password form is the
                // only path, so the button is not shown at all.
                if (viewModel.isVerifiedEmailSupported && activity != null) {
                    OutlinedButton(
                        onClick = { viewModel.authenticateWithVerifiedEmail(activity) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue with verified email")
                    }
                    Text(
                        "No password, no verification code.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 20.dp))
                }
                AuthForm(onSubmit = viewModel::authenticate)
            }

            is ParentPairingState.Loading ->
                CircularProgressIndicator()

            is ParentPairingState.ShowCode -> {
                verifiedName?.let {
                    Text(
                        "Signed in as $it",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text("Scan with your child's device", style = MaterialTheme.typography.titleLarge)
                QrImage(
                    content = s.code,
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .size(280.dp),
                )
                Text(s.code, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Code expires shortly. Generate a new one if it stops working.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = viewModel::generateCode,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Generate new code")
                }
            }

            is ParentPairingState.Error -> {
                Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
                Text(
                    s.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(onClick = viewModel::generateCode) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun AuthForm(onSubmit: (email: String, password: String, register: Boolean) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(true) }

    Text(
        if (register) "Create a parent account" else "Log in",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    Button(
        onClick = { onSubmit(email.trim(), password, register) },
        enabled = email.isNotBlank() && password.length >= 8,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        Text(if (register) "Create account" else "Log in")
    }
    TextButton(onClick = { register = !register }) {
        Text(if (register) "I already have an account" else "Create a new account")
    }
}
