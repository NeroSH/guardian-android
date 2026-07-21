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
package com.shdev.guardian.data.sync

import kotlinx.serialization.Serializable

/** Minimal probe to read the version off a policy response without decoding the whole document. */
@Serializable
data class PolicyVersionProbe(val version: Long)

@Serializable
data class UsageEventDto(
    val eventId: String,          // client ULID/UUID — server idempotency key
    val packageName: String,
    val startTs: Long,
    val endTs: Long,
    val foregroundMs: Long,       // monotonic; wall-clock-tamper resistant
    val localDay: String,         // device-timezone day "yyyy-MM-dd"
    val bootId: String,
)

@Serializable
data class UsageBatchDto(val schemaVersion: Int, val events: List<UsageEventDto>)

@Serializable
data class UsageAckDto(val accepted: List<String>)
