package com.example.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi seed 2FA dengan AES-256-GCM. Kunci diturunkan dari konstanta aplikasi (SHA-256),
 * jadi berkas ketikku_a2f.json bisa dipulihkan di perangkat lain tanpa input ulang.
 * Ringan: tidak ada IPC Keystore, kunci dihitung sekali.
 * Format hasil: base64(iv) + ":" + base64(ciphertext). Plaintext seed tidak pernah disimpan.
 */
object SecretVault {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val KEY_MATERIAL = "KetikKu-A2F-seed-v1"

    private val secretKey: SecretKey by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(KEY_MATERIAL.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    private val random = SecureRandom()

    fun encrypt(plainSecret: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainSecret.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /** Null jika kosong atau format/kunci tidak cocok. */
    fun decryptOrNull(cipherText: String): String? {
        if (cipherText.isBlank()) return null
        return try {
            val parts = cipherText.split(':')
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val bytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
