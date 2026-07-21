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

import com.shdev.guardian.data.db.ClockAnchorDao
import com.shdev.guardian.data.db.OutboxDao
import com.shdev.guardian.data.db.OutboxEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns tamper signals (clock change, admin removal, service kill) into high-priority outbox rows so
 * the parent is alerted. Detection is the guarantee in a standard install — enforcement can be
 * circumvented, but the parent always finds out.
 */
class TamperReporter(
    private val outboxDao: OutboxDao,
    private val clockAnchorDao: ClockAnchorDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val skewThresholdMs: Long = 2 * 60_000L,
) {
    fun onClockChanged(wallNow: Long, elapsedNow: Long) = scope.launch {
        val anchor = clockAnchorDao.get() ?: return@launch
        val expectedWall = anchor.serverTimeMs + (elapsedNow - anchor.elapsedRealtimeMs)
        val skew = kotlin.math.abs(wallNow - expectedWall)
        if (skew > skewThresholdMs) enqueue("CLOCK_TAMPER", mapOf("skewMs" to skew.toString()))
    }

    fun onAdminDisableRequested() = enqueueAsync("ADMIN_DISABLE_REQUESTED", emptyMap())
    fun onAdminDisabled() = enqueueAsync("ADMIN_DISABLED", emptyMap())
    fun onServiceKilled(downtimeMs: Long, exitReason: String) =
        enqueueAsync(
            "SERVICE_DOWN",
            mapOf("downtimeMs" to downtimeMs.toString(), "exitReason" to exitReason)
        )

    private fun enqueueAsync(type: String, fields: Map<String, String>) {
        scope.launch { enqueue(type, fields) }
    }

    private suspend fun enqueue(type: String, fields: Map<String, String>) {
        val payload = JsonObject(fields.mapValues { JsonPrimitive(it.value) })
        outboxDao.enqueue(
            OutboxEntity(
                type = "TAMPER",
                payloadJson = Json.encodeToString(JsonObject.serializer(), payload)
                    .let { """{"kind":"$type","data":$it}""" },
                priority = 1, // expedited
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
