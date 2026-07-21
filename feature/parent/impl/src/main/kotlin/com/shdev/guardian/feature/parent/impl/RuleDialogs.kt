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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Add-limit dialog: a name (package or category) + a daily allowance in minutes. */
@Composable
fun NameAndMinutesDialog(
    title: String,
    nameLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, minutes: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    val minutes = minutesText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit) },
                    label = { Text("Minutes per day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && minutes > 0,
                onClick = { onConfirm(name.trim(), minutes) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Add-schedule dialog: pick days + a start/end hour. Handles overnight windows (start > end). */
@Composable
fun ScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (dayMask: Int, startMin: Int, endMin: Int) -> Unit,
) {
    val selectedDays = remember { mutableStateOf(setOf<Int>()) }
    var startHour by remember { mutableStateOf("21") }
    var endHour by remember { mutableStateOf("7") }

    val start = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val end = endHour.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val mask = selectedDays.value.fold(0) { acc, day -> acc or (1 shl day) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add schedule") },
        text = {
            Column {
                FlowRow {
                    DAY_LABELS.forEachIndexed { index, label ->
                        FilterChip(
                            selected = index in selectedDays.value,
                            onClick = {
                                selectedDays.value = selectedDays.value.toMutableSet().apply {
                                    if (!add(index)) remove(index)
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = startHour,
                    onValueChange = { startHour = it.filter(Char::isDigit).take(2) },
                    label = { Text("Start hour (0–23)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = endHour,
                    onValueChange = { endHour = it.filter(Char::isDigit).take(2) },
                    label = { Text("End hour (0–23)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedDays.value.isNotEmpty(),
                onClick = { onConfirm(mask, start * 60, end * 60) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
