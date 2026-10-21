package com.sokhan.chat.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.sokhan.chat.core.AppLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed implementation of [SecureStore].
 *
 * Design:
 *  - A single AES-256/GCM key ("sokhan.secure") is generated inside the
 *    hardware-backed Android Keystore; it never leaves the device.
 *  - Ciphertext records are stored in one private file (`secure_store.bin`),
 *    itself excluded from Android auto-backup (see backup_rules.xml).
 *  - Each record gets a fresh 12-byte IV, stored next to the ciphertext.
 *
 * If Keystore is corrupt (very rare — e.g. a trie corruption after factory
 * reset restore), we fall back to a fresh key and accept that previously saved
 * keys cannot be decrypted; the user then re-enters them (scenarios documented
 * in SECURITY.md).
 */
class KeystoreSecureStore(
    context: Context,
    private val fileName: String = "secure_store.bin"
) : SecureStore {

    private val alias = "sokhan.secure"
    private val file = File(context.applicationContext.filesDir, fileName)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun loadAll(): MutableMap<String, String> {
        synchronized(lock) {
            if (!file.exists()) return HashMap()
            val raw: String = try {
                runCatching { file.readText() }.getOrNull() ?: return HashMap()
            } catch (t: Throwable) {
                AppLog.e("Secure", "unreadable secure store", t)
                return HashMap()
            }
            if (raw.isBlank()) return HashMap()
            return runCatching {
                json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    raw
                ).toMutableMap()
            }.getOrElse { HashMap() }
        }
    }

    private fun saveAll(values: Map<String, String>) {
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(
                    MapSerializer(String.serializer(), String.serializer()),
                    values
                ))
            }.onFailure { AppLog.e("Secure", "failed writing secure store", it) }
        }
    }

    override fun read(key: String): String? {
        val cipherText = loadAll()[key] ?: return null
        return runCatching { decrypt(cipherText) }
            .onFailure { AppLog.e("Secure", "failing to decrypt value; treat key as missing", it) }
            .getOrNull()
    }

    override fun write(key: String, value: String) {
        synchronized(lock) {
            val values = loadAll()
            values[key] = encrypt(value)
            saveAll(values)
        }
    }

    override fun delete(key: String) {
        synchronized(lock) {
            val values = loadAll()
            values.remove(key)
            saveAll(values)
        }
    }

    override fun deleteAll() {
        synchronized(lock) { file.delete() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(false)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        iv.copyInto(combined, 0)
        encrypted.copyInto(combined, iv.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.getDecoder().decode(value)
        require(combined.size > IV_LENGTH_BYTES) { "corrupt secure record" }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val body = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
