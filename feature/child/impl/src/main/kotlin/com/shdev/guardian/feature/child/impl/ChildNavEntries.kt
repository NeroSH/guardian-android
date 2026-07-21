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

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.shdev.guardian.feature.child.api.ChildRoutes
import com.shdev.guardian.feature.child.impl.onboarding.ActiveScreen
import com.shdev.guardian.feature.child.impl.onboarding.PermissionFunnelScreen

/**
 * Registers the child-feature screen into the host's Navigation 3 entry provider.
 *
 * @param onPaired invoked once the device is paired; :app routes on to the permission funnel.
 */
fun EntryProviderScope<NavKey>.childEntries(
    onPaired: () -> Unit,
    onPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<ChildRoutes.Scan> {
        ChildScanScreen(onPaired = onPaired, modifier = modifier)
    }

    entry<ChildRoutes.Funnel> {
        PermissionFunnelScreen(
            onComplete = onPermissionsGranted,
            modifier = modifier,
        )
    }

    entry<ChildRoutes.Done> { ActiveScreen(modifier = modifier) }
}
