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
package com.shdev.guardian.data.tests.datastore.schema

import com.shdev.guardian.data.tests.datastore.schema.BenchmarkDataSets.AUDIT_ENTRIES_PER_CHUNK
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** The Json used everywhere in the suite; mirrors [EncryptedJsonSerializer]'s default instance. */
val BenchmarkJson: Json = Json { ignoreUnknownKeys = true }

/**
 * The three payload sizes every scenario is run against.
 *
 * The byte bands are the contract; the element counts that reach them are solved at runtime by
 * [BenchmarkDataSets] rather than hard-coded, so the datasets stay inside their band even if the
 * schema gains or loses fields.
 */
enum class DatasetSize(
    val label: String,
    val minBytes: Int,
    val maxBytes: Int,
    val nestingDepth: Int,
    val appsPerDevice: Int,
    val tagCount: Int,
    val flagCount: Int,
) {
    /** Defaults and nulls only — isolates the fixed cost of a DataStore round trip. */
    EMPTY(
        "Empty",
        minBytes = 0,
        maxBytes = 1_024,
        nestingDepth = 0,
        appsPerDevice = 0,
        tagCount = 0,
        flagCount = 0
    ),

    /** Realistic production document. */
    MEDIUM(
        "Medium",
        minBytes = 10_000,
        maxBytes = 50_000,
        nestingDepth = 8,
        appsPerDevice = 6,
        tagCount = 8,
        flagCount = 10
    ),

    /** Stress payload — the size at which "re-encrypt the whole file" stops being free. */
    LARGE(
        "Large",
        minBytes = 1_000_000,
        maxBytes = 2_000_000,
        nestingDepth = 24,
        appsPerDevice = 20,
        tagCount = 24,
        flagCount = 40
    ),
    ;

    val targetBytes: Int get() = (minBytes + maxBytes) / 2
}

/**
 * One dataset: the document under test plus an equally sized but entirely different [alternate],
 * used by the whole-document update scenario so that every timed update really does change the data
 * (DataStore skips the write when the new value equals the current one).
 */
data class BenchmarkDataset(
    val size: DatasetSize,
    val primary: BenchmarkPayload,
    val alternate: BenchmarkPayload,
    val primaryJsonBytes: Long,
) {
    val label: String get() = size.label

    /** Encoded form of [primary], cached — the raw-codec scenarios feed on this. */
    val primaryJson: String by lazy {
        BenchmarkJson.encodeToString(
            BenchmarkPayload.serializer(),
            primary
        )
    }
}

/**
 * Deterministic dataset builder.
 *
 * Every string is fixed-length and drawn from a JSON-safe alphabet, so the encoded size of a payload
 * depends only on the element counts and not on the seed. That keeps [BenchmarkDataset.primary] and
 * [BenchmarkDataset.alternate] the same size while holding completely different content, and makes
 * two runs of the suite — or a run of a future encryption implementation — comparable byte for byte.
 */
object BenchmarkDataSets {

    private const val PRIMARY_SEED = 20_260_725L
    private const val ALTERNATE_SEED = 98_765_431L
    private const val AUDIT_ENTRIES_PER_CHUNK = 4
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    private val cache = ConcurrentHashMap<DatasetSize, BenchmarkDataset>()

    fun of(size: DatasetSize): BenchmarkDataset = cache.getOrPut(size) { build(size) }

    fun all(): List<BenchmarkDataset> = DatasetSize.entries.map(::of)

    private fun build(size: DatasetSize): BenchmarkDataset {
        if (size == DatasetSize.EMPTY) {
            val primary = BenchmarkPayload()
            // Must differ from `primary`, or the whole-document update scenario would be a no-op.
            val alternate = BenchmarkPayload(revision = 1L, isActive = true)
            return BenchmarkDataset(size, primary, alternate, encodedBytes(primary))
        }
        val chunks = solveChunkCount(size)
        val primary = buildPayload(size, chunks, PRIMARY_SEED)
        val alternate = buildPayload(size, chunks, ALTERNATE_SEED)
        return BenchmarkDataset(size, primary, alternate, encodedBytes(primary))
    }

