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
package com.shdev.guardian.data.rules

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.query
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/** Result of a policy save: the version-race is surfaced explicitly so the UI can reconcile. */
sealed interface SaveResult {
    data class Saved(val policy: PolicyDto) : SaveResult

    /** Another parent wrote first; [server] is the current authoritative policy to reapply onto. */
    data class Conflict(val server: PolicyDto) : SaveResult
}

/**
 * Parent-side policy read/write. Rides the parent client, whose Auth plugin attaches the parent
 * bearer token and — on 401 — refreshes and retries. Parents are not device-scoped, so this is a
 * separate client from the device one, but the refresh behaviour is the same.
 */
class ParentRulesApi(
    private val http: HttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Always fetch the full document (sinceVersion = -1 forces a body even at version 0). */
    suspend fun getPolicy(): PolicyDto = http.query("$baseUrl/sync/policy") {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToJsonElement(mapOf<String, Long>("sinceVersion" to -1)))
    }.body()

    suspend fun updatePolicy(expectedVersion: Long, edit: PolicyEditDto): SaveResult {
        val resp = http.post("$baseUrl/sync/policy") {
            parameter("expectedVersion", expectedVersion)
            contentType(ContentType.Application.Json)
            setBody(edit)
        }
        val body = json.decodeFromString(PolicyDto.serializer(), resp.bodyAsText())
        return if (resp.status == HttpStatusCode.Conflict) SaveResult.Conflict(body)
        else SaveResult.Saved(body)
    }
}
