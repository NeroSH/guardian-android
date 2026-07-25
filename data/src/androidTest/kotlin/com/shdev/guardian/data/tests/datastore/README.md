# DataStore encryption benchmark

Instrumented suite measuring what it costs to keep a Jetpack DataStore document encrypted at rest:
CRUD speed, real RAM consumption, disk footprint, and the overhead of changing a single field in a
large document.

It is built to compare implementations, not to measure one. Everything is written against
[`EncryptedDataStoreBenchmark`](EncryptedDataStoreBenchmark.kt); the shipping stack
(`CryptoDataStoreImpl` + `EncryptedJsonSerializer`) is one column, a plaintext control is another,
and
a future algorithm becomes a third by implementing one interface.

## Layout

| Path                   | Contents                                                                                                                                                                                                                                                                                                           |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `schema/`              | [`BenchmarkPayload`](schema/BenchmarkPayload.kt) — a wide document (primitives, nullables, lists, three map value types, enums, nested classes, a recursive node) — and [`BenchmarkDataSets`](schema/BenchmarkDataSets.kt), which sizes it deterministically into Empty / Medium / Large.                          |
| `models/`              | Test-only DataStore plumbing: [`BenchmarkStoreFactory`](models/BenchmarkStoreFactory.kt) (unique file per store, lifecycle, cold-read copies) and [`PlainJsonSerializer`](models/PlainJsonSerializer.kt) (the unencrypted control).                                                                                |
| `crypto_test/`         | The shipping implementation: its [target](crypto_test/CryptoDataStoreTarget.kt), the [benchmark subclass](crypto_test/CryptoDataStoreBenchmarkTest.kt), a [correctness gate](crypto_test/CryptoDataStoreCorrectnessTest.kt), and a [`BenchmarkRule` microbenchmark](crypto_test/CryptoDataStoreMicrobenchmark.kt). |
| `new_encryption_test/` | Where candidates go: a [template](new_encryption_test/TemplateEncryptionTarget.kt), a [working example](new_encryption_test/NoEncryptionBaselineTarget.kt), and [instructions](new_encryption_test/README.md).                                                                                                     |
| *(this package)*       | The interface, the [measurement harness](BenchmarkHarness.kt), the [memory probes](MemoryProbe.kt), the [result store](BenchmarkResults.kt), the [HTML report](HtmlReportGenerator.kt), and the [scenario suite](AbstractEncryptedDataStoreBenchmarkTest.kt).                                                      |

## Datasets

|            | Serialized JSON | Shape                                                            |
|------------|-----------------|------------------------------------------------------------------|
| **Empty**  | < 1 KB          | Defaults and nulls — the fixed cost of a round trip.             |
| **Medium** | 10–50 KB        | Realistic production document.                                   |
| **Large**  | 1–2 MB          | The size at which re-encrypting the whole file stops being free. |

Element counts are solved at runtime to land inside those bands, so the datasets stay in range if
the schema changes. Strings are fixed-length and JSON-escape-free, which keeps a dataset's size
independent of its seed — the "different document, same size" payload the whole-document update
scenario needs.

## Running

```bash
# Everything: both implementations, all scenarios, all sizes.
./gradlew :data:connectedDebugAndroidTest

# One implementation.
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shdev.guardian.data.tests.datastore.crypto_test.CryptoDataStoreBenchmarkTest

# More iterations, and start from an empty report.
./gradlew :data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.datastore.bench.iterations=25 \
  -Pandroid.testInstrumentationRunnerArguments.datastore.bench.reset=true
```

Instrumentation arguments: `datastore.bench.iterations`, `datastore.bench.warmups`,
`datastore.bench.memoryIterations`, `datastore.bench.reset`.

A full sweep of one implementation is a few minutes, most of it the Large dataset.

## The report

`report.html` is rewritten after every measurement, so an interrupted run still leaves a readable
report of what completed. Collect it with:

```bash
./gradlew :data:pullDataStoreBenchmarkReport
```

which writes `report.html` next to this file and refreshes
`src/androidTest/assets/datastore_benchmark_baseline.json`. Commit both.

Results are **merged across runs** by (implementation, scenario, dataset). Benchmarking a new
algorithm months from now, in a run that executes only its own test class, still produces a report
with every column side by side — re-running an implementation replaces its own rows and leaves the
others alone. `datastore.bench.reset=true` ignores the history and starts clean.

The history is carried by that committed asset rather than by anything on the device, because
nothing on the device survives: AGP clears `additionalTestOutputDir` before every connected run, and
it re-installs the test APK, which takes the app's internal *and* external directories with it.
Keeping the state on-device would silently reduce every report to whichever tests that one run
happened to execute. In the repository it also shows up in diffs, which is where a performance
regression should be visible.

## What is measured

Speed is the median of N timed iterations (min / p90 / max are in the cell tooltip and the raw
appendix), after warmups, with setup and teardown outside the measured window.

Memory is measured in a second pass so the probes never contaminate the timings, and reports three
different things:

- **Allocation churn** — the delta of ART's `art.gc.bytes-allocated`: every byte the process handed
  out during the operation, surviving or not. This is the honest cost of an encrypted write (the
  intermediate JSON `String`, the cipher's output array, the Base64 expansion). It is process-wide
  because DataStore works on its own dispatcher threads, where a thread-local counter would miss it.
- **Retained** — settled heap growth across the pass: what the operation left behind, such as the
  document DataStore now caches in memory.
- **PSS Δ** — resident footprint, which also catches native allocations made below the Java heap.

Scenarios worth knowing about before reading the numbers:

- **Cold vs warm read.** A live DataStore serves every read after the first from memory, so warm
  reads are identical across implementations by construction — on the read path of a running app,
  encryption costs nothing after the first read.
- **No-op update.** `updateData { it }` skips the write once `equals()` matches, and the test
  asserts the file's size and mtime are unchanged. It is not free, though: on the emulator run this
  suite was developed against, a no-op update of the Large document cost as much as a *cold read*
  (~118 ms, ~54 MB allocated) while writing nothing. These stores are created with
  `MultiProcessDataStoreFactory`, exactly as production does, and it re-reads and re-decrypts the
  current value under its file lock before running the transform. Budget every `updateData` at a
  decrypt plus a write, whether or not the data changed — check the row against your own device.
- **Partial update.** See below.

## Partial updates

`partialUpdateOverhead_*` answers whether changing one field of a megabyte document rewrites the
whole encrypted file, and records the evidence rather than a claim: bytes changed, longest identical
prefix, whether the inode was replaced, and the timing of one field against a whole-document replace
and against a no-op.

The finding lands in the report as a verdict paragraph with the numbers behind it.

## Caveats

`androidTest` of a library variant is debuggable, and CI usually runs it on an emulator — both of
which AndroidX benchmark treats as hard errors. `data/build.gradle.kts` suppresses them so the suite
runs anywhere, at the cost of inflated absolute numbers. Rows in one report are comparable with each
other; before quoting a figure as a production timing, run on a physical device.

The correctness tests in `crypto_test/` are not optional. `EncryptedJsonSerializer` fails soft — an
undecryptable blob reads back as the default value instead of throwing — so a broken decrypt would
leave every timing test green while measuring the cost of returning a default. The cold-read
scenario re-checks the round trip for every implementation before timing it.
