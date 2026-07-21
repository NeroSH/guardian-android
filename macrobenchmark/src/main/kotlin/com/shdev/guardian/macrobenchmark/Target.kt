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
package com.shdev.guardian.macrobenchmark

/** Package under benchmark — single applicationId, no build-type suffixes. */
const val TARGET_PACKAGE = "com.shdev.guardian"

/** RoleSelectionScreen headline — signals the suspend DataStore role read finished and real UI is up. */
const val ROLE_SCREEN_ANCHOR = "Set up Guardian"

/** Parent-role button on RoleSelectionScreen. */
const val PARENT_BUTTON = "I'm a parent"

const val UI_TIMEOUT_MS = 10_000L
