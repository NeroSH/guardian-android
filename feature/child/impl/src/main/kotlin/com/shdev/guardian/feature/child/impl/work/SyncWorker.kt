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

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shdev.guardian.data.sync.SyncEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * The ONLY writer of the policy cache. Enforcement reads the cache and never the network. Triggered
 * two ways: expedited by an FCM poke ("you're stale"), and periodically as the reconciliation
 * fallback for lost pokes. Fails closed — a stale/failed sync keeps the last-known-good policy.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val syncEngine: SyncEngine by inject()

    override suspend fun doWork(): Result = try {
        syncEngine.pullPolicy()   // version-gated apply: rejects version <= cached
        syncEngine.drainOutbox()  // idempotent upload of sessions + tamper events (ULID dedup)
        Result.success()
    } catch (_: Exception) {
        Result.retry()            // WorkManager exponential backoff; cache stays last-known-good
    }

    companion object {
        private const val PERIODIC = "guardian_sync_periodic"
        private const val EXPEDITED = "guardian_sync_poke"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Called from FCM: fetch the authoritative policy right now. */
        fun poke(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    uniqueWorkName = EXPEDITED,
                    existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                    request = req
                )
        }

        /** Reconciliation fallback for lost pokes. */
        fun ensurePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    uniqueWorkName = PERIODIC,
                    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                    request = req
                )
        }
    }
}
