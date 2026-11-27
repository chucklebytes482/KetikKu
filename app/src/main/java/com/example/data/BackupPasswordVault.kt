package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Kata sandi cadangan yang diingat di perangkat. Dienkripsi dengan kunci AES-GCM
 * non-exportable di Android Keystore. Format: k1:base64(iv):base64(ciphertext).
 */
/** Pembungkus kata sandi tersimpan. Dipisah agar manajer cadangan bisa diuji tanpa Android Keystore. */
interface BackupPasswordCipher {
    fun encrypt(password: String): String
    /** Null jika nilai tersimpan tidak bisa dibuka (format salah atau kunci sudah tidak ada). */
    fun decryptOrNull(value: String): String?
}

object BackupPasswordVault : BackupPasswordCipher {
    private const val ALIAS = "ketikku.backup.password.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    @Synchronized
    private fun deviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    override fun encrypt(password: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val bytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return "k1:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Null jika kosong, format tidak cocok, atau kunci sudah tidak ada. */
    override fun decryptOrNull(value: String): String? {
        if (!value.startsWith("k1:")) return null
        return try {
            val parts = value.removePrefix("k1:").split(':')
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val bytes = Base64.decode(parts[1], Base64.NO_WRAP)
            if (iv.size != IV_BYTES || bytes.isEmpty()) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
