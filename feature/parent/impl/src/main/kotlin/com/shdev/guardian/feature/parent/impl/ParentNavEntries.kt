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

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.shdev.guardian.feature.parent.api.ParentRoutes

/**
 * Registers the parent feature's host-level screens into :app's Navigation 3 entry provider.
 *
 * Only two routes surface here. [ParentRoutes.Pairing] is the pre-auth onboarding screen reached from
 * role selection, and [ParentRoutes.Dashboard] is the authenticated home. Rules and Alerts are no
 * longer registered at this level — they are tabs inside the dashboard's own nested display, so
 * their navigation is owned by the feature rather than plumbed through :app as callbacks.
 *
 * @param onAuthenticated called after a successful sign-in on the onboarding pairing screen; :app
 * resets its back stack to [ParentRoutes.Dashboard] so back cannot re-enter role selection.
 */
fun EntryProviderScope<NavKey>.parentEntries(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<ParentRoutes.Pairing> {
        ParentPairingScreen(
            modifier = modifier,
            onAuthenticated = onAuthenticated,
        )
    }
    entry<ParentRoutes.Dashboard> {
        // Draws its own Scaffold (top bar + bottom nav), so it takes the full window rather than the
        // host's content padding.
        ParentDashboardScreen()
    }
}
