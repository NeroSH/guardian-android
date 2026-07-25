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

import androidx.test.platform.app.InstrumentationRegistry
import com.shdev.guardian.data.tests.datastore.schema.DatasetSize
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

/** How many times a scenario is run. Defaults scale down for LARGE so a full sweep stays minutes. */
data class BenchmarkConfig(
    val warmupIterations: Int,
    val timedIterations: Int,
    val memoryIterations: Int,
) {
    companion object {
        /** Overridable per run: `-e datastore.bench.iterations 25 -e datastore.bench.warmups 5`. */
        fun forDataset(size: DatasetSize): BenchmarkConfig {
            val heavy = size == DatasetSize.LARGE
            return BenchmarkConfig(
                warmupIterations = intArg("datastore.bench.warmups", if (heavy) 2 else 3),
                timedIterations = intArg("datastore.bench.iterations", if (heavy) 7 else 15),
                memoryIterations = intArg("datastore.bench.memoryIterations", if (heavy) 3 else 5),
            )
        }

        private fun intArg(key: String, fallback: Int): Int =
            InstrumentationRegistry.getArguments().getString(key)?.toIntOrNull()?.coerceAtLeast(1)
                ?: fallback
    }
}

data class TimingStats(
    val iterations: Int,
    val minNs: Long,
    val medianNs: Long,
    val meanNs: Double,
    val p90Ns: Long,
    val maxNs: Long,
    val stdDevNs: Double,
)

data class MemoryStats(
    val iterations: Int,
    /** Median of the per-iteration `art.gc.bytes-allocated` delta — the allocation churn. */
    val medianAllocatedBytes: Long,
    val maxAllocatedBytes: Long,
    /** Settled-heap growth across the memory pass: what the operation left behind. */
    val retainedHeapDeltaBytes: Long,
    val pssDeltaKb: Long,
    val gcCount: Long,
)

data class MeasuredScenario(val timing: TimingStats, val memory: MemoryStats)

/**
 * The measurement loop.
 *
 * Timing and memory are two separate passes over the same work. Probing costs milliseconds
 * (`Debug.getMemoryInfo` walks /proc, `settle()` forces two collections), so folding it into the
 * timed pass would measure the probe as much as the operation. The timed pass therefore touches
 * nothing but `System.nanoTime`, and the memory pass runs afterwards with the clock ignored.
 *
 * `setup` and `teardown` run inside the loop but outside the measured window — that is where a
 * scenario builds the state it needs (a populated file to read, a fresh store to write into)
 * without paying for it in the result.
 */
class BenchmarkHarness(private val config: BenchmarkConfig) {

    fun measure(
        setup: suspend (iteration: Int) -> Unit = {},
        teardown: suspend (iteration: Int) -> Unit = {},
        block: suspend (iteration: Int) -> Unit,
    ): MeasuredScenario = runBlocking {
        repeat(config.warmupIterations) { iteration ->
            setup(iteration)
            block(iteration)
            teardown(iteration)
        }

        val timings = LongArray(config.timedIterations)
        for (iteration in 0 until config.timedIterations) {
            setup(iteration)
            val start = System.nanoTime()
            block(iteration)
            timings[iteration] = System.nanoTime() - start
            teardown(iteration)
        }

        val allocations = LongArray(config.memoryIterations)
        var gcCount = 0L
        MemoryProbe.settle()
        val heapBefore = MemoryProbe.snapshot(includePss = true)
        for (iteration in 0 until config.memoryIterations) {
            setup(iteration)
            val before = MemoryProbe.snapshot()
            block(iteration)
            val after = MemoryProbe.snapshot()
            allocations[iteration] = (after.allocatedBytes - before.allocatedBytes).coerceAtLeast(0)
            gcCount += (after.gcCount - before.gcCount).coerceAtLeast(0)
            teardown(iteration)
        }
        MemoryProbe.settle()
        val heapAfter = MemoryProbe.snapshot(includePss = true)

        MeasuredScenario(
            timing = timingStats(timings),
            memory = MemoryStats(
                iterations = config.memoryIterations,
                medianAllocatedBytes = allocations.median(),
                maxAllocatedBytes = allocations.maxOrNull() ?: 0L,
                retainedHeapDeltaBytes = heapAfter.javaHeapUsedBytes - heapBefore.javaHeapUsedBytes,
                pssDeltaKb = heapAfter.totalPssKb - heapBefore.totalPssKb,
                gcCount = gcCount,
            ),
        )
    }

    private fun timingStats(samples: LongArray): TimingStats {
        val sorted = samples.sortedArray()
        val mean = sorted.average()
        val variance = sorted.sumOf { val d = it - mean; d * d } / sorted.size
        return TimingStats(
            iterations = sorted.size,
            minNs = sorted.first(),
            medianNs = sorted.median(),
            meanNs = mean,
            p90Ns = sorted[((sorted.size - 1) * 90) / 100],
            maxNs = sorted.last(),
            stdDevNs = sqrt(variance),
        )
    }
}

/** Median of an unsorted array — the headline statistic, being robust to a single GC outlier. */
internal fun LongArray.median(): Long {
    if (isEmpty()) return 0L
    val sorted = sortedArray()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
