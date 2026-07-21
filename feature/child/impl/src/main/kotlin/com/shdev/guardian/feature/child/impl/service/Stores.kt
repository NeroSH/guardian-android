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
package com.shdev.guardian.feature.child.impl.service

import com.shdev.guardian.data.db.SessionDao
import com.shdev.guardian.data.db.UsageCursorDao
import com.shdev.guardian.policy.PolicySnapshot
import kotlinx.serialization.json.Json

/**
 * Bridges the Room layer + serialization to the service. Also owns the per-boot [bootId] and the
 * package->category lookup (synced from backend; simplified here to an in-memory map).
 */
class PolicySnapshotStore(
    val sessionDao: SessionDao,
    val cursorDao: UsageCursorDao,
    val bootId: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    @Volatile
    private var categoryTable: Map<String, String> = emptyMap()

    fun decode(raw: String): PolicySnapshot =
        json.decodeFromString(PolicySnapshotDto.serializer(), raw).toDomain()

    fun updateCategories(table: Map<String, String>) {
        categoryTable = table
    }

    fun categoryOf(pkg: String): String? = categoryTable[pkg]
}

/** Heartbeat written by the tick; the watchdog reads it to decide whether the service is a corpse. */
interface HeartbeatStore {
    suspend fun beat(elapsedRealtime: Long)
    fun last(): Long
}
