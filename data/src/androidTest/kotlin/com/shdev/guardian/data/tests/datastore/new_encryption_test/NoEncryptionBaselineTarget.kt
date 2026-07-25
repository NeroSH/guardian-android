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
import com.shdev.guardian.data.tests.datastore.models.PlainJsonSerializer
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload

/**
 * The control column: the identical DataStore path with no encryption at all.
 *
 * It is not a candidate implementation — it is the floor. Every encrypting column read against this
 * one separates the cost of encryption from the cost of DataStore's atomic write, kotlinx
 * .serialization, and the file system, none of which any algorithm can improve on.
 *
 * It also proves the extension seam works: this file plus a three-line test subclass is the entire
 * cost of adding a column to the report.
 */
class NoEncryptionBaselineTarget : EncryptedDataStoreBenchmark {

    override val id: String = "plaintext_json_control"

    override val displayName: String = "Plaintext JSON (control)"

    override val description: String =
        "No encryption — kotlinx.serialization JSON written straight to the DataStore file. " +
                "The floor for speed and disk size, and a document anyone with the file can read."

    override fun serializer(
        context: Context,
        defaultValue: BenchmarkPayload
    ): Serializer<BenchmarkPayload> =
        PlainJsonSerializer(defaultValue = defaultValue, json = json)

    /** No codec: the codec-only rows are skipped for this column. */
    override fun rawCodec(context: Context): EncryptedDataStoreBenchmark.RawCodec? = null
}
