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

/** Report section a scenario belongs to. */
enum class ScenarioGroup(val label: String) {
    CREATE("Create"),
    READ("Read"),
    UPDATE("Update"),
    DELETE("Delete"),
    CRYPTO("Codec only"),
    SERIALIZATION("Serialization only"),
    FOOTPRINT("Footprint"),
}

/**
 * Every measured operation. The ordinal order is the row order in the report, so keep it in
 * lifecycle order.
 */
enum class BenchmarkScenario(
    val label: String,
    val group: ScenarioGroup,
    val description: String,
) {
    CREATE(
        label = "Create — first write",
        group = ScenarioGroup.CREATE,
        description = "Fresh store over a fresh file: initial (empty) read, serialize, encrypt, " +
                "write, fsync, atomic rename.",
    ),
    COLD_READ(
        label = "Read — cold",
        group = ScenarioGroup.READ,
        description = "New DataStore instance over an already populated file: the read path that " +
                "pays for decryption. Every later read through the same instance is served from memory.",
    ),
    WARM_READ(
        label = "Read — warm",
        group = ScenarioGroup.READ,
        description = "Read from the in-memory cache of a live DataStore. Touches neither disk " +
                "nor the codec, so it should be identical across implementations.",
    ),
    UPDATE_FULL(
        label = "Update — whole document",
        group = ScenarioGroup.UPDATE,
        description = "updateData replacing the document with an equally sized but different one.",
    ),
    UPDATE_SINGLE_FIELD(
        label = "Update — one field",
        group = ScenarioGroup.UPDATE,
        description = "updateData changing a single Long. Compare against 'whole document' to see " +
                "whether anything is saved by touching less data.",
    ),
    NO_OP_UPDATE(
        label = "Update — no-op",
        group = ScenarioGroup.UPDATE,
        description = "updateData returning the current instance unchanged. The write is skipped " +
                "once equals() matches — the test asserts the file is untouched — but everything " +
                "before the comparison still runs. Compare this row against the cold read to see how " +
                "much of an update is spent getting to the point of deciding not to write.",
    ),
    DELETE_RESET(
        label = "Delete — reset to default",
        group = ScenarioGroup.DELETE,
        description = "updateData writing the default instance — the way a typed DataStore is cleared.",
    ),
    DELETE_FILE(
        label = "Delete — remove file",
        group = ScenarioGroup.DELETE,
        description = "Closing the store and unlinking the file, for reference against the reset above.",
    ),
    ENCRYPT_RAW(
        label = "Encrypt — codec only",
        group = ScenarioGroup.CRYPTO,
        description = "The encryption primitive on the already serialized JSON. No DataStore, no I/O.",
    ),
    DECRYPT_RAW(
        label = "Decrypt — codec only",
        group = ScenarioGroup.CRYPTO,
        description = "The decryption primitive on the stored blob. No DataStore, no I/O.",
    ),
    JSON_ENCODE(
        label = "JSON encode only",
        group = ScenarioGroup.SERIALIZATION,
        description = "kotlinx.serialization encode. The floor under every write; subtract it to " +
                "isolate the cost of encryption.",
    ),
    JSON_DECODE(
        label = "JSON decode only",
        group = ScenarioGroup.SERIALIZATION,
        description = "kotlinx.serialization decode. The floor under every cold read.",
    ),
    DISK_FOOTPRINT(
        label = "Disk footprint",
        group = ScenarioGroup.FOOTPRINT,
        description = "Bytes on disk after a write, against the plaintext JSON size.",
    ),
    ;

    val isTimed: Boolean get() = this != DISK_FOOTPRINT
}