    /**
     * Finds the chunk count whose encoded size lands inside [DatasetSize]'s band: one probe to learn
     * the per-chunk cost, one jump to the target, then bounded correction steps.
     */
    private fun solveChunkCount(size: DatasetSize): Int {
        val base = encodedBytes(buildPayload(size, chunks = 0, seed = PRIMARY_SEED)).toInt()
        val probeChunks = 4
        val probe = encodedBytes(buildPayload(size, probeChunks, PRIMARY_SEED)).toInt()
        val perChunk = ((probe - base) / probeChunks).coerceAtLeast(1)

        var chunks = ((size.targetBytes - base) / perChunk).coerceAtLeast(1)
        var bytes = encodedBytes(buildPayload(size, chunks, PRIMARY_SEED)).toInt()

        var guard = 0
        while (bytes < size.minBytes && guard++ < MAX_SOLVE_STEPS) {
            chunks += ((size.minBytes - bytes) / perChunk) + 1
            bytes = encodedBytes(buildPayload(size, chunks, PRIMARY_SEED)).toInt()
        }
        while (bytes > size.maxBytes && chunks > 1 && guard++ < MAX_SOLVE_STEPS) {
            chunks = ((chunks.toLong() * size.targetBytes) / bytes).toInt().coerceIn(1, chunks - 1)
            bytes = encodedBytes(buildPayload(size, chunks, PRIMARY_SEED)).toInt()
        }
        check(bytes in size.minBytes..size.maxBytes) {
            "Could not size the ${size.label} dataset into ${size.minBytes}..${size.maxBytes} bytes " +
                    "(landed on $bytes with $chunks chunks). Adjust DatasetSize.appsPerDevice."
        }
        return chunks
    }

    private const val MAX_SOLVE_STEPS = 64

    private fun encodedBytes(payload: BenchmarkPayload): Long =
        BenchmarkJson.encodeToString(BenchmarkPayload.serializer(), payload)
            .toByteArray(Charsets.UTF_8).size.toLong()

    /** A chunk is one [DeviceRecord] plus [AUDIT_ENTRIES_PER_CHUNK] audit rows. */
    private fun buildPayload(size: DatasetSize, chunks: Int, seed: Long): BenchmarkPayload {
        val random = Random(seed)
        val devices = ArrayList<DeviceRecord>(chunks)
        val auditLog = ArrayList<AuditEntry>(chunks * AUDIT_ENTRIES_PER_CHUNK)
        repeat(chunks) { chunkIndex ->
            devices += device(random, chunkIndex, size.appsPerDevice)
            repeat(AUDIT_ENTRIES_PER_CHUNK) { entryIndex ->
                auditLog += auditEntry(
                    random,
                    (chunkIndex * AUDIT_ENTRIES_PER_CHUNK + entryIndex).toLong()
                )
            }
        }
        return BenchmarkPayload(
            schemaVersion = 3,
            revision = 1L,
            profileId = text(random, 24),
            trustScore = random.nextDouble(0.0, 100.0),
            isActive = true,
            displayName = text(random, 18),
            lastSyncEpochMillis = 1_700_000_000_000L + random.nextLong(0, 1_000_000_000L),
            batteryThreshold = random.nextDouble(0.0, 1.0),
            pairingCode = text(random, 8),
            tags = List(size.tagCount) { text(random, 12) },
            featureFlags = (0 until size.flagCount).associate { "feature_flag_$it" to random.nextBoolean() },
            quotaByPackage = (0 until size.flagCount).associate {
                "com.example.package$it" to random.nextLong(0, 5_000_000L)
            },
            owner = owner(random),
            devices = devices,
            auditLog = auditLog,
            settings = settings(random, size.nestingDepth),
        )
    }

    private fun owner(random: Random): UserProfile = UserProfile(
        userId = text(random, 32),
        email = "${text(random, 12)}@${text(random, 8)}.example",
        // Nullability and collection sizes are fixed rather than random: the encoded size then
        // depends only on the element counts, so `primary` and `alternate` stay the same size.
        phone = "+1${random.nextLong(2_000_000_000, 9_999_999_999)}",
        locale = "en-US",
        roles = listOf(Role.OWNER, Role.PARENT, Role.AUDITOR),
        attributes = (0 until 12).associate { "attribute_$it" to text(random, 16) },
        address = Address(
            line1 = text(random, 24),
            line2 = text(random, 12),
            city = text(random, 12),
            postalCode = text(random, 6),
            countryCode = "US",
            geo = GeoPoint(
                random.nextDouble(-90.0, 90.0),
                random.nextDouble(-180.0, 180.0),
                random.nextDouble(0.0, 50.0)
            ),
        ),
        avatarBase64 = text(random, 256),
    )

