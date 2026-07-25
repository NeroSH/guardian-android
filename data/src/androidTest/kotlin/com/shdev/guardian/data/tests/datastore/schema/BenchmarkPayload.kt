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

import kotlinx.serialization.Serializable

/**
 * The document every benchmark writes through the DataStore under test.
 *
 * It is deliberately wider than any production store: the point is to keep the serializer busy on
 * every shape kotlinx.serialization has a different code path for, so a comparison between two
 * encryption implementations is not accidentally measuring one narrow field type.
 *
 * Covered: non-null primitives, nullable primitives, `List<String>`, `Map<String, *>` with three
 * different value types, enums, nested classes, lists of nested classes, and a recursive node that
 * forces deep (non-tail) encoder recursion.
 */
@Serializable
data class BenchmarkPayload(
    // ---- non-null primitives -------------------------------------------------------------------
    val schemaVersion: Int = 1,
    val revision: Long = 0L,
    val profileId: String = "",
    val trustScore: Double = 0.0,
    val isActive: Boolean = false,

    // ---- nullable primitives -------------------------------------------------------------------
    val displayName: String? = null,
    val lastSyncEpochMillis: Long? = null,
    val batteryThreshold: Double? = null,
    val pairingCode: String? = null,

    // ---- collections ---------------------------------------------------------------------------
    val tags: List<String> = emptyList(),
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val quotaByPackage: Map<String, Long> = emptyMap(),

    // ---- nested --------------------------------------------------------------------------------
    val owner: UserProfile? = null,
    val devices: List<DeviceRecord> = emptyList(),
    val auditLog: List<AuditEntry> = emptyList(),
    val settings: SettingsTree = SettingsTree(),
)

@Serializable
enum class Role { OWNER, PARENT, CHILD, GUEST, AUDITOR }

@Serializable
enum class AppCategory { SOCIAL, GAMES, EDUCATION, PRODUCTIVITY, MEDIA, SYSTEM, UNKNOWN }

@Serializable
enum class AuditAction { CREATED, UPDATED, DELETED, BLOCKED, ALLOWED, SYNCED, LOGIN, LOGOUT }

@Serializable
data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double? = null,
)

@Serializable
data class Address(
    val line1: String = "",
    val line2: String? = null,
    val city: String = "",
    val postalCode: String = "",
    val countryCode: String = "",
    val geo: GeoPoint? = null,
)

@Serializable
data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val phone: String? = null,
    val locale: String = "en-US",
    val roles: List<Role> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    val address: Address? = null,
    /** A long opaque string — the kind of field that dominates the byte count of a real document. */
    val avatarBase64: String? = null,
)

@Serializable
data class AppRecord(
    val packageName: String = "",
    val label: String = "",
    val versionCode: Long = 0L,
    val versionName: String? = null,
    val category: AppCategory = AppCategory.UNKNOWN,
    val isBlocked: Boolean = false,
    val blockedReason: String? = null,
    val installedAtEpochMillis: Long = 0L,
    val permissions: List<String> = emptyList(),
    val dailyUsageMinutes: Map<String, Int> = emptyMap(),
)

@Serializable
data class DeviceRecord(
    val deviceId: String = "",
    val model: String = "",
    val manufacturer: String = "",
    val sdkInt: Int = 0,
    val lastSeenEpochMillis: Long = 0L,
    val batteryLevel: Double = 0.0,
    val isRooted: Boolean = false,
    val nickname: String? = null,
    val installedApps: List<AppRecord> = emptyList(),
    val sensorReadings: Map<String, Double> = emptyMap(),
    val lastKnownLocation: GeoPoint? = null,
)

@Serializable
data class AuditEntry(
    val sequence: Long = 0L,
    val actorId: String = "",
    val action: AuditAction = AuditAction.CREATED,
    val timestampEpochMillis: Long = 0L,
    val succeeded: Boolean = true,
    val durationMillis: Double = 0.0,
    val metadata: Map<String, String> = emptyMap(),
    val previousHash: String? = null,
)

/**
 * Recursive node. [BenchmarkDataSets] builds a chain [DatasetSize.nestingDepth] links long so the
 * encoder/decoder has to recurse that far — flat payloads of the same byte size do not exercise the
 * same code path.
 */
@Serializable
data class NestedNode(
    val depth: Int = 0,
    val label: String = "",
    val weight: Double = 0.0,
    val flags: List<Boolean> = emptyList(),
    val child: NestedNode? = null,
)

@Serializable
data class ScheduleWindow(
    val dayOfWeek: Int = 0,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val label: String? = null,
)

@Serializable
data class SettingsTree(
    val bedtimeEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 0,
    val windows: List<ScheduleWindow> = emptyList(),
    val thresholds: Map<String, Double> = emptyMap(),
    val nested: NestedNode? = null,
)
