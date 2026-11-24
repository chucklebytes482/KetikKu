package com.example.data

import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seed lokal memakai kunci AES-GCM non-exportable di Android Keystore.
 * Format v2: k2:base64(iv):base64(ciphertext). Cadangan portabel memakai password terpisah.
 * Kunci v1 hanya untuk membaca/migrasi data lama, tidak untuk menulis data baru.
 */
object SecretVault {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val KEY_MATERIAL = "KetikKu-A2F-seed-v1"

    private val legacyKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(KEY_MATERIAL.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    @Synchronized
    private fun deviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("ketikku.a2f.v2", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                "ketikku.a2f.v2", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    fun isDeviceCipher(value: String): Boolean = value.startsWith("k2:")

    fun encrypt(plainSecret: String): String {
        require(TotpGenerator.isValidBase32Secret(plainSecret)) { "Seed A2F tidak valid" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val cipherBytes = cipher.doFinal(TotpGenerator.normalizeBase32(plainSecret).toByteArray(Charsets.UTF_8))
        return "k2:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /** Membungkus teks (mis. passphrase cadangan) dengan kunci Keystore. Format: w1:base64(iv):base64(ciphertext). */
    fun wrapText(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "w1:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /** Null jika format tidak cocok atau kunci Keystore tidak lagi tersedia. */
    fun unwrapTextOrNull(value: String): String? {
        if (!value.startsWith("w1:")) return null
        return try {
            val parts = value.removePrefix("w1:").split(':')
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val bytes = Base64.decode(parts[1], Base64.NO_WRAP)
            if (iv.size != IV_BYTES || bytes.size < 16) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Null jika kosong atau format/kunci tidak cocok. */
    fun decryptOrNull(cipherText: String): String? {
        if (cipherText.isBlank()) return null
        return try {
            val modern = isDeviceCipher(cipherText)
            val parts = cipherText.removePrefix("k2:").split(':')
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val bytes = Base64.decode(parts[1], Base64.NO_WRAP)
            if (iv.size != IV_BYTES || bytes.size < 16) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, if (modern) deviceKey() else legacyKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(bytes), Charsets.UTF_8).takeIf { TotpGenerator.isValidBase32Secret(it) }
        } catch (_: Exception) {
            null
        }
    }
}
