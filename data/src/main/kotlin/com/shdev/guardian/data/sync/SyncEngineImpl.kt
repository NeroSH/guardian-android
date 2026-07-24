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

import android.os.SystemClock
import com.shdev.guardian.data.db.ClockAnchorDao
import com.shdev.guardian.data.db.ClockAnchorEntity
import com.shdev.guardian.data.db.OutboxDao
import com.shdev.guardian.data.db.PolicyCacheDao
import com.shdev.guardian.data.db.PolicyCacheEntity
import com.shdev.guardian.data.db.SessionDao
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.query
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Concrete sync. Enforcement never touches this — only SyncWorker does.
 *
 * pullPolicy: GET the authoritative snapshot; the server 304s when nothing is newer than our cached
 * version. On a 200, we still gate locally (version <= cached => reject) as replay/stale-CDN defense,
 * then persist the RAW json (the enforcement side decodes it) and re-anchor the monotonic clock.
 *
 * drainOutbox: upload unuploaded sessions (idempotent by eventId; server upserts) then mark them.
 * A retried batch after a lost 2xx is a no-op server-side.
 */
internal class SyncEngineImpl(
    private val http: HttpClient,
    private val baseUrl: String,
    private val policyCacheDao: PolicyCacheDao,
    private val sessionDao: SessionDao,
    private val outboxDao: OutboxDao,
    private val clockAnchorDao: ClockAnchorDao,
    private val bootId: String,
    private val verifier: PolicyVerifier,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SyncEngine {

    override suspend fun pullPolicy() {
        val cachedVersion = policyCacheDao.version() ?: 0L
        val resp: HttpResponse = http.query("$baseUrl/sync/policy") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToJsonElement(mapOf("sinceVersion" to cachedVersion)))
        }
        if (resp.status == HttpStatusCode.NotModified) return

        val raw = resp.bodyAsText()

        // Fail-closed authenticity check: an unsigned/tampered document is never applied.
        if (!verifier.verify(raw, resp.headers[POLICY_SIGNATURE_HEADER])) return

        val incomingVersion = json.decodeFromString(PolicyVersionProbe.serializer(), raw).version
        if (incomingVersion <= cachedVersion) return // fail-closed: keep last-known-good

        policyCacheDao.put(PolicyCacheEntity(version = incomingVersion, json = raw))
        reanchorClock(resp)
    }

    override suspend fun drainOutbox() {
        val sessions = sessionDao.unuploaded(BATCH)
        if (sessions.isNotEmpty()) {
            val batch = UsageBatchDto(
                schemaVersion = SCHEMA_VERSION,
                events = sessions.map {
                    UsageEventDto(
                        eventId = it.eventId,
                        packageName = it.packageName,
                        startTs = it.startTs,
                        endTs = it.endTs,
                        foregroundMs = it.foregroundMs,
                        localDay = it.day,
                        bootId = it.bootId
                    )
                },
            )
            val ack: UsageAckDto = http.post("$baseUrl/sync/usage") {
                contentType(ContentType.Application.Json)
                setBody(batch)
            }.body()
            val acceptedIds = ack.accepted.toSet()
            sessionDao.markUploaded(sessions.filter { it.eventId in acceptedIds }
                .map { it.localId })
        }
        drainTamperEvents()
    }

    private suspend fun drainTamperEvents() {
        val now = System.currentTimeMillis()
        for (row in outboxDao.due(now)) {
            val ok = runCatching {
                http.post("$baseUrl/sync/event") {
                    contentType(ContentType.Application.Json)
                    setBody(row.payloadJson)
                }.status.isSuccess()
            }.getOrDefault(false)
            if (ok) outboxDao.ack(row.id)
            else outboxDao.backoff(row.id, now + backoff(row.attempts))
        }
    }

    /** Capture (serverTime, elapsedRealtime) so clock-tamper checks have a monotonic reference. */
    private suspend fun reanchorClock(resp: HttpResponse) {
        val serverTime = resp.headers["X-Server-Time"]?.toLongOrNull() ?: System.currentTimeMillis()
        clockAnchorDao.set(
            ClockAnchorEntity(
                serverTimeMs = serverTime,
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                bootId = bootId,
            ),
        )
    }

    private fun backoff(attempts: Int): Long =
        (BASE_BACKOFF_MS * (1L shl attempts.coerceAtMost(6))).coerceAtMost(MAX_BACKOFF_MS)

    private fun HttpStatusCode.isSuccess() = value in 200..299

    private companion object {
        const val BATCH = 500
        const val SCHEMA_VERSION = 1
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 6 * 60 * 60_000L
        const val POLICY_SIGNATURE_HEADER = "X-Policy-Signature"
    }
}
