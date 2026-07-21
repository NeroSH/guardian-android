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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: SessionEntity)

    /** Effective persisted total today for one package (does NOT include the live open session). */
    @Query("SELECT COALESCE(SUM(foregroundMs), 0) FROM sessions WHERE packageName = :pkg AND day = :day")
    suspend fun totalForApp(pkg: String, day: String): Long

    @Query(
        """SELECT COALESCE(SUM(foregroundMs), 0) FROM sessions
           WHERE packageName IN (:pkgs) AND day = :day""",
    )
    suspend fun totalForPackages(pkgs: List<String>, day: String): Long

    @Query("SELECT * FROM sessions WHERE uploaded = 0 ORDER BY startTs LIMIT :n")
    suspend fun unuploaded(n: Int = 500): List<SessionEntity>

    @Query("UPDATE sessions SET uploaded = 1 WHERE localId IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("DELETE FROM sessions WHERE uploaded = 1 AND startTs < :cutoff")
    suspend fun gcOld(cutoff: Long)
}

@Dao
interface UsageCursorDao {
    @Query("SELECT lastProcessedTs FROM usage_cursor WHERE id = 'usage'")
    suspend fun last(): Long?

    @Upsert
    suspend fun set(cursor: UsageCursorEntity)
}

@Dao
interface PolicyCacheDao {
    @Query("SELECT * FROM policy_cache WHERE id = 'policy'")
    suspend fun get(): PolicyCacheEntity?

    @Query("SELECT version FROM policy_cache WHERE id = 'policy'")
    suspend fun version(): Long?

    @Upsert
    suspend fun put(policy: PolicyCacheEntity)
}

@Dao
interface OutboxDao {
    @Insert
    suspend fun enqueue(row: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE nextAttemptAt <= :now ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int = 50): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun ack(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1, nextAttemptAt = :next WHERE id = :id")
    suspend fun backoff(id: Long, next: Long)
}

@Dao
interface ClockAnchorDao {
    @Query("SELECT * FROM clock_anchor WHERE id = 'anchor'")
    suspend fun get(): ClockAnchorEntity?

    @Upsert
    suspend fun set(anchor: ClockAnchorEntity)
}
