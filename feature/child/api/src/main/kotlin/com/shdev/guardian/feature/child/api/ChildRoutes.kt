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
package com.shdev.guardian.feature.child.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Public navigation contract for the child feature. :app references these keys to wire the child
 * flow into its back stack; the child :impl module registers the matching entries.
 */
object ChildRoutes {
    /** Child onboarding: scan the parent's pairing QR and redeem it. */
    @Serializable
    data object Scan : NavKey

    @Serializable
    data object Funnel : NavKey

    @Serializable
    data object Done : NavKey
}
