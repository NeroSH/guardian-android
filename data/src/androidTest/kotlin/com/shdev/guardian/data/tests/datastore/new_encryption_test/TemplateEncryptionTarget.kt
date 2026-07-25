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
package com.shdev.guardian.data.tests.datastore.new_encryption_test

import android.content.Context
import androidx.datastore.core.Serializer
import com.shdev.guardian.data.tests.datastore.EncryptedDataStoreBenchmark
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload

/**
 * Copy this file, rename it after the algorithm, fill in the four members — that is the whole job.
 * Nothing runs it until a test class points at it, so it is safe to leave here half-finished.
 *
 * Checklist for a candidate to be comparable:
 *
 *  1. **[id] is permanent.** Results are merged across runs by this key, so changing it later
 *     orphans the old column instead of updating it.
 *  2. **Hold expensive state on the instance.** Key handles, `SecureRandom`, a Tink `KeysetHandle`:
 *     build them once, the way the app would. A per-call key load shows up as the algorithm being
 *     slow when it is really the setup being repeated.
 *  3. **Do not change the document format.** The serializer must round-trip [BenchmarkPayload]
 *     through the same JSON, or the columns are measuring different work. Only the bytes on disk
 *     may differ.
 *  4. **Implement [rawCodec] if there is a primitive to isolate.** Return `null` only when
 *     encryption is inseparable from serialization; the codec-only rows are simply skipped then.
 *
 * Then add the test:
 *
 * ```kotlin
 * @RunWith(AndroidJUnit4::class)
 * class MyAlgorithmBenchmarkTest : AbstractEncryptedDataStoreBenchmarkTest() {
 *     override val target: EncryptedDataStoreBenchmark = TemplateEncryptionTarget()
 * }
 * ```
 *
 * and run it. The report picks the new column up automatically and reports it as a delta against
 * whichever implementation declares `isReference = true`.
 */
class TemplateEncryptionTarget : EncryptedDataStoreBenchmark {

    override val id: String = "template_replace_me"

    override val displayName: String = "Template — replace me"

    override val description: String =
        "Algorithm, key size, mode, where the key lives, how the ciphertext is encoded."

    override fun serializer(
        context: Context,
        defaultValue: BenchmarkPayload
    ): Serializer<BenchmarkPayload> =
        TODO(
            "Return a Serializer<BenchmarkPayload> that encodes with `json` and encrypts with the " +
                    "algorithm under test. Model it on EncryptedJsonSerializer: read empty/undecryptable " +
                    "input back as `defaultValue` rather than throwing.",
        )

    override fun rawCodec(context: Context): EncryptedDataStoreBenchmark.RawCodec =
        TODO(
            "Return the encrypt/decrypt primitives so the codec-only rows can separate cipher cost " +
                    "from serialization and I/O, or null if this implementation has no such seam.",
        )
}
