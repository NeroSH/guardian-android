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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class FcmTokenRequest(val fcmToken: String)

/**
 * Device-authenticated calls. Uses the MAIN client (with the Auth plugin) so the device bearer token
 * is attached and refreshed automatically.
 */
class DeviceApi(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    suspend fun updateFcmToken(token: String) {
        http.post("$baseUrl/devices/fcm-token") {
            contentType(ContentType.Application.Json)
            setBody(FcmTokenRequest(token))
        }
    }
}
