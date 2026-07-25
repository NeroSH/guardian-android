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
package com.shdev.guardian.data.tests.datastore.models

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import com.shdev.guardian.data.tests.datastore.EncryptedDataStoreBenchmark
import com.shdev.guardian.data.tests.datastore.schema.BenchmarkPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production wiring, reproduced for the benchmark: the same
 * [MultiProcessDataStoreFactory] the app's stores use, so the file locking and the atomic
 * write path being measured are the real ones.
 */
fun EncryptedDataStoreBenchmark.defaultMultiProcessStore(
    context: Context,
    file: File,
    defaultValue: BenchmarkPayload,
    scope: CoroutineScope,
): DataStore<BenchmarkPayload> = MultiProcessDataStoreFactory.create(
    serializer = serializer(context, defaultValue),
    scope = scope,
    produceFile = { file },
)

/**
 * A live store plus the file behind it and the scope that owns it.
 *
 * DataStore has no public `close()`; an instance is released by cancelling the scope it was created
 * with. Until that completes, opening a second instance over the same file throws — which is why
 * [close] joins rather than firing and forgetting.
 */
class ManagedBenchmarkStore internal constructor(
    val dataStore: DataStore<BenchmarkPayload>,
    val file: File,
    private val scope: CoroutineScope,
) {
    private var closed = false

    /** Bytes currently on disk, or 0 before the first write. */
    fun diskBytes(): Long = if (file.exists()) file.length() else 0L

    fun close() {
        if (closed) return
        closed = true
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    }
}

/**
 * Creates DataStores for one implementation under test, and guarantees they never collide.
 *
 * Every file name carries a per-factory run id and a monotonic counter, so no two scenarios — and no
 * two runs of the same scenario — ever share a file. That matters more here than in an ordinary
 * test: DataStore keeps a process-wide in-memory cache keyed by file, so a reused name would let a
 * previous scenario's cached document turn a "cold read" into a warm one and silently delete the
 * decryption cost from the results.
 */
class BenchmarkStoreFactory(
    private val context: Context,
    private val target: EncryptedDataStoreBenchmark,
) {
    private val runId = UUID.randomUUID().toString().take(8)
    private val counter = AtomicInteger()
    private val openStores = CopyOnWriteArrayList<ManagedBenchmarkStore>()

    /** All benchmark files live here and nowhere near the app's real DataStore directory. */
    private val directory: File =
        File(context.filesDir, "datastore_benchmark/${target.id}_$runId").apply { mkdirs() }

    fun uniqueFile(tag: String): File =
        File(directory, "bench_${tag}_${counter.incrementAndGet()}.pb")

    /** Opens a store over an empty, never-before-used file. */
    fun openFresh(
        tag: String,
        defaultValue: BenchmarkPayload = BenchmarkPayload()
    ): ManagedBenchmarkStore =
        open(uniqueFile(tag), defaultValue)

    /** Opens a store over [file], which may already hold a document. */
    fun open(
        file: File,
        defaultValue: BenchmarkPayload = BenchmarkPayload()
    ): ManagedBenchmarkStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = ManagedBenchmarkStore(
            dataStore = target.createStore(context, file, defaultValue, scope),
            file = file,
            scope = scope,
        )
        openStores += store
        return store
    }

    /**
     * Byte-for-byte copy of [source] under a brand new name. The cold-read scenario opens each
     * iteration's store over its own copy: a fresh file can never be served from another instance's
     * cache, and there is no window in which two instances contend for one path.
     */
    fun copyOf(source: File, tag: String): File =
        uniqueFile(tag).also { source.copyTo(it, overwrite = true) }

    /** Releases the store but leaves its file, for scenarios that reopen it. */
    fun close(store: ManagedBenchmarkStore) {
        store.close()
        openStores.remove(store)
    }

    /**
     * Releases the store and unlinks its file plus the `.lock` sibling
     * [MultiProcessDataStoreFactory] keeps beside it. Used in per-iteration teardown so a
     * LARGE-dataset sweep does not leave tens of megabytes behind mid-run.
     */
    fun discard(store: ManagedBenchmarkStore) {
        close(store)
        deleteFiles(store.file)
    }

    fun deleteFiles(file: File) {
        runCatching { file.delete() }
        runCatching { File(file.parentFile, "${file.name}.lock").delete() }
    }

    /** Closes every store this factory opened and removes the whole directory. */
    fun cleanUp() {
        openStores.forEach { runCatching { it.close() } }
        openStores.clear()
        runCatching { directory.deleteRecursively() }
    }
}
