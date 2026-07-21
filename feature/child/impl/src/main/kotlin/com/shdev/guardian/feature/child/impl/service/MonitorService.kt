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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shdev.guardian.data.db.PolicyCacheDao
import com.shdev.guardian.feature.child.impl.overlay.BlockOverlay
import com.shdev.guardian.feature.child.impl.work.WatchdogScheduler
import com.shdev.guardian.policy.Decision
import com.shdev.guardian.policy.EvalContext
import com.shdev.guardian.policy.PolicyEvaluator
import com.shdev.guardian.policy.PolicySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds

/**
 * Always-on foreground service. Runs the 1 Hz enforcement tick: detect foreground app, load the
 * cached policy, evaluate, act (hide/warn/block). WorkManager is never on this path — it is
 * resurrection-only. The tick is the enforcement cadence.
 *
 * All state is re-hydrated from Room/DataStore on start; the Intent is null on a sticky restart.
 */
class MonitorService : Service() {

    private val policyCacheDao: PolicyCacheDao by inject()
    private val usageStatsManager: UsageStatsManager by inject()
    private val evaluator: PolicyEvaluator by inject()
    private val overlay: BlockOverlay by inject()
    private val snapshotStore: PolicySnapshotStore by inject()
    private val heartbeat: HeartbeatStore by inject()

    private lateinit var collector: UsageCollector
    private lateinit var detector: ForegroundAppDetector
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    // Orchestration state lives HERE, not in the pure evaluator.
    private var lastWarnedPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        collector = UsageCollector(
            usageStatsManager,
            snapshotStore.sessionDao,
            snapshotStore.cursorDao,
            snapshotStore.bootId
        )
        detector = ForegroundAppDetector(usageStatsManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WatchdogScheduler.ensureScheduled(applicationContext) // re-arm on every (re)start
        if (tickJob?.isActive != true) tickJob = scope.launch { tickLoop() }
        return START_STICKY // intent may be null on sticky restart — never read state from it
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            runCatching { tickOnce() }
            heartbeat.beat(Uptime.elapsed())
            delay(TICK_MS.milliseconds)
        }
    }

    private suspend fun tickOnce() {
        val now = System.currentTimeMillis()
        collector.ingest(now)

        val snapshot = loadSnapshot()
        val pkg = detector.current(now)
        val day = collector.todayKey(now)

        val liveElapsed = pkg?.let { collector.liveElapsedFor(it, now) } ?: 0L
        val used = pkg?.let { liveElapsed + snapshotStore.sessionDao.totalForApp(it, day) } ?: 0L

        // Category usage = summed persisted usage of every app in the same category today, plus the
        // foreground app's live (not-yet-persisted) session (the foreground app is in this category).
        val category = pkg?.let { snapshot.categories[it] }
        val categoryUsed = if (category != null) {
            val pkgs = snapshot.packagesInCategory(category)
            snapshotStore.sessionDao.totalForPackages(pkgs, day) + liveElapsed
        } else {
            0L
        }

        val zoned = Instant.ofEpochMilli(now).atZone(ZONE)
        val ctx = EvalContext(
            foregroundPkg = pkg,
            nowMillis = now,
            localMinuteOfDay = zoned.hour * 60 + zoned.minute,
            dayOfWeekBit = zoned.dayOfWeek.value - 1, // Monday=1 -> bit0
            category = category,
            usedTodayMs = used,
            categoryUsedTodayMs = categoryUsed,
            snapshot = snapshot,
        )
        when (val decision = evaluator.evaluate(ctx)) {
            is Decision.Allow -> if (overlay.targetPkg == pkg) {
                hideOverlay()
                lastWarnedPkg = null
            }

            is Decision.Warn -> if (pkg != lastWarnedPkg) {
                lastWarnedPkg = pkg
                notifyWarn(decision.remainingMs)
            }

            is Decision.Block -> if (!overlay.isOverlayShown) {
                // WindowManager view ops must touch the main thread; the tick runs on Default.
                withContext(Dispatchers.Main) {
                    overlay.goHome()
                    overlay.show(decision.reason, pkg)
                }
            }

            else -> return
        }
    }

    private suspend fun hideOverlay() = withContext(Dispatchers.Main) {
        overlay.hide()
    }

    private suspend fun loadSnapshot(): PolicySnapshot =
        policyCacheDao.get()?.let { snapshotStore.decode(it.json) } ?: PolicySnapshot.EMPTY

    private fun startAsForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Supervision",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian is protecting this device")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notifyWarn(remainingMs: Long) {
        val mins = (remainingMs / 60_000L).coerceAtLeast(1)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Almost out of time")
            .setContentText("About $mins min left")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        getSystemService(NotificationManager::class.java).notify(WARN_NOTIF_ID, n)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away — schedule an immediate restart, then let START_STICKY do its part.
        WatchdogScheduler.scheduleImmediateRestart(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "guardian_supervision"
        private const val NOTIF_ID = 1001
        private const val WARN_NOTIF_ID = 1002
        private const val TICK_MS = 1_000L
        private val ZONE: ZoneId = ZoneId.systemDefault()

        fun intent(ctx: Context) = Intent(ctx, MonitorService::class.java)
    }
}
