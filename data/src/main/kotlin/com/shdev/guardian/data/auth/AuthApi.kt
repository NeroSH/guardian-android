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
data class RegisterDeviceRequest(
    val pairingCode: String,
    val model: String? = null,
    val oem: String? = null,
    val fcmToken: String? = null,
)

@Serializable
data class DeviceTokenResponse(
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String,
    val serverPublicKey: String,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String)

/**
 * Talks to the pairing/auth endpoints. IMPORTANT: constructed with a PLAIN HttpClient (no Auth
 * plugin) so redeem/refresh calls never recurse into the token-refresh path.
 */
class AuthApi(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    suspend fun redeemPairingCode(req: RegisterDeviceRequest): DeviceTokenResponse =
        http.post("$baseUrl/devices/register") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun refresh(refreshToken: String): TokenResponse =
        http.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }.body()
}
