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
package com.shdev.guardian.data.sync

import com.shdev.guardian.data.auth.TokenStore
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies a policy document's signature against the public key pinned at pairing (TOFU). Fail-closed:
 * a missing/invalid signature or missing pinned key means "do not apply", so the child keeps its
 * last-known-good policy rather than trusting an unauthenticated document.
 */
class PolicyVerifier(private val tokenStore: TokenStore) {

    suspend fun verify(body: String, signatureB64: String?): Boolean {
        if (signatureB64.isNullOrBlank()) return false
        val pinned = tokenStore.serverPublicKey() ?: return false
        return try {
            val keyBytes = Base64.getDecoder().decode(pinned)
            val publicKey =
                KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance("SHA256withRSA").run {
                initVerify(publicKey)
                update(body.toByteArray())
                verify(Base64.getDecoder().decode(signatureB64))
            }
        } catch (_: Exception) {
            false
        }
    }
}
