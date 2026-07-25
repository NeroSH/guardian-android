# Adding an encryption implementation

Two files. The suite, the datasets, the memory probes and the report are already written against
[`EncryptedDataStoreBenchmark`](../EncryptedDataStoreBenchmark.kt) and do not change.

## 1. The target

Copy [`TemplateEncryptionTarget.kt`](TemplateEncryptionTarget.kt), rename it after the algorithm,
and fill in `id`, `displayName`, `description`, `serializer()` and `rawCodec()`.

```kotlin
class TinkAeadTarget : EncryptedDataStoreBenchmark {
    override val id = "tink_aes_gcm_hkdf_streaming"
    override val displayName = "Tink AES-GCM-HKDF streaming"
    override val description = "Tink StreamingAead, keyset in AndroidKeyStore, raw bytes on disk."

    private val aead by lazy { /* build the keyset once, as the app would */ }

    override fun serializer(context: Context, defaultValue: BenchmarkPayload) =
        TinkJsonSerializer(aead, defaultValue, json)

    override fun rawCodec(context: Context) = object : EncryptedDataStoreBenchmark.RawCodec {
        override fun encrypt(plain: String) = aead.encrypt(plain.toByteArray(), null)
        override fun decrypt(bytes: ByteArray) = String(aead.decrypt(bytes, null))
    }
}
```

## 2. The test

```kotlin
@RunWith(AndroidJUnit4::class)
class TinkAeadBenchmarkTest : AbstractEncryptedDataStoreBenchmarkTest() {
    override val target: EncryptedDataStoreBenchmark = TinkAeadTarget()
}
```

Run it. The column appears in `report.html` next to the existing ones, with deltas against whichever
implementation declares `isReference = true` — today
[`CryptoDataStoreTarget`](../crypto_test/CryptoDataStoreTarget.kt).

## Rules for a fair column

- **`id` is permanent.** Records merge across runs by that key; changing it orphans the old column
  instead of updating it.
- **Build key material once, on the instance.** A `KeysetHandle` or `SecretKey` loaded per call
  reports as a slow algorithm when it is really a repeated setup the app pays once.
- **Keep the document format.** Same JSON, same `BenchmarkPayload`, same fail-soft read. Only the
  bytes on disk may differ — otherwise the columns measure different work.
- **Prefer overriding `serializer()` to `createStore()`.** The default `createStore()` uses the same
  `MultiProcessDataStoreFactory` production does. Override it only for an implementation that does
  not fit behind a DataStore `Serializer` at all, and say so in `description`, because the column is
  then no longer measuring the same write path.
- **Return `null` from `rawCodec()`** only when there is no separable primitive. The codec-only rows
  are skipped for that column; see [`NoEncryptionBaselineTarget`](NoEncryptionBaselineTarget.kt).

## What to compare against

[`NoEncryptionBaselineTarget`](NoEncryptionBaselineTarget.kt) runs the identical path with no
encryption. It is the floor: the gap between it and any encrypting column is the price of
encryption, with DataStore's atomic write, kotlinx.serialization and the file system already
subtracted.