    private fun device(random: Random, index: Int, appsPerDevice: Int): DeviceRecord = DeviceRecord(
        deviceId = text(random, 28),
        model = text(random, 14),
        manufacturer = text(random, 10),
        sdkInt = random.nextInt(26, 37),
        lastSeenEpochMillis = 1_700_000_000_000L + random.nextLong(0, 1_000_000_000L),
        batteryLevel = random.nextDouble(0.0, 1.0),
        isRooted = random.nextBoolean(),
        nickname = if (index % 3 == 0) null else text(random, 10),
        installedApps = List(appsPerDevice) { app(random, it) },
        sensorReadings = (0 until 8).associate { "sensor_$it" to random.nextDouble(-100.0, 100.0) },
        lastKnownLocation = if (index % 4 == 0) null else {
            GeoPoint(
                random.nextDouble(-90.0, 90.0),
                random.nextDouble(-180.0, 180.0),
                random.nextDouble(0.0, 50.0)
            )
        },
    )

    private fun app(random: Random, index: Int): AppRecord = AppRecord(
        packageName = "com.${text(random, 8)}.${text(random, 10)}",
        label = text(random, 16),
        versionCode = random.nextLong(1, 900_000),
        versionName = if (index % 5 == 0) null else "${random.nextInt(1, 20)}.${
            random.nextInt(
                0,
                99
            )
        }.${random.nextInt(0, 99)}",
        category = AppCategory.entries[random.nextInt(AppCategory.entries.size)],
        isBlocked = random.nextBoolean(),
        blockedReason = if (index % 2 == 0) null else text(random, 20),
        installedAtEpochMillis = 1_600_000_000_000L + random.nextLong(0, 1_000_000_000L),
        permissions = List(6) { "android.permission.${text(random, 14)}" },
        dailyUsageMinutes = (0 until 7).associate { "day_$it" to random.nextInt(0, 720) },
    )

    private fun auditEntry(random: Random, sequence: Long): AuditEntry = AuditEntry(
        sequence = sequence,
        actorId = text(random, 20),
        action = AuditAction.entries[random.nextInt(AuditAction.entries.size)],
        timestampEpochMillis = 1_700_000_000_000L + sequence * 1_000L,
        succeeded = random.nextBoolean(),
        durationMillis = random.nextDouble(0.0, 5_000.0),
        metadata = (0 until 4).associate { "meta_$it" to text(random, 14) },
        previousHash = if (sequence == 0L) null else text(random, 40),
    )

    private fun settings(random: Random, depth: Int): SettingsTree = SettingsTree(
        bedtimeEnabled = true,
        dailyLimitMinutes = random.nextInt(30, 600),
        windows = List(7) {
            ScheduleWindow(
                dayOfWeek = it,
                startMinute = random.nextInt(0, 720),
                endMinute = random.nextInt(720, 1_440),
                label = if (it % 2 == 0) null else text(random, 10),
            )
        },
        thresholds = (0 until 10).associate { "threshold_$it" to random.nextDouble(0.0, 1.0) },
        nested = nestedChain(random, depth),
    )

    private fun nestedChain(random: Random, depth: Int): NestedNode? {
        if (depth <= 0) return null
        var node: NestedNode? = null
        // Built leaf-first so the recursion here stays iterative; the *decoder* still recurses.
        for (level in depth downTo 1) {
            node = NestedNode(
                depth = level,
                label = text(random, 12),
                weight = random.nextDouble(),
                flags = List(4) { random.nextBoolean() },
                child = node,
            )
        }
        return node
    }

    /** Fixed-length, JSON-escape-free string: length is seed-independent, so sizes stay stable. */
    private fun text(random: Random, length: Int): String {
        val chars = CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars)
    }
}

/** The single-field mutation used by the partial-update scenarios. */
fun BenchmarkPayload.withNextRevision(): BenchmarkPayload = copy(revision = revision + 1)
