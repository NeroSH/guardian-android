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
package com.shdev.guardian.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single resume->pause foreground session, built from UsageStatsManager events.
 * [day] is the LOCAL-timezone day key ("yyyy-MM-dd") so daily quotas roll over at device midnight.
 */
@Entity(
    tableName = "sessions",
    indices = [Index("day"), Index("packageName"), Index("uploaded")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    /** Client-generated ULID — the server idempotency key. A retried upload is a no-op server-side. */
    val eventId: String,
    val packageName: String,
    val startTs: Long,
    val endTs: Long,
    val day: String,
    /** Monotonic elapsed real-time for the span, immune to wall-clock tampering. */
    val foregroundMs: Long,
    /** Regenerated each boot; lets the server quarantine implausible clock jumps within a boot. */
    val bootId: String,
    val uploaded: Boolean = false,
)

/** Cursor for incremental usage ingestion: the last event timestamp we processed, per source. */
@Entity(tableName = "usage_cursor")
data class UsageCursorEntity(
    @PrimaryKey val id: String = "usage",
    val lastProcessedTs: Long,
)

/** The single cached policy row. SyncWorker is the only writer; enforcement reads this, never network. */
@Entity(tableName = "policy_cache")
data class PolicyCacheEntity(
    @PrimaryKey val id: String = "policy",
    val version: Long,
    val json: String,
)

/** Durable queue of child-originated mutations (tamper events, acks). Drained by upload workers. */
@Entity(tableName = "outbox", indices = [Index("nextAttemptAt")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // TAMPER | HEARTBEAT | ACK
    val payloadJson: String,
    val priority: Int = 0,      // 1 = expedited (tamper), 0 = normal
    val createdAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
)

/** Monotonic clock anchor captured at each successful sync, to detect wall-clock manipulation. */
@Entity(tableName = "clock_anchor")
data class ClockAnchorEntity(
    @PrimaryKey val id: String = "anchor",
    val serverTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val bootId: String,
)
