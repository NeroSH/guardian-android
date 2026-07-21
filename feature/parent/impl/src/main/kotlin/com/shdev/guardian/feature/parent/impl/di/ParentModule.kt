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
package com.shdev.guardian.feature.parent.impl.di

import com.shdev.guardian.feature.parent.impl.DigitalCredentialClient
import com.shdev.guardian.feature.parent.impl.ParentAlertsViewModel
import com.shdev.guardian.feature.parent.impl.ParentPairingViewModel
import com.shdev.guardian.feature.parent.impl.ParentRulesViewModel
import com.shdev.guardian.feature.parent.impl.account.AccountSettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Presentation-layer wiring for the parent feature. The data-layer singletons the ViewModels resolve
 * (ParentApi, ParentSessionStore, ParentSessionManager, VerifiedEmailApi, ParentRulesApi, AlertsApi,
 * ParentAccountRepository) are provided by :data's module; Koin's global graph binds them together at
 * startup.
 */
val parentModule = module {
    // Injected lazily into the pairing ViewModel: CredentialManager.create() must not run on an
    // API < 28 device, which never reaches the call site.
    single { DigitalCredentialClient(androidContext()) }

    // (parentApi, session, sessionManager, verifiedEmailApi, digitalCredentialClient) — the Lazy
    // arguments keep HttpClient and CredentialManager construction off the cold-start path.
    viewModel { ParentPairingViewModel(inject(), get(), get(), inject(), inject()) }
    viewModelOf(::ParentRulesViewModel)
    viewModelOf(::ParentAlertsViewModel)
    viewModelOf(::AccountSettingsViewModel)
}
