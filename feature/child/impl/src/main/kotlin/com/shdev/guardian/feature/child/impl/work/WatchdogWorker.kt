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
package com.shdev.guardian.feature.child.impl.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shdev.guardian.feature.child.impl.receiver.WatchdogAlarmReceiver
import com.shdev.guardian.feature.child.impl.service.HeartbeatStore
import com.shdev.guardian.feature.child.impl.service.MonitorService
import com.shdev.guardian.feature.child.impl.service.Uptime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Resurrection-only. If the heartbeat is stale, the FGS is a corpse — restart it. Legal to start an
 * FGS from the background here because the app holds SYSTEM_ALERT_WINDOW + battery exemption (both
 * documented exemptions to the Android 12+ background-FGS-start restriction). WorkManager's 15-min
 * floor is acceptable precisely because enforcement runs at 1 Hz inside the service, not here.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val heartbeat: HeartbeatStore by inject()

    override suspend fun doWork(): Result {
        val stale = Uptime.elapsed() - heartbeat.last() > STALE_MS
        if (stale) ContextCompat.startForegroundService(
            applicationContext,
            MonitorService.intent(applicationContext)
        )
        WatchdogScheduler.ensureAlarm(applicationContext) // self-rescheduling chain, Doze-safe
        return Result.success()
    }

    private companion object {
        const val STALE_MS = 90_000L
    }
}

/** Schedules the periodic WorkManager watchdog + the exact-alarm belt-and-braces channel. */
object WatchdogScheduler {
    private const val PERIODIC_NAME = "guardian_watchdog"
    private const val ALARM_REQ = 4711
    private const val ALARM_INTERVAL_MS = 15 * 60_000L

    fun ensureScheduled(context: Context) {
        val work = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        ensureAlarm(context)
    }

    /** Independent of WorkManager's scheduler (which OEMs sometimes wedge). Self-reschedules on fire. */
    fun ensureAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS
        runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }.onFailure {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pi
            ) // inexact fallback
        }
    }

    fun scheduleImmediateRestart(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1_000L,
            alarmIntent(context),
        )
    }

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, WatchdogAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, ALARM_REQ, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
