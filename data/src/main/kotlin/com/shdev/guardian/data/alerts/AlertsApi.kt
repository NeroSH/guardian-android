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
package com.shdev.guardian.data.alerts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

/** Parent-visible tamper alert (mirror of the backend AlertDto). */
@Serializable
data class AlertDto(
    val id: String,
    val deviceId: String? = null,
    val kind: String,
    val payload: String,
    val createdAt: String,
    val acknowledged: Boolean,
)

/**
 * Reads/acknowledges tamper alerts. Rides the parent client, whose Auth plugin attaches the parent
 * bearer token and — on 401 — refreshes and retries.
 */
class AlertsApi(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    suspend fun list(unacknowledgedOnly: Boolean = false): List<AlertDto> =
        http.get("$baseUrl/alerts") {
            parameter("unacknowledgedOnly", unacknowledgedOnly)
        }.body()

    suspend fun acknowledge(id: String) {
        http.post("$baseUrl/alerts/$id/ack")
    }
}
