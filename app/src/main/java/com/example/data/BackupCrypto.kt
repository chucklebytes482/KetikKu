package com.example.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Amplop terenkripsi untuk berkas cadangan otomatis. Satu passphrase menurunkan satu kunci
 * (PBKDF2 + salt tersimpan di setiap amplop), lalu AES-256-GCM dengan AAD = format|jenis,
 * sehingga berkas tidak bisa ditukar antar-jenis.
 */
object BackupCrypto {
    const val FORMAT = "ketikku-enc-v1"
    const val ITERATIONS = 600_000
    const val MIN_PASSPHRASE_CHARS = 12
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_CHARS = 8 * 1024 * 1024
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    /** PBKDF2-HMAC-SHA1: didukung sejak API 24 (SHA-256 baru tersedia di API 26+). */
    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "Passphrase minimal $MIN_PASSPHRASE_CHARS karakter" }
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    fun isEnvelope(raw: String): Boolean = try {
        JSONObject(raw).optString("format") == FORMAT
    } catch (_: Exception) {
        false
    }

    fun kindOf(raw: String): String? = try {
        JSONObject(raw).takeIf { it.optString("format") == FORMAT }?.getString("kind")
    } catch (_: Exception) {
        null
    }

    fun saltOf(raw: String): ByteArray? = try {
        JSONObject(raw).takeIf { it.optString("format") == FORMAT }
            ?.let { Base64.decode(it.getString("salt"), Base64.NO_WRAP) }
            ?.takeIf { it.size == SALT_BYTES }
    } catch (_: Exception) {
        null
    }

    fun seal(key: SecretKey, salt: ByteArray, kind: String, plain: String): String {
        require(plain.length <= MAX_CHARS) { "Cadangan terlalu besar" }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(kind))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("kind", kind)
            .put("iterations", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    /** Melempar exception jika passphrase salah, jenis tidak cocok, atau berkas diubah. */
    fun open(key: SecretKey, raw: String, expectedKind: String): String {
        require(raw.length <= MAX_CHARS) { "Cadangan terlalu besar" }
        val root = JSONObject(raw)
        require(root.getString("format") == FORMAT && root.getInt("iterations") == ITERATIONS) {
            "Format cadangan terenkripsi tidak didukung"
        }
        require(root.getString("kind") == expectedKind) { "Jenis cadangan tidak cocok" }
        val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        require(iv.size == IV_BYTES && encrypted.size >= 16) { "Cadangan terenkripsi rusak" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(expectedKind))
        val plain = try { cipher.doFinal(encrypted) } catch (_: Exception) {
            error("Passphrase salah atau cadangan rusak; data tidak diubah")
        }
        return String(plain, Charsets.UTF_8)
    }

    private fun aad(kind: String): ByteArray = "$FORMAT|$kind".toByteArray(Charsets.UTF_8)
}

/**
 * Menyimpan passphrase cadangan dalam bentuk terbungkus Keystore (SecretVault.wrapText) di SharedPreferences.
 * Kunci turunan di-cache di memori selama proses hidup. Tanpa passphrase, cadangan terenkripsi tidak bisa dibaca.
 */
class BackupCipher(context: Context) {
    private class Session(val key: SecretKey, val salt: ByteArray)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var session: Session? = null

    fun isConfigured(): Boolean =
        prefs.getString(KEY_SALT, null) != null && prefs.getString(KEY_PASSPHRASE, null) != null

    @Synchronized
    private fun sessionOrNull(): Session? {
        session?.let { return it }
        if (!isConfigured()) return null
        val salt = Base64.decode(prefs.getString(KEY_SALT, null)!!, Base64.NO_WRAP)
        val passphrase = SecretVault.unwrapTextOrNull(prefs.getString(KEY_PASSPHRASE, null)!!) ?: return null
        return Session(BackupCrypto.deriveKey(passphrase.toCharArray(), salt), salt).also { session = it }
    }

    /** Membuat passphrase baru untuk perangkat ini. */
    @Synchronized
    fun setup(passphrase: CharArray) {
        require(passphrase.size >= BackupCrypto.MIN_PASSPHRASE_CHARS) { "Passphrase minimal 12 karakter" }
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(passphrase, salt)
        store(salt, passphrase)
        session = Session(key, salt)
    }

    /** Membuka passphrase yang sudah ada (mis. setelah pasang ulang) memakai salt dari amplop cadangan. */
    @Synchronized
    fun unlock(passphrase: CharArray, envelope: String) {
        require(passphrase.size >= BackupCrypto.MIN_PASSPHRASE_CHARS) { "Passphrase minimal 12 karakter" }
        val salt = BackupCrypto.saltOf(envelope) ?: error("Format cadangan terenkripsi tidak valid")
        val kind = BackupCrypto.kindOf(envelope) ?: error("Format cadangan terenkripsi tidak valid")
        val key = BackupCrypto.deriveKey(passphrase, salt)
        BackupCrypto.open(key, envelope, kind) // verifikasi sebelum menyimpan apa pun
        store(salt, passphrase)
        session = Session(key, salt)
    }

    fun encrypt(kind: String, plain: String): String {
        val current = sessionOrNull() ?: error("Enkripsi cadangan belum aktif")
        return BackupCrypto.seal(current.key, current.salt, kind, plain)
    }

    fun decrypt(kind: String, raw: String): String {
        val current = sessionOrNull() ?: error("Buka cadangan terenkripsi dengan passphrase dulu")
        return BackupCrypto.open(current.key, raw, kind)
    }

    private fun store(salt: ByteArray, passphrase: CharArray) {
        val wrapped = SecretVault.wrapText(String(passphrase))
        val saved = prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PASSPHRASE, wrapped)
            .commit()
        check(saved) { "Gagal menyimpan passphrase cadangan" }
    }

    private companion object {
        const val PREFS_NAME = "ketikku_backup_cipher"
        const val KEY_SALT = "salt"
        const val KEY_PASSPHRASE = "passphrase_wrapped"
    }
}
