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

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.SystemClock
import com.shdev.guardian.data.db.SessionDao
import com.shdev.guardian.data.db.SessionEntity
import com.shdev.guardian.data.db.UsageCursorDao
import com.shdev.guardian.data.db.UsageCursorEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Builds foreground sessions from UsageStatsManager EVENTS (not aggregate buckets), so re-processing
 * a window never inflates totals. Each poll walks a sliding window from the last processed timestamp.
 *
 * ACTIVITY_RESUMED (1) opens a session; ACTIVITY_PAUSED (2) closes it. We persist the last processed
 * event timestamp so nothing is counted twice across polls.
 */
class UsageCollector(
    private val usageStatsManager: UsageStatsManager,
    private val sessionDao: SessionDao,
    private val cursorDao: UsageCursorDao,
    private val bootId: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)

    // package -> wall-clock start of the currently-open resume, awaiting a pause.
    private val open = HashMap<String, Long>()

    /** Ingest new events since the cursor. Idempotent: safe to call every tick. */
    suspend fun ingest(nowWall: Long) {
        val from = cursorDao.last() ?: (nowWall - DEFAULT_LOOKBACK_MS)
        // TODO: Add permission checker Manifest.permission.PACKAGE_USAGE_STATS
        val events = usageStatsManager.queryEvents(from, nowWall)
        val e = UsageEvents.Event()
        var lastTs = from

        while (events.getNextEvent(e)) {
            lastTs = maxOf(lastTs, e.timeStamp)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    open[e.packageName] = e.timeStamp

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = open.remove(e.packageName) ?: continue
                    if (e.timeStamp > start) persist(e.packageName, start, e.timeStamp)
                }
            }
        }
        cursorDao.set(UsageCursorEntity(lastProcessedTs = lastTs))
    }

    private suspend fun persist(pkg: String, start: Long, end: Long) {
        sessionDao.insert(
            SessionEntity(
                eventId = UUID.randomUUID().toString(),
                packageName = pkg,
                startTs = start,
                endTs = end,
                day = dayFmt.format(Instant.ofEpochMilli(start)),
                foregroundMs = end - start,
                bootId = bootId,
            ),
        )
    }

    /**
     * Live elapsed for a package whose session is still open (not yet paused), so a limit trips within
     * ~1s of being crossed rather than at the next pause boundary. Uses wall clock for the delta.
     */
    fun liveElapsedFor(pkg: String, nowWall: Long): Long =
        open[pkg]?.let { (nowWall - it).coerceAtLeast(0L) } ?: 0L

    fun todayKey(nowWall: Long): String = dayFmt.format(Instant.ofEpochMilli(nowWall))

    companion object {
        private const val DEFAULT_LOOKBACK_MS = 60_000L
    }
}

/**
 * Fast "what's in the foreground right now" probe for the 1s tick — the latest ACTIVITY_RESUMED in a
 * short window. UsageStats has minutes of latency for aggregates, but events are near-real-time.
 */
class ForegroundAppDetector(private val usageStatsManager: UsageStatsManager) {
    fun current(nowWall: Long, windowMs: Long = 10_000L): String? {
        // TODO: Add permission checker Manifest.permission.PACKAGE_USAGE_STATS
        val events = usageStatsManager.queryEvents(nowWall - windowMs, nowWall)
        val e = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTs = 0L
        while (events.getNextEvent(e)) {
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.timeStamp >= latestTs) {
                latestTs = e.timeStamp
                latestPkg = e.packageName
            }
        }
        return latestPkg
    }
}

/** Monotonic uptime helper — used by the heartbeat and clock-skew checks. */
object Uptime {
    fun elapsed(): Long = SystemClock.elapsedRealtime()
}
