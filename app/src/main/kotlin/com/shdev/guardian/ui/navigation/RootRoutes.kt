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
package com.shdev.guardian.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable


/**
 * Navigation 3 routes. [Role] (onboarding home) and [com.shdev.guardian.feature.child.api.ChildRoutes.Done] (supervised home) are top-level routes,
 * each with its own back stack; the rest are child routes pushed onto the current stack.
 */
internal object Routes {
    @Serializable
    data object Role : NavKey
}