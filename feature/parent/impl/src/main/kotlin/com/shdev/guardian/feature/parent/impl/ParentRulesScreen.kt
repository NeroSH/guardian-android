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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@Composable
fun ParentRulesScreen(
    modifier: Modifier = Modifier,
    viewModel: ParentRulesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is RulesState.Loading -> Centered(modifier) { CircularProgressIndicator() }
        is RulesState.Error -> Centered(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load rules", style = MaterialTheme.typography.titleMedium)
                Text(
                    s.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(onClick = viewModel::load) { Text("Retry") }
            }
        }

        is RulesState.Ready -> RulesEditor(s, viewModel, modifier)
    }
}

@Composable
private fun RulesEditor(
    state: RulesState.Ready,
    vm: ParentRulesViewModel,
    modifier: Modifier
) {
    val p = state.policy
    var showAppDialog by remember { mutableStateOf(false) }
    var showCatDialog by remember { mutableStateOf(false) }
    var showSchedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Rules & limits", style = MaterialTheme.typography.headlineMedium)
        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Pause now
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pause device now", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Blocks everything until turned off",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = p.devicePaused, onCheckedChange = vm::setPaused)
            }
        }

        SectionHeader("App limits", onAdd = { showAppDialog = true })
        if (p.appRules.isEmpty()) EmptyHint("No app limits yet")
        p.appRules.forEach { rule ->
            RuleRow(title = rule.packageName, subtitle = "${rule.dailyQuotaMs / 60_000} min/day") {
                vm.removeAppLimit(rule.packageName)
            }
        }

        SectionHeader("Category limits", onAdd = { showCatDialog = true })
        if (p.categoryRules.isEmpty()) EmptyHint("No category limits yet")
        p.categoryRules.forEach { rule ->
            RuleRow(rule.category, "${rule.dailyQuotaMs / 60_000} min/day") {
                vm.removeCategoryLimit(rule.category)
            }
        }

        SectionHeader("Schedules (downtime)", onAdd = { showSchedDialog = true })
        if (p.schedules.isEmpty()) EmptyHint("No schedules yet")
        p.schedules.forEach { sched ->
            RuleRow(
                dayMaskLabel(sched.dayMask),
                "${hhmm(sched.startMin)} – ${hhmm(sched.endMin)}"
            ) {
                vm.removeSchedule(sched.id)
            }
        }

        Button(
            onClick = vm::save,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            Text(if (state.saving) "Saving…" else "Save changes")
        }
    }

    if (showAppDialog) {
        NameAndMinutesDialog(
            "Add app limit",
            "Package name",
            onDismiss = { showAppDialog = false }) { name, mins ->
            vm.addAppLimit(name, mins); showAppDialog = false
        }
    }
    if (showCatDialog) {
        NameAndMinutesDialog(
            "Add category limit",
            "Category",
            onDismiss = { showCatDialog = false }) { name, mins ->
            vm.addCategoryLimit(name, mins); showCatDialog = false
        }
    }
    if (showSchedDialog) {
        ScheduleDialog(onDismiss = { showSchedDialog = false }) { mask, start, end ->
            vm.addSchedule(mask, start, end); showSchedDialog = false
        }
    }
}

// --- small building blocks ---

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

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onAdd) { Text("Add") }
    }
    HorizontalDivider(Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun RuleRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}

private fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun dayMaskLabel(mask: Int): String {
    val on = DAYS.indices.filter { (mask and (1 shl it)) != 0 }.map { DAYS[it] }
    return if (on.size == 7) "Every day" else if (on.isEmpty()) "No days" else on.joinToString(", ")
}
