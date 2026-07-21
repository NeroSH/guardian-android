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
package com.shdev.guardian.feature.child.impl.di

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shdev.guardian.data.config.RoleStore
import com.shdev.guardian.feature.child.impl.ChildPairingViewModel
import com.shdev.guardian.feature.child.impl.PairingCoordinator
import com.shdev.guardian.feature.child.impl.fcm.FcmTokenSyncer
import com.shdev.guardian.feature.child.impl.fcm.FirebaseInitializer
import com.shdev.guardian.feature.child.impl.onboarding.ChildRuntimeLauncher
import com.shdev.guardian.feature.child.impl.overlay.BlockOverlay
import com.shdev.guardian.feature.child.impl.service.HeartbeatStore
import com.shdev.guardian.feature.child.impl.service.PolicySnapshotStore
import com.shdev.guardian.feature.child.impl.service.TamperReporter
import com.shdev.guardian.policy.PolicyEvaluator
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private val Context.hbStore by preferencesDataStore("heartbeat")
private val HB_KEY = longPreferencesKey("last_beat")

/**
 * Child-feature wiring: the pairing presentation layer plus the always-on enforcement runtime
 * (foreground service, overlay, usage/tamper stores, resurrection scheduling). Data-layer singletons
 * the runtime resolves (DAOs, SyncEngine, RoleStore, PairingRepository) are provided by :app's module;
 * Koin's global graph binds them at startup.
 */
val childModule = module {
    // --- pairing (presentation) ---
    singleOf(::PairingCoordinator)
    factoryOf(::ChildPairingViewModel)

    // --- enforcement runtime ---
    single { androidContext().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }
    single { PolicyEvaluator() }
    single { BlockOverlay(androidContext()) }
    single { ChildRuntimeLauncher(inject<RoleStore>()) }

    // FCM
    single { FcmTokenSyncer(get(), get(), get()) }
    single { FirebaseInitializer(androidContext(), get()) }

    // One bootId per process lifetime.
    single(qualifier = named("bootId")) { UUID.randomUUID().toString() }

    single { PolicySnapshotStore(get(), get(), get(named("bootId"))) }

    single<HeartbeatStore> {
        object : HeartbeatStore {
            private val ctx = androidContext()
            private val cache = AtomicLong(0)
            override suspend fun beat(elapsedRealtime: Long) {
                cache.set(elapsedRealtime)
                ctx.hbStore.edit { it[HB_KEY] = elapsedRealtime }
            }

            override fun last(): Long = cache.get()
        }
    }

    single { TamperReporter(get(), get()) }
}
