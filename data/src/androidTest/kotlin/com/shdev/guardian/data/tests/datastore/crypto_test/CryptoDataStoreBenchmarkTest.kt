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
package com.shdev.guardian.data.tests.datastore.crypto_test

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shdev.guardian.data.tests.datastore.AbstractEncryptedDataStoreBenchmarkTest
import com.shdev.guardian.data.tests.datastore.EncryptedDataStoreBenchmark
import org.junit.runner.RunWith

/**
 * Runs the full CRUD / memory / partial-update sweep against the shipping encryption stack.
 *
 * The suite itself lives in [AbstractEncryptedDataStoreBenchmarkTest]; this class only names the
 * implementation under test. Results land in the shared `report.html` alongside every other
 * implementation that has been benchmarked on this device.
 */
@RunWith(AndroidJUnit4::class)
class CryptoDataStoreBenchmarkTest : AbstractEncryptedDataStoreBenchmarkTest() {
    override val target: EncryptedDataStoreBenchmark = CryptoDataStoreTarget()
}
