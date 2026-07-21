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
package com.shdev.guardian.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class PairingCodeResponse(val code: String, val expiresAt: String)

/**
 * Parent-side auth + pairing-code minting, split across two Ktor clients on purpose.
 *
 * [authHttp] is the PLAIN client (no Auth plugin): register/login mint a session, so they must not
 * ride the bearer plugin — a 401 there is bad credentials, and letting it reach the refresh path
 * would recurse.
 *
 * [parentHttp] carries the Auth plugin: it attaches the parent access token and, on 401, refreshes
 * and retries transparently. Authenticated calls take no token argument — passing one by hand is
 * exactly what used to bypass refresh and log the parent out on every expiry.
 */
class ParentApi(
    private val authHttp: HttpClient,
    private val parentHttp: HttpClient,
    private val baseUrl: String,
) {
    suspend fun register(email: String, password: String): TokenResponse =
        authHttp.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password))
        }.body()

    suspend fun login(email: String, password: String): TokenResponse =
        authHttp.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body()

    suspend fun createPairingCode(): PairingCodeResponse =
        parentHttp.post("$baseUrl/pairing/codes").body()
}
