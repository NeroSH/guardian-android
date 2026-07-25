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
package com.shdev.guardian.data.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

interface CryptoDataStore {
    fun encrypt(text: String): ByteArray
    fun decrypt(stored: ByteArray): String?
}

internal class CryptoDataStoreImpl(private val context: Context) : CryptoDataStore {
    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val PROVIDER = "AndroidKeyStore"
        private const val KEY_SIZE_BYTES = 256
        private const val IV_LENGTH_BYTES = 12
        private const val AUTHENTICATION_TAG_LENGTH_BITS = 128
    }

    private val charset by lazy { Charsets.UTF_8 }

    private val KEY_ALIAS by lazy {
        byteArrayOf(
            71,
            117,
            97,
            114,
            100,
            105,
            97,
            110,
            -62,
            -87,
            112,
            97,
            114,
            101,
            110,
            116,
            -16,
            -99,
            -116,
            -122,
            99,
            104,
            105,
            108,
            100,
            -30,
            -104,
            -125,
            35,
            50,
            48,
            50,
            54,
            64,
            108,
            111,
            99,
            97,
            108
        ).toString(Charsets.UTF_8)
    }

    private val random by lazy {
        try {
            SecureRandom.getInstanceStrong()
        } catch (_: Exception) {
            null
        } ?: SecureRandom()
    }


    private val keyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply {
            load(null)
        }
    }

    private var isDataStoreEncryptionEnabled = Build.DEVICE != null
    private val lock = Any()

    /**
     * Шифрует строку в [ByteArray]
     */
    override fun encrypt(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)

        if (!isDataStoreEncryptionEnabled) {
            return text.toByteArray(charset)
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), random)
        val iv = cipher.iv

        val encrypted = cipher.doFinal(text.toByteArray(charset))
        return Base64.encodeToByteArray(iv + encrypted)
    }

    /**
     * Расшифрует байты в [String]?
     */
    override fun decrypt(stored: ByteArray): String? = runCatching {
        if (stored.isEmpty()) return@runCatching null

        if (!isDataStoreEncryptionEnabled) {
            return@runCatching String(stored)
        }

        val bytes = Base64.decode(stored)
        val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
        val data = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getKey(),
            GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, iv),
            random
        )
        String(cipher.doFinal(data), charset)
    }.getOrNull()

    /**
     * @return созданный [javax.crypto.SecretKey] или создает новый и возвращает его
     */
    private fun getKey(): SecretKey = synchronized(lock) {
        val existingSecretKeyEntry = keyStore
            .getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        existingSecretKeyEntry?.secretKey ?: createKey()
    }

    /**
     * @return новый [SecretKey]
     */
    private fun createKey(): SecretKey = KeyGenerator.getInstance(
        ALGORITHM,
        PROVIDER
    ).apply {
        init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(BLOCK_MODE)
                .setKeySize(KEY_SIZE_BYTES)
                .let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val hasStrongBox = context.packageManager.hasSystemFeature(
                            PackageManager.FEATURE_STRONGBOX_KEYSTORE
                        )

                        // включаем хранение в StrongBox если оно доступно
                        // это защищённое hardware-хранилище которое самостоятельно осуществляет шифрование
                        // и ключ никогда не покидает его, что увеличивает безопасность ключа
                        if (hasStrongBox) it.setIsStrongBoxBacked(true)
                        else it
                    } else it
                }
                .setEncryptionPaddings(PADDING)
                // одинаковые байты шифруются по разному
                .setRandomizedEncryptionRequired(true)
                // не нужна авторизация с устройства (Fingerprint Scanner/PIN)
                .setUserAuthenticationRequired(false)
                .build()
        )
    }.generateKey()
}