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

/**
 * Owns all network<->cache movement. Enforcement never calls this; only workers do.
 *
 *  - [pullPolicy]: fetch authoritative snapshot over TLS, apply to Room in one transaction, but
 *    REJECT any snapshot whose version <= cached (replay / stale-CDN protection). Re-anchors the
 *    monotonic clock (serverTime vs elapsedRealtime) on success.
 *  - [drainOutbox]: upload unuploaded sessions + tamper events. At-least-once with idempotent dedup
 *    (client ULID/UUID event ids -> server upsert), so a retried batch after a lost 2xx is a no-op.
 */
interface SyncEngine {
    suspend fun pullPolicy()
    suspend fun drainOutbox()
}
