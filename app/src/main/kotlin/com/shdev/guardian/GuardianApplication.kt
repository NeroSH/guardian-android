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
package com.shdev.guardian

import android.app.Application
import android.os.Trace
import com.shdev.guardian.data.di.dataModule
import com.shdev.guardian.data.di.loadHeavyDataDependencies
import com.shdev.guardian.di.allModules
import com.shdev.guardian.feature.child.impl.di.childModule
import com.shdev.guardian.feature.child.impl.fcm.FirebaseInitializer
import com.shdev.guardian.feature.parent.impl.di.parentModule
import io.kotzilla.generated.monitoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

/**
 * App entry point. Assembles Koin modules (child module here; parent/core modules join in a fuller
 * build) and installs the Koin WorkManager factory so workers can @inject dependencies.
 */
class GuardianApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Trace startKoin so the cold-startup gap can be attributed (Koin DI-level data alone doesn't
        // explain the pre-first-frame main-thread time — see systrace section "GuardianApp.onCreate").
        try {
            Trace.beginSection("GuardianApp.onCreate")
            startKoin {
                androidContext(this@GuardianApplication)
                workManagerFactory()
                modules(allModules, dataModule, parentModule, childModule)
                monitoring()
            }
        } finally {
            Trace.endSection()
        }

        appScope.launch {
            loadHeavyDataDependencies()

            // Cold-start Firebase decision. FirebaseInitProvider is removed from the merged manifest, so
            // nothing initializes Firebase implicitly — this is the only path for an already-paired device.
            //
            // Synchronous on purpose: FCM can deliver to GuardianFcmService before any Activity exists, and
            // a message arriving into a process with no FirebaseApp is dropped. The cost is one
            // SharedPreferences boolean, and initializeApp() only runs when the device is already paired —
            // an unpaired install does no Firebase work here at all, it initializes at the pairing screen.
            val firebaseInitializer: FirebaseInitializer = getKoin().get()
            firebaseInitializer.initializeIfPaired()
        }
        // Enforcement + periodic sync are armed by ChildRuntimeLauncher after the child completes
        // onboarding (and re-armed on boot via BootReceiver on child devices only) — never here, so a
        // parent device stays inert.
    }
}
