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
package com.shdev.guardian.data.di

import android.content.ComponentCallbacks
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.shdev.guardian.data.BuildConfig.BASE_URL
import com.shdev.guardian.data.account.ParentAccountRepository
import com.shdev.guardian.data.alerts.AlertsApi
import com.shdev.guardian.data.auth.AuthApi
import com.shdev.guardian.data.auth.DeviceApi
import com.shdev.guardian.data.auth.DeviceSessionManager
import com.shdev.guardian.data.auth.PairingRepository
import com.shdev.guardian.data.auth.ParentApi
import com.shdev.guardian.data.auth.ParentDevicesApi
import com.shdev.guardian.data.auth.ParentSessionManager
import com.shdev.guardian.data.auth.ParentSessionStore
import com.shdev.guardian.data.auth.TokenStore
import com.shdev.guardian.data.auth.UnauthorizedException
import com.shdev.guardian.data.auth.VerifiedEmailApi
import com.shdev.guardian.data.config.RoleStore
import com.shdev.guardian.data.crypto.CryptoDataStore
import com.shdev.guardian.data.crypto.CryptoDataStoreIml
import com.shdev.guardian.data.db.GuardianDatabase
import com.shdev.guardian.data.firebase.FirebaseStateStore
import com.shdev.guardian.data.rules.ParentRulesApi
import com.shdev.guardian.data.sync.PolicyVerifier
import com.shdev.guardian.data.sync.SyncEngine
import com.shdev.guardian.data.sync.SyncEngineImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Cross-cutting infrastructure wiring assembled by :app (the composition root): auth/token storage,
 * the Ktor HttpClients, and the sync engine. Feature-owned wiring lives in :feature:child:impl
 * (childFeatureModule — pairing + enforcement runtime) and :feature:parent:impl (parentModule).
 */
