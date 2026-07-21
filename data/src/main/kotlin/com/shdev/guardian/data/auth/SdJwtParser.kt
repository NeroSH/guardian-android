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

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Claims decoded for display. Not authenticated — see [SdJwtParser]. */
data class VerifiedUserInfo(
    val email: String?,
    val displayName: String?,
)

/**
 * Decodes an SD-JWT's issuer payload and disclosures for DISPLAY ONLY.
 *
 * Performs NO signature, issuer, key-binding, digest or nonce verification. Everything it returns is
 * attacker-controllable and MUST NOT drive a trust decision — account creation, login and
 * authorization all happen server-side against the raw response JSON (see [VerifiedEmailApi], whose
 * KDoc lists the checks the backend owes). This exists so the UI can say "Signed in as Jane Doe"
 * without a round trip; nothing more.
 */
object SdJwtParser {

    /**
     * @param responseJson the raw `DigitalCredential.credentialJson`.
     * @return best-effort claims, or null if the shape isn't what we expect. Callers must treat null
     * as "no name to show", never as an authentication failure — that verdict is the server's.
     */
    fun parseForDisplay(responseJson: String): VerifiedUserInfo? = runCatching {
        // { "vp_token": { "<dcql id>": [ "<issuer-jwt>~<disclosure>~...~<kb-jwt>" ] } }
        val vpToken = JSONObject(responseJson).getJSONObject("vp_token")
        val credentialId = vpToken.keys().next()
        val rawSdJwt = vpToken.getJSONArray(credentialId).getString(0)

        val parts = rawSdJwt.split("~")
        val issuerPayload = decodeJwtPayload(parts.first())

        // Selectively-disclosed claims arrive as separate ["salt", "name", value] arrays, not in the
        // JWT payload, so merge them over the top.
        val claims = JSONObject(issuerPayload.toString())
        parts.drop(1)
            .filter { it.isNotBlank() }
            .forEach { disclosure ->
                runCatching {
                    val decoded = JSONArray(String(decodeBase64Url(disclosure), Charsets.UTF_8))
                    if (decoded.length() >= 3) claims.put(decoded.getString(1), decoded.get(2))
                }
            }

        val email = claims.optString("email").takeIf { it.isNotBlank() }
        VerifiedUserInfo(
            email = email,
            displayName = claims.optString("name").takeIf { it.isNotBlank() } ?: email,
        )
    }.getOrNull()

    private fun decodeJwtPayload(jwt: String): JSONObject {
        val payload = jwt.split(".").getOrNull(1) ?: return JSONObject()
        return JSONObject(String(decodeBase64Url(payload), Charsets.UTF_8))
    }

    private fun decodeBase64Url(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}