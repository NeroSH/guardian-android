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

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.shdev.guardian.core.navigation.Navigator
import com.shdev.guardian.core.navigation.rememberNavigationState
import com.shdev.guardian.core.navigation.toEntries
import com.shdev.guardian.feature.parent.api.ParentRoutes
import com.shdev.guardian.feature.parent.impl.account.AccountSettingsDialog

private data class Tab(val route: NavKey, val label: String, val icon: ImageVector)

/**
 * Tab order is part of the spec: Rules first (and the start route, so back exits through it),
 * Pairing in the middle, Alerts last.
 */
private val TABS = listOf(
    Tab(ParentRoutes.Rules, "Rules", Icons.AutoMirrored.Filled.List),
    Tab(ParentRoutes.Pairing, "Pairing", Icons.Default.Add),
    Tab(ParentRoutes.Alerts, "Alerts", Icons.Default.Notifications),
)

/**
 * Authenticated parent home. Hosts a nested Navigation 3 display over the three tabs, each with its
 * own back stack that survives config change and process death.
 *
 * The tab stacks are separate from :app's root stack on purpose — the dashboard is one top-level
 * route up there, so switching tabs can never strand the parent outside the parent flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(modifier: Modifier = Modifier) {
    val tabState = rememberNavigationState(
        startRoute = ParentRoutes.Rules,
        topLevelRoutes = TABS.map { it.route }.toSet(),
    )
    val tabNavigator = remember(tabState) { Navigator(tabState) }
    val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Guardian") },
                actions = {
                    IconButton(
                        onClick = dropUnlessResumed {
                            tabNavigator.navigate(ParentRoutes.AccountSettings)
                        }
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Account settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = tabState.topLevelRoute == tab.route,
                        onClick = dropUnlessResumed { tabNavigator.navigate(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        val entries = entryProvider {
            entry<ParentRoutes.Rules> {
                ParentRulesScreen(modifier = Modifier.padding(padding))
            }
            entry<ParentRoutes.Pairing> {
                ParentPairingScreen(modifier = Modifier.padding(padding))
            }
            entry<ParentRoutes.Alerts> {
                ParentAlertsScreen(modifier = Modifier.padding(padding))
            }
            entry<ParentRoutes.AccountSettings>(
                metadata = DialogSceneStrategy.dialog(),
            ) {
                AccountSettingsDialog(onDismiss = { tabNavigator.goBack() })
            }
        }

        NavDisplay(
            entries = tabState.toEntries(entries),
            onBack = { tabNavigator.goBack() },
            sceneStrategies = listOf(dialogStrategy),
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            popTransitionSpec = {
                slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
            },
            predictivePopTransitionSpec = {
                slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
