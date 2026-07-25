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
package com.shdev.guardian.data.tests.datastore

import android.os.Debug

/** One reading of the process's memory counters. All values are bytes unless named otherwise. */
data class MemorySnapshot(
    /** Cumulative bytes ever allocated by the process (`art.gc.bytes-allocated`). */
    val allocatedBytes: Long,
    /** Cumulative bytes ever reclaimed (`art.gc.bytes-freed`). */
    val freedBytes: Long,
    val gcCount: Long,
    val blockingGcCount: Long,
    /** Java heap currently in use — meaningful only right after a [MemoryProbe.settle]. */
    val javaHeapUsedBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val totalPssKb: Long,
)

/**
 * Real memory measurement, not an estimate.
 *
 * Three numbers matter and they answer different questions:
 *
 *  * **Allocation churn** — the delta of ART's `art.gc.bytes-allocated`, which counts every byte the
 *    process handed out, whether or not it survived. This is the honest cost of an operation: the
 *    intermediate `String`, the `ByteArray` the cipher produced, the Base64 expansion. It is
 *    process-wide by design — DataStore does its work on its own dispatcher threads, so a
 *    thread-local counter would miss most of it.
 *  * **Retained heap** — used heap after a forced GC, before against after. What the operation
 *    permanently added, e.g. the document DataStore now caches in memory.
 *  * **PSS** — the resident footprint the OS actually charges the app for, including native
 *    allocations made by the cipher below the Java heap.
 *
 * `art.gc.*` runtime stats need API 23; the module's minSdk is 26, so they are always available.
 */
object MemoryProbe {

    private const val STAT_BYTES_ALLOCATED = "art.gc.bytes-allocated"
    private const val STAT_BYTES_FREED = "art.gc.bytes-freed"
    private const val STAT_GC_COUNT = "art.gc.gc-count"
    private const val STAT_BLOCKING_GC_COUNT = "art.gc.blocking-gc-count"

    /** [includePss] is off in hot paths: `getMemoryInfo` walks /proc and costs milliseconds. */
    fun snapshot(includePss: Boolean = false): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val pssKb = if (includePss) {
            Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong()
        } else {
            -1L
        }
        return MemorySnapshot(
            allocatedBytes = runtimeStat(STAT_BYTES_ALLOCATED),
            freedBytes = runtimeStat(STAT_BYTES_FREED),
            gcCount = runtimeStat(STAT_GC_COUNT),
            blockingGcCount = runtimeStat(STAT_BLOCKING_GC_COUNT),
            javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
            totalPssKb = pssKb,
        )
    }

    /**
     * Drives the heap to a quiet, comparable state before a reading. Two collections because the
     * first one queues finalizers whose objects only die in the second.
     */
    fun settle() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(SETTLE_PAUSE_MILLIS)
        }
    }

    private const val SETTLE_PAUSE_MILLIS = 60L

    private fun runtimeStat(name: String): Long =
        Debug.getRuntimeStat(name)?.toLongOrNull() ?: -1L
}