val dataModule = module {
    single<GuardianDatabase> {
        val appContext = androidContext().applicationContext
        val dbFile = appContext.getDatabasePath(GuardianDatabase.NAME)

        Room.databaseBuilder<GuardianDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    single { get<GuardianDatabase>().sessionDao() }
    single { get<GuardianDatabase>().usageCursorDao() }
    single { get<GuardianDatabase>().policyCacheDao() }
    single { get<GuardianDatabase>().outboxDao() }
    single { get<GuardianDatabase>().clockAnchorDao() }

    single<CryptoDataStore> { CryptoDataStoreIml(androidContext()) }
    single<SyncEngine> {
        SyncEngineImpl(
            http = get(),
            baseUrl = BASE_URL,
            policyCacheDao = get(),
            sessionDao = get(),
            outboxDao = get(),
            clockAnchorDao = get(),
            bootId = get(named("bootId")),
            verifier = get(),
        )
    }
    single { RoleStore(androidContext(), get()) }

    // --- Auth / pairing ---
    single { TokenStore(androidContext(), get()) }

    // Device-side session teardown (mirrors ParentSessionManager): clears the encrypted tokens, the
    // Firebase state mirror, and revokes the FCM token so an unpaired device stops being addressable.
    single { DeviceSessionManager(androidContext(), get(), get()) }

    // --- Firebase lifecycle ---
    // Nothing initializes Firebase implicitly (FirebaseInitProvider is removed from the manifest):
    // GuardianApplication initializes on cold start when already paired, and the child pairing flow
    // initializes just before the QR screen. FcmTokenSyncer gates token upload on pairing state.
    single { FirebaseStateStore(androidContext(), get()) }

    // Plain client (NO Auth plugin) for pairing, refresh, login/register and the verified-email
    // exchange — calls that mint a session and so must never recurse into the refresh path.
    //
    // Deliberately has NO 401 validator. A 401 here is an ordinary failure (bad credentials, an
    // expired pairing code) that the calling ViewModel surfaces itself. It previously cleared the
    // parent session, which is what logged parents out on every routine access-token expiry.
    single(named("authClient")) {
        HttpClient(OkHttp) {
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.ALL
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }

            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
    single { AuthApi(authClient, BASE_URL) }
    single { PairingRepository(get(), get(), get()) }

    // Parent session storage + lifecycle
    single { ParentSessionStore(androidContext(), get()) }
    single { ParentSessionManager(get(), get()) }

    // Parent client: attaches the parent bearer token and transparently refreshes+rotates on 401.
    // Mirrors the device client below, but reads ParentSessionStore instead of TokenStore.
    single(named("parentClient")) {
        val session: ParentSessionStore = get()
        val authApi: AuthApi = get()
        val sessionManager: ParentSessionManager = get()
        HttpClient(OkHttp) {
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.ALL
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = session.accessToken() ?: return@loadTokens null
                        BearerTokens(access, session.refreshToken() ?: "")
                    }
                    // Ktor calls this on a 401 and retries the request once with the new pair. Only a
                    // FAILED refresh ends the session — the 401 itself never does.
                    refreshTokens {
                        val refresh = session.refreshToken() ?: run {
                            sessionManager.onRefreshFailed()
                            return@refreshTokens null
                        }
                        runCatching { authApi.refresh(refresh) }.fold(
                            onSuccess = { fresh ->
                                session.updateTokens(fresh.accessToken, fresh.refreshToken)
                                BearerTokens(fresh.accessToken, fresh.refreshToken)
                            },
                            onFailure = {
                                sessionManager.onRefreshFailed()
                                null
                            },
                        )
                    }
                    sendWithoutRequest { true }
                }
            }

            // Reached only after the Auth plugin already retried with a refreshed token.
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) throw UnauthorizedException()
                }
            }
        }
    }

    // Parent-side auth + pairing-code minting. ParentApi straddles both clients: register/login on
    // the plain one, authenticated calls on the parent one.
    single { ParentApi(authClient, parentClient, BASE_URL) }
    single { ParentRulesApi(parentClient, BASE_URL) }
    single { AlertsApi(parentClient, BASE_URL) }
    single { ParentDevicesApi(parentClient, BASE_URL) }
    single { ParentAccountRepository(get(), get(), get()) }

    // Verified email (OTP-less sign-in) — plain client: this flow mints a session.
    single { VerifiedEmailApi(authClient, BASE_URL) }

    // Main client: attaches the device bearer token and transparently refreshes+rotates on 401.
    single {
        val tokenStore: TokenStore = get()
        val authApi: AuthApi = get()
        HttpClient(OkHttp) {
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.ALL
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Auth) {
                bearer {
                    loadTokens {
                        val access = tokenStore.accessToken() ?: return@loadTokens null
                        BearerTokens(access, tokenStore.refreshToken() ?: "")
                    }
                    refreshTokens {
                        val refresh = tokenStore.refreshToken() ?: return@refreshTokens null
                        runCatching { authApi.refresh(refresh) }.getOrNull()?.let { fresh ->
                            tokenStore.updateTokens(fresh.accessToken, fresh.refreshToken)
                            BearerTokens(fresh.accessToken, fresh.refreshToken)
                        }
                    }
                    sendWithoutRequest { true }
                }
            }

            // Reached only after the Auth plugin already retried with a refreshed token, so the
            // backend has genuinely revoked or forgotten this device. Tear the whole device session
            // down — auth tokens, the Firebase mirror, and the FCM registration token — rather than
            // just the tokens, which used to leave the device half-unpaired.
            val deviceSession: DeviceSessionManager = get()
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        deviceSession.onDeviceUnpaired()
                        throw UnauthorizedException()
                    }
                }
            }
        }
    }

    // Device-authenticated calls (main client attaches the bearer token)
    single { DeviceApi(get(), BASE_URL) }

    single { PolicyVerifier(get()) }
}

private val Scope.authClient: HttpClient
    get() = get(named("authClient"))

private val Scope.parentClient: HttpClient
    get() = get(named("parentClient"))

fun ComponentCallbacks.loadHeavyDataDependencies() {
    getKoin().get<HttpClient>(named("authClient"))
}