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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the collected results as one self-contained HTML file.
 *
 * Every table is pivoted the same way — scenarios down the side, implementations across the top —
 * because the question the suite exists to answer is "is the new algorithm better than the current
 * one, and where". Each non-reference column carries its delta against the reference so the answer
 * is readable without arithmetic.
 */
object HtmlReportGenerator {

    private const val TITLE = "Guardian · DataStore encryption benchmark"

    fun render(data: BenchmarkReportData): String {
        val implementations = data.implementations()
        val datasets = data.records.map { it.dataset }.distinct().sortedBy { datasetOrder(it) }
        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
            append("<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            append("<title>").append(TITLE.esc()).append("</title>\n")
            append("<style>").append(CSS).append("</style>\n")
            append("</head>\n<body>\n<main>\n")

            appendHeader(data, implementations.size)
            appendImplementations(implementations)
            appendDatasets(data, datasets)

            appendSection(
                title = "CRUD speed",
                subtitle = "Median wall-clock time per operation. Lower is better; the delta " +
                        "compares against the reference implementation.",
            )
            datasets.forEach { dataset -> appendSpeedTable(data, implementations, dataset) }

            appendSection(
                title = "RAM consumption",
                subtitle = "Allocation churn is the delta of ART's art.gc.bytes-allocated across " +
                        "the operation — every byte handed out, surviving or not. Retained is settled " +
                        "heap growth; PSS is the resident footprint the OS charges the process.",
            )
            datasets.forEach { dataset -> appendMemoryTable(data, implementations, dataset) }

            appendSection(
                title = "Disk footprint",
                subtitle = "Bytes on disk after a write, against the plaintext JSON of the same " +
                        "document. Overhead covers the IV, the authentication tag and any text encoding " +
                        "the implementation applies to the ciphertext.",
            )
            appendFootprintTable(data, implementations, datasets)

            appendSection(
                title = "Partial update overhead",
                subtitle = "What one changed field actually costs, and whether DataStore rewrites " +
                        "the entire encrypted document to apply it.",
            )
            appendFindings(data)

            appendRawAppendix(data)

            append("</main>\n</body>\n</html>\n")
        }
    }

    // ---- sections ----------------------------------------------------------------------------

    private fun StringBuilder.appendHeader(data: BenchmarkReportData, implementationCount: Int) {
        val device = data.device
        append("<header>\n<h1>").append(TITLE.esc()).append("</h1>\n")
        append(
            "<p class=\"lede\">Speed, RAM and on-disk cost of encrypting a Jetpack DataStore " +
                    "document, measured across three payload sizes and "
        )
        append(implementationCount).append(" implementation")
        append(if (implementationCount == 1) "" else "s").append(".</p>\n")
        append("<dl class=\"meta\">\n")
        appendMeta("Device", "${device.manufacturer} ${device.model} (${device.device})")
        appendMeta("Android", "API ${device.sdkInt} · ${device.abi}")
        appendMeta("Heap limit", formatBytes(device.maxHeapBytes))
        appendMeta("Build type", if (device.isEmulator) "emulator" else "physical device")
        appendMeta("Generated", timestamp(data.generatedAtEpochMillis))
        append("</dl>\n")
        if (device.isEmulator) {
            append(
                "<p class=\"warn\">Measured on an emulator, from a debuggable test build. " +
                        "Rows are comparable with each other; the absolute numbers are not production " +
                        "timings. Re-run on a physical device before quoting them.</p>\n"
            )
        }
        append("</header>\n")
    }

    private fun StringBuilder.appendMeta(term: String, value: String) {
        append("<div><dt>").append(term.esc()).append("</dt><dd>").append(value.esc())
            .append("</dd></div>\n")
    }

    private fun StringBuilder.appendImplementations(implementations: List<ImplementationColumn>) {
        append("<section>\n<h2>Implementations compared</h2>\n<div class=\"cards\">\n")
        implementations.forEach { implementation ->
            append("<article class=\"card")
            if (implementation.isReference) append(" reference")
            append("\">\n<h3>").append(implementation.displayName.esc())
            if (implementation.isReference) append("<span class=\"pill\">reference</span>")
            append("</h3>\n<p>").append(implementation.description.esc()).append("</p>\n")
            append("<code>").append(implementation.id.esc()).append("</code>\n</article>\n")
        }
        append("</div>\n</section>\n")
    }

    private fun StringBuilder.appendDatasets(data: BenchmarkReportData, datasets: List<String>) {
        append("<section>\n<h2>Datasets</h2>\n<div class=\"scroll\">\n<table>\n")
        append("<thead><tr><th>Dataset</th><th>Serialized JSON</th></tr></thead>\n<tbody>\n")
        datasets.forEach { dataset ->
            val bytes = data.records.firstOrNull { it.dataset == dataset }?.payloadJsonBytes ?: 0L
            append("<tr><th scope=\"row\">").append(dataset.esc()).append("</th><td class=\"num\">")
            append(formatBytes(bytes)).append("</td></tr>\n")
        }
        append("</tbody>\n</table>\n</div>\n</section>\n")
    }

    private fun StringBuilder.appendSection(title: String, subtitle: String) {
        append("<section>\n<h2>").append(title.esc()).append("</h2>\n")
        append("<p class=\"note\">").append(subtitle.esc()).append("</p>\n</section>\n")
    }

    private fun StringBuilder.appendSpeedTable(
        data: BenchmarkReportData,
        implementations: List<ImplementationColumn>,
        dataset: String,
    ) {
        val scenarios = BenchmarkScenario.entries.filter { scenario ->
            scenario.isTimed && data.records.any { it.dataset == dataset && it.scenario == scenario.name }
        }
        if (scenarios.isEmpty()) return

        append("<section class=\"table-block\">\n<h3>").append(dataset.esc())
            .append(" · speed</h3>\n")
        append("<div class=\"scroll\">\n<table>\n<thead>\n<tr><th scope=\"col\">Scenario</th>\n")
        implementations.forEach {
            append("<th scope=\"col\" colspan=\"2\">").append(it.displayName.esc())
                .append("</th>\n")
        }
        append("</tr>\n<tr><th></th>\n")
        implementations.forEach { append("<th class=\"sub\">median</th><th class=\"sub\">vs ref</th>\n") }
        append("</tr>\n</thead>\n<tbody>\n")

        scenarios.forEach { scenario ->
            append("<tr><th scope=\"row\"><span class=\"tag\">").append(scenario.group.label.esc())
            append("</span>").append(scenario.label.esc())
            append("<small>").append(scenario.description.esc()).append("</small></th>\n")
            val reference = data.cell(implementations.reference().id, scenario, dataset)?.medianNs
            implementations.forEach { implementation ->
                val cell = data.cell(implementation.id, scenario, dataset)
                if (cell == null) {
                    append("<td class=\"num muted\">—</td><td class=\"num muted\">—</td>\n")
                } else {
                    append("<td class=\"num\" title=\"")
                    append(
                        ("min ${formatNs(cell.minNs)} · p90 ${formatNs(cell.p90Ns)} · " +
                                "max ${formatNs(cell.maxNs)} · ${cell.iterations} iterations").esc()
                    )
                    append("\">").append(formatNs(cell.medianNs)).append("</td>\n")
                    appendDelta(
                        cell.medianNs.toDouble(),
                        reference?.toDouble(),
                        implementation.isReference
                    )
                }
            }
            append("</tr>\n")
        }
        append("</tbody>\n</table>\n</div>\n</section>\n")
    }

    private fun StringBuilder.appendMemoryTable(
        data: BenchmarkReportData,
        implementations: List<ImplementationColumn>,
        dataset: String,
    ) {
        val scenarios = BenchmarkScenario.entries.filter { scenario ->
            scenario.isTimed && data.records.any { it.dataset == dataset && it.scenario == scenario.name }
        }
        if (scenarios.isEmpty()) return

        append("<section class=\"table-block\">\n<h3>").append(dataset.esc())
            .append(" · memory</h3>\n")
        append("<div class=\"scroll\">\n<table>\n<thead>\n<tr><th scope=\"col\">Scenario</th>\n")
        implementations.forEach {
            append("<th scope=\"col\" colspan=\"3\">").append(it.displayName.esc())
                .append("</th>\n")
        }
        append("</tr>\n<tr><th></th>\n")
        implementations.forEach {
            append("<th class=\"sub\">alloc churn</th><th class=\"sub\">retained</th><th class=\"sub\">PSS Δ</th>\n")
        }
        append("</tr>\n</thead>\n<tbody>\n")

        scenarios.forEach { scenario ->
            append("<tr><th scope=\"row\">").append(scenario.label.esc()).append("</th>\n")
            implementations.forEach { implementation ->
                val cell = data.cell(implementation.id, scenario, dataset)
                if (cell == null) {
                    append("<td class=\"num muted\">—</td><td class=\"num muted\">—</td><td class=\"num muted\">—</td>\n")
                } else {
                    append("<td class=\"num\" title=\"peak ").append(formatBytes(cell.maxAllocatedBytes).esc())
                    append(" · ").append(cell.gcCount).append(" GCs\">")
                    append(formatBytes(cell.medianAllocatedBytes)).append("</td>\n")
                    append("<td class=\"num\">").append(formatSignedBytes(cell.retainedHeapDeltaBytes))
                        .append("</td>\n")
                    append("<td class=\"num\">").append(formatSignedBytes(cell.pssDeltaKb * 1024))
                        .append("</td>\n")
                }
            }
            append("</tr>\n")
        }
        append("</tbody>\n</table>\n</div>\n</section>\n")
    }

    private fun StringBuilder.appendFootprintTable(
        data: BenchmarkReportData,
        implementations: List<ImplementationColumn>,
        datasets: List<String>,
    ) {
        val rows = datasets.filter { dataset ->
            data.records.any { it.dataset == dataset && it.scenario == BenchmarkScenario.DISK_FOOTPRINT.name }
        }
        if (rows.isEmpty()) return

        append("<section class=\"table-block\">\n<div class=\"scroll\">\n<table>\n<thead>\n")
        append("<tr><th scope=\"col\">Dataset</th><th scope=\"col\">Plaintext JSON</th>\n")
        implementations.forEach {
            append("<th scope=\"col\" colspan=\"2\">").append(it.displayName.esc())
                .append("</th>\n")
        }
        append("</tr>\n<tr><th></th><th class=\"sub\">bytes</th>\n")
        implementations.forEach { append("<th class=\"sub\">on disk</th><th class=\"sub\">overhead</th>\n") }
        append("</tr>\n</thead>\n<tbody>\n")

        rows.forEach { dataset ->
            val jsonBytes = data.records
                .first { it.dataset == dataset && it.scenario == BenchmarkScenario.DISK_FOOTPRINT.name }
                .payloadJsonBytes
            append("<tr><th scope=\"row\">").append(dataset.esc()).append("</th>")
            append("<td class=\"num\">").append(formatBytes(jsonBytes)).append("</td>\n")
            implementations.forEach { implementation ->
                val cell = data.cell(implementation.id, BenchmarkScenario.DISK_FOOTPRINT, dataset)
                if (cell == null || cell.diskBytes < 0) {
                    append("<td class=\"num muted\">—</td><td class=\"num muted\">—</td>\n")
                } else {
                    append("<td class=\"num\">").append(formatBytes(cell.diskBytes))
                        .append("</td>\n")
                    val overhead =
                        if (jsonBytes > 0) (cell.diskBytes - jsonBytes) * 100.0 / jsonBytes else 0.0
                    append("<td class=\"num ").append(if (overhead > 1.0) "slower" else "neutral")
                        .append("\">")
                    append(formatSignedPercent(overhead)).append("</td>\n")
                }
            }
            append("</tr>\n")
        }
        append("</tbody>\n</table>\n</div>\n</section>\n")
    }

    private fun StringBuilder.appendFindings(data: BenchmarkReportData) {
        if (data.findings.isEmpty()) {
            append("<section class=\"table-block\"><p class=\"note\">No partial-update analysis recorded.</p></section>\n")
            return
        }
        append("<section class=\"cards\">\n")
        data.findings
            .sortedWith(compareBy({ it.implementationName }, { datasetOrder(it.dataset) }))
            .forEach { finding ->
                append("<article class=\"card finding\">\n")
                append("<h3>").append(finding.implementationName.esc()).append(" · ")
                    .append(finding.dataset.esc()).append("</h3>\n")
                append("<p class=\"verdict ").append(if (finding.rewritesWholeFile) "bad" else "good")
                    .append("\">")
                append(finding.verdict.esc()).append("</p>\n")
                append("<table class=\"kv\">\n<tbody>\n")
                appendKeyValue("Document size", formatBytes(finding.payloadJsonBytes))
                appendKeyValue(
                    "File before / after",
                    "${formatBytes(finding.fileBytesBefore)} → ${formatBytes(finding.fileBytesAfter)}"
                )
                appendKeyValue("Bytes changed", formatPercent(finding.changedByteRatio * 100))
                appendKeyValue("Identical prefix", formatBytes(finding.identicalPrefixBytes))
                appendKeyValue(
                    "Inode replaced",
                    if (finding.inodeChanged) "yes — temp file + atomic rename" else "no — written in place"
                )
                appendKeyValue("One field", formatNs(finding.singleFieldUpdateMedianNs))
                appendKeyValue("Whole document", formatNs(finding.fullReplaceMedianNs))
                appendKeyValue(
                    "One field vs whole document",
                    if (finding.fullReplaceMedianNs > 0) {
                        formatRatio(finding.singleFieldUpdateMedianNs.toDouble() / finding.fullReplaceMedianNs)
                    } else {
                        "—"
                    },
                )
                appendKeyValue("No-op update", formatNs(finding.noOpUpdateMedianNs))
                appendKeyValue(
                    "Allocated for one field",
                    formatBytes(finding.singleFieldAllocatedBytes)
                )
                appendKeyValue(
                    "Allocated for a no-op",
                    formatBytes(finding.noOpUpdateAllocatedBytes)
                )
                append("</tbody>\n</table>\n</article>\n")
            }
        append("</section>\n")
    }

    private fun StringBuilder.appendKeyValue(term: String, value: String) {
        append("<tr><th scope=\"row\">").append(term.esc()).append("</th><td class=\"num\">")
        append(value.esc()).append("</td></tr>\n")
    }

    private fun StringBuilder.appendRawAppendix(data: BenchmarkReportData) {
        append("<section>\n<details>\n<summary>Raw measurements (")
        append(data.records.size).append(" rows)</summary>\n<div class=\"scroll\">\n<table class=\"raw\">\n")
        append("<thead><tr><th>Implementation</th><th>Scenario</th><th>Dataset</th><th>iters</th>")
        append("<th>min</th><th>median</th><th>mean</th><th>p90</th><th>max</th><th>std dev</th>")
        append("<th>alloc</th><th>retained</th><th>disk</th><th>notes</th></tr></thead>\n<tbody>\n")
        data.records
            .sortedWith(
                compareBy(
                    { it.implementationName },
                    { datasetOrder(it.dataset) },
                    { it.scenario })
            )
            .forEach { record ->
                append("<tr><td>").append(record.implementationName.esc())
                append("</td><td>").append(record.scenarioLabel.esc())
                append("</td><td>").append(record.dataset.esc())
                append("</td><td class=\"num\">").append(record.iterations)
                append("</td><td class=\"num\">").append(formatNs(record.minNs))
                append("</td><td class=\"num\">").append(formatNs(record.medianNs))
                append("</td><td class=\"num\">").append(formatNs(record.meanNs.toLong()))
                append("</td><td class=\"num\">").append(formatNs(record.p90Ns))
                append("</td><td class=\"num\">").append(formatNs(record.maxNs))
                append("</td><td class=\"num\">").append(formatNs(record.stdDevNs.toLong()))
                append("</td><td class=\"num\">").append(formatBytes(record.medianAllocatedBytes))
                append("</td><td class=\"num\">").append(formatSignedBytes(record.retainedHeapDeltaBytes))
                append("</td><td class=\"num\">").append(
                    if (record.diskBytes >= 0) formatBytes(
                        record.diskBytes
                    ) else "—"
                )
                append("</td><td>").append((record.notes ?: "").esc())
                append("</td></tr>\n")
            }
        append("</tbody>\n</table>\n</div>\n</details>\n</section>\n")
    }

    private fun StringBuilder.appendDelta(value: Double, reference: Double?, isReference: Boolean) {
        if (isReference) {
            append("<td class=\"num muted\">ref</td>\n")
            return
        }
        if (reference == null || reference <= 0.0) {
            append("<td class=\"num muted\">—</td>\n")
            return
        }
        val percent = (value - reference) * 100.0 / reference
        val cssClass = when {
            percent < -SIGNIFICANT_DELTA_PERCENT -> "faster"
            percent > SIGNIFICANT_DELTA_PERCENT -> "slower"
            else -> "neutral"
        }
        append("<td class=\"num ").append(cssClass).append("\">")
            .append(formatSignedPercent(percent)).append("</td>\n")
    }

    private const val SIGNIFICANT_DELTA_PERCENT = 3.0

    // ---- model helpers -----------------------------------------------------------------------

    private data class ImplementationColumn(
        val id: String,
        val displayName: String,
        val description: String,
        val isReference: Boolean,
    )

    private fun BenchmarkReportData.implementations(): List<ImplementationColumn> =
        records.map {
            ImplementationColumn(
                it.implementationId,
                it.implementationName,
                it.implementationDescription,
                it.isReference
            )
        }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<ImplementationColumn> { it.isReference }.thenBy { it.displayName })

    /** Falls back to the first column when no implementation declared itself the reference. */
    private fun List<ImplementationColumn>.reference(): ImplementationColumn =
        firstOrNull { it.isReference } ?: first()

    private fun BenchmarkReportData.cell(
        implementationId: String,
        scenario: BenchmarkScenario,
        dataset: String,
    ): BenchmarkRecord? = records.firstOrNull {
        it.implementationId == implementationId && it.scenario == scenario.name && it.dataset == dataset
    }

    private fun datasetOrder(dataset: String): Int = when (dataset) {
        "Empty" -> 0
        "Medium" -> 1
        "Large" -> 2
        else -> 3
    }

    // ---- formatting --------------------------------------------------------------------------

    private fun timestamp(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))

    private fun formatNs(nanos: Long): String = when {
        nanos <= 0L -> "0"
        nanos < 1_000L -> "$nanos ns"
        nanos < 1_000_000L -> String.format(Locale.US, "%.1f µs", nanos / 1_000.0)
        nanos < 1_000_000_000L -> String.format(Locale.US, "%.2f ms", nanos / 1_000_000.0)
        else -> String.format(Locale.US, "%.2f s", nanos / 1_000_000_000.0)
    }

    private fun formatBytes(bytes: Long): String {
        val absolute = kotlin.math.abs(bytes)
        return when {
            absolute < 1_024 -> "$bytes B"
            absolute < 1_048_576 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
        }
    }

    private fun formatSignedBytes(bytes: Long): String =
        if (bytes > 0) "+${formatBytes(bytes)}" else formatBytes(bytes)

    private fun formatPercent(percent: Double): String = String.format(Locale.US, "%.2f%%", percent)

    private fun formatSignedPercent(percent: Double): String =
        String.format(Locale.US, "%+.1f%%", percent)

    private fun formatRatio(ratio: Double): String = String.format(Locale.US, "%.2f×", ratio)

    private fun String.esc(): String = buildString(length) {
        this@esc.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

    private val CSS = """
        :root {
          color-scheme: light dark;
          --bg: #f6f7f9;
          --surface: #ffffff;
          --border: #e2e5ea;
          --text: #14181f;
          --muted: #6b7280;
          --accent: #2f6fed;
          --faster: #0f7b3d;
          --slower: #b3261e;
          --shadow: 0 1px 2px rgba(16, 24, 40, .06), 0 1px 3px rgba(16, 24, 40, .04);
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --bg: #101317;
            --surface: #171b21;
            --border: #262c35;
            --text: #e7eaee;
            --muted: #97a1b0;
            --accent: #79a4ff;
            --faster: #57d38c;
            --slower: #ff8a80;
            --shadow: none;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 32px 20px 80px;
          background: var(--bg);
          color: var(--text);
          font: 15px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }
        main { max-width: 1180px; margin: 0 auto; }
        h1 { font-size: 26px; margin: 0 0 6px; letter-spacing: -.01em; }
        h2 { font-size: 19px; margin: 40px 0 6px; letter-spacing: -.01em; }
        h3 { font-size: 15px; margin: 24px 0 10px; display: flex; align-items: center; gap: 8px; }
        p { margin: 0 0 10px; }
        .lede { color: var(--muted); max-width: 70ch; }
        .note { color: var(--muted); max-width: 78ch; margin-bottom: 4px; }
        .warn {
          margin-top: 14px; padding: 10px 12px; border-radius: 8px;
          border: 1px solid var(--border); border-left: 3px solid var(--slower);
          background: var(--surface); color: var(--muted); max-width: 78ch;
        }
        header { padding-bottom: 8px; border-bottom: 1px solid var(--border); }
        dl.meta {
          display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
          gap: 10px; margin: 18px 0 4px;
        }
        dl.meta dt { font-size: 11px; text-transform: uppercase; letter-spacing: .07em; color: var(--muted); }
        dl.meta dd { margin: 2px 0 0; font-weight: 600; }
        .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; }
        .card {
          background: var(--surface); border: 1px solid var(--border); border-radius: 10px;
          padding: 14px 16px; box-shadow: var(--shadow);
        }
        .card.reference { border-color: var(--accent); }
        .card h3 { margin: 0 0 6px; }
        .card p { color: var(--muted); font-size: 13px; }
        .card code { font-size: 11px; color: var(--muted); }
        .pill {
          font-size: 10px; text-transform: uppercase; letter-spacing: .08em; font-weight: 700;
          color: var(--accent); border: 1px solid var(--accent); border-radius: 999px; padding: 1px 7px;
        }
        .table-block { margin-top: 8px; }
        .scroll { overflow-x: auto; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); }
        table { border-collapse: collapse; width: 100%; font-size: 13px; }
        thead th {
          position: sticky; top: 0; background: var(--surface); text-align: right;
          font-size: 11px; text-transform: uppercase; letter-spacing: .06em; color: var(--muted);
          padding: 9px 12px; border-bottom: 1px solid var(--border); white-space: nowrap;
        }
        thead th:first-child { text-align: left; }
        thead th.sub { text-transform: none; letter-spacing: 0; font-weight: 500; }
        tbody th {
          text-align: left; font-weight: 600; padding: 9px 12px; vertical-align: top;
          border-bottom: 1px solid var(--border); min-width: 220px;
        }
        tbody th small { display: block; font-weight: 400; color: var(--muted); font-size: 11px; max-width: 44ch; }
        tbody td { padding: 9px 12px; border-bottom: 1px solid var(--border); white-space: nowrap; }
        tbody tr:last-child th, tbody tr:last-child td { border-bottom: 0; }
        tbody tr:hover td, tbody tr:hover th { background: rgba(127, 145, 180, .08); }
        .num { text-align: right; font-variant-numeric: tabular-nums; font-feature-settings: "tnum"; }
        .muted { color: var(--muted); }
        .faster { color: var(--faster); font-weight: 600; }
        .slower { color: var(--slower); font-weight: 600; }
        .neutral { color: var(--muted); }
        .tag {
          display: inline-block; font-size: 10px; text-transform: uppercase; letter-spacing: .06em;
          color: var(--muted); border: 1px solid var(--border); border-radius: 4px;
          padding: 0 5px; margin-right: 6px;
        }
        .finding .verdict {
          font-size: 13px; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--border);
        }
        .finding .verdict.bad { border-left: 3px solid var(--slower); }
        .finding .verdict.good { border-left: 3px solid var(--faster); }
        table.kv { margin-top: 10px; font-size: 12px; }
        table.kv th { min-width: 0; font-weight: 500; color: var(--muted); padding: 5px 0; }
        table.kv td { padding: 5px 0; }
        table.raw th, table.raw td { white-space: nowrap; }
        table.raw tbody th { min-width: 0; }
        details { margin-top: 18px; }
        summary { cursor: pointer; color: var(--muted); font-size: 13px; padding: 6px 0; }
    """.trimIndent()
}
