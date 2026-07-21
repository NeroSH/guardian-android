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
package com.shdev.guardian.feature.parent.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Public navigation contract for the parent feature. :app references these keys to wire the parent
 * flow into its back stack; the parent :impl module registers the matching entries.
 */
object ParentRoutes {
    /**
     * Authenticated parent home. A top-level route in :app's back stack, hosting its own bottom
     * navigation over [Rules], [Pairing] and [Alerts]. A parent who has authenticated lands here on
     * every launch until they explicitly log out.
     */
    @Serializable
    data object Dashboard : NavKey

    /** Parent onboarding home: authenticate + render the pairing QR. Also the dashboard's middle tab. */
    @Serializable
    data object Pairing : NavKey

    /** Manage rules & limits for the family policy. The dashboard's default (first) tab. */
    @Serializable
    data object Rules : NavKey

    /** View + acknowledge tamper alerts. The dashboard's last tab. */
    @Serializable
    data object Alerts : NavKey

    /** Account settings, rendered as a dialog scene over whichever tab is showing. */
    @Serializable
    data object AccountSettings : NavKey
}
