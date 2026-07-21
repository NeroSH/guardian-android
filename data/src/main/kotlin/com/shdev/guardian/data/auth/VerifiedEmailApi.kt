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

/** Server-issued challenge. [nonce] is single-use and short-lived; the server owns and consumes it. */
@Serializable
data class ChallengeResponse(val nonce: String, val expiresAt: String? = null)

/** The untouched Credential Manager response, plus the nonce it was requested with. */
@Serializable
data class VerifiedEmailRequest(val responseJson: String, val nonce: String)

/**
 * OTP-less parent sign-in via a cryptographically verified email credential (OpenID4VP / SD-JWT VC).
 *
 * Uses the PLAIN client — this flow mints a session, so it must never ride the parent Auth plugin.
 *
 * The nonce is deliberately **server-issued** rather than generated on device: replay protection
 * works by the server comparing the nonce inside the presentation's key-binding JWT against a value
 * it issued and has not yet consumed. A client-generated nonce would have the server checking a
 * number the caller chose, which proves nothing.
 *
 * TODO(server): neither endpoint exists yet. `POST /auth/verified-email` MUST, before minting any
 * session, verify all of the following against the raw [VerifiedEmailRequest.responseJson]:
 *  1. `iss == "https://verifiablecredentials-pa.googleapis.com"`.
 *  2. Issuer-JWT signature against the JWKS at
 *     `https://verifiablecredentials-pa.googleapis.com/.well-known/vc-public-jwks` (cache per HTTP
 *     cache headers; re-fetch on an unknown `kid`).
 *  3. `vct == "UserInfoCredential"`.
 *  4. Every disclosure's digest is present in the issuer JWT's `_sd` array — otherwise claims can be
 *     injected by appending disclosures.
 *  5. Key-binding JWT signature against the public key in the issuer JWT's `cnf.jwk`. This is what
 *     proves the presenter holds the device the credential was issued to.
 *  6. The key-binding JWT's `nonce` equals the nonce issued for THIS challenge.
 *  7. That nonce is unexpired (~5 min) and consumed ATOMICALLY (delete-on-use / compare-and-set) —
 *     a non-atomic check lets two concurrent requests both pass.
 *  8. `aud` / `client_id` matches this app's registered origin.
 *  9. `exp` / `iat` within tolerance.
 * 10. `email_verified == true` — a credential can carry an unverified address.
 * 11. `hd` empty ⇒ consumer Google account. Google does not issue these for Workspace accounts, so a
 *     non-empty `hd` is unexpected today: reject or flag rather than trust.
 * 12. Non-`@gmail.com` addresses carry NO freshness claim (Google verified them at account creation
 *     only, and the address may since have changed hands) — require an additional challenge such as
 *     an email OTP for those. OTP-less is only safe for `@gmail.com`.
 *
 * Use a maintained SD-JWT VC library server-side rather than hand-rolling checks 4 and 5; hand-rolled
 * implementations of those tend to fail open.
 */
class VerifiedEmailApi(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    suspend fun challenge(): ChallengeResponse =
        http.post("$baseUrl/auth/verified-email/challenge").body()

    /**
     * Exchange a verified-email presentation for a parent session. [responseJson] must be forwarded
     * byte-for-byte as Credential Manager returned it — re-serializing it would break the signature.
     */
    suspend fun authenticate(responseJson: String, nonce: String): TokenResponse =
        http.post("$baseUrl/auth/verified-email") {
            contentType(ContentType.Application.Json)
            setBody(VerifiedEmailRequest(responseJson, nonce))
        }.body()
}
