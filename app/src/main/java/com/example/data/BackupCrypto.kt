package com.example.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Amplop terenkripsi untuk berkas cadangan otomatis.
 *
 * Format v2 (dipakai untuk semua penulisan baru): kunci AES-256 = SHA-256(passphrase), lalu AES-GCM
 * dengan AAD = format|jenis, sehingga berkas tidak bisa ditukar antar-jenis.
 *
 * Format v1 (PBKDF2 + salt) hanya dibaca untuk cadangan yang sudah ada sebelum perubahan ini.
 */
object BackupCrypto {
    const val FORMAT = "ketikku-enc-v2"
    const val LEGACY_FORMAT = "ketikku-enc-v1"
    const val LEGACY_ITERATIONS = 600_000
    const val MIN_PASSPHRASE_CHARS = 12
    private const val LEGACY_SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_CHARS = 8 * 1024 * 1024
    private val random = SecureRandom()

    /** Kunci AES-256 = SHA-256 dari passphrase (UTF-8). Tanpa salt dan tanpa iterasi. */
    fun deriveKey(passphrase: CharArray): SecretKey {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "Passphrase minimal $MIN_PASSPHRASE_CHARS karakter" }
        val bytes = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            return SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(bytes), "AES")
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Jenis lama yang tersimpan di amplop cadangan lama -> jenis baru. Hanya untuk membaca;
     * penulisan selalu memakai jenis baru.
     */
    val LEGACY_KIND_ALIASES: Map<String, String> = mapOf(
        "kata_sandi" to "pw",
        "kredensial" to "kred",
        "a2f" to "aut",
        "a2f_auto" to "aut_terenkripsi"
    )

    /** Jenis yang dipakai untuk membuka amplop: jenis yang tersimpan jika itu alias lama dari [kind]. */
    fun readKindFor(stored: String, kind: String): String =
        if (LEGACY_KIND_ALIASES[stored] == kind) stored else kind

    fun isEnvelope(raw: String): Boolean = formatOf(raw) != null

    fun isLegacy(raw: String): Boolean = formatOf(raw) == LEGACY_FORMAT

    fun kindOf(raw: String): String? = try {
        JSONObject(raw).takeIf { formatOf(raw) != null }?.getString("kind")
    } catch (_: Exception) {
        null
    }

    private fun formatOf(raw: String): String? = try {
        JSONObject(raw).optString("format").takeIf { it == FORMAT || it == LEGACY_FORMAT }
    } catch (_: Exception) {
        null
    }

    fun seal(key: SecretKey, kind: String, plain: String): String {
        require(plain.length <= MAX_CHARS) { "Cadangan terlalu besar" }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val encrypted = encryptWith(key, FORMAT, kind, iv, plain)
        return JSONObject()
            .put("format", FORMAT)
            .put("kind", kind)
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    /** Melempar exception jika passphrase salah, jenis tidak cocok, atau berkas diubah. */
    fun open(key: SecretKey, raw: String, expectedKind: String): String {
        require(raw.length <= MAX_CHARS) { "Cadangan terlalu besar" }
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Format cadangan terenkripsi tidak didukung" }
        return decryptWith(key, FORMAT, root, expectedKind)
    }

    /** Membaca amplop v1 (PBKDF2). Hanya untuk kompatibilitas cadangan lama. */
    fun openLegacy(passphrase: CharArray, raw: String, expectedKind: String): String {
        require(raw.length <= MAX_CHARS) { "Cadangan terlalu besar" }
        val root = JSONObject(raw)
        require(root.optString("format") == LEGACY_FORMAT && root.getInt("iterations") == LEGACY_ITERATIONS) {
            "Format cadangan terenkripsi tidak didukung"
        }
        val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
        require(salt.size == LEGACY_SALT_BYTES) { "Cadangan terenkripsi rusak" }
        val key = deriveLegacyKey(passphrase, salt)
        return decryptWith(key, LEGACY_FORMAT, root, expectedKind)
    }

    /** Hanya untuk tes: menghasilkan amplop v1 seperti versi lama. */
    internal fun sealLegacy(passphrase: CharArray, salt: ByteArray, kind: String, plain: String): String {
        val key = deriveLegacyKey(passphrase, salt)
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val encrypted = encryptWith(key, LEGACY_FORMAT, kind, iv, plain)
        return JSONObject()
            .put("format", LEGACY_FORMAT)
            .put("kind", kind)
            .put("iterations", LEGACY_ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    private fun deriveLegacyKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "Passphrase minimal $MIN_PASSPHRASE_CHARS karakter" }
        val spec = PBEKeySpec(passphrase.copyOf(), salt, LEGACY_ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    private fun encryptWith(key: SecretKey, format: String, kind: String, iv: ByteArray, plain: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(format, kind))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    private fun decryptWith(key: SecretKey, format: String, root: JSONObject, expectedKind: String): String {
        require(root.getString("kind") == expectedKind) { "Jenis cadangan tidak cocok" }
        val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        require(iv.size == IV_BYTES && encrypted.size >= 16) { "Cadangan terenkripsi rusak" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(format, expectedKind))
        val plain = try { cipher.doFinal(encrypted) } catch (_: Exception) {
            error("Passphrase salah atau cadangan rusak; data tidak diubah")
        }
        return String(plain, Charsets.UTF_8)
    }

    private fun aad(format: String, kind: String): ByteArray = "$format|$kind".toByteArray(Charsets.UTF_8)
}

/**
 * Menyimpan passphrase cadangan dalam bentuk terbungkus Keystore (SecretVault.wrapText) di SharedPreferences.
 * Kunci turunan di-cache di memori selama proses hidup. Tanpa passphrase, cadangan terenkripsi tidak bisa dibaca.
 */
class BackupCipher(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var sessionKey: SecretKey? = null

    fun isConfigured(): Boolean = prefs.getString(KEY_PASSPHRASE, null) != null

    private fun storedPassphrase(): CharArray? =
        prefs.getString(KEY_PASSPHRASE, null)?.let { SecretVault.unwrapTextOrNull(it) }?.toCharArray()

    @Synchronized
    private fun keyOrNull(): SecretKey? {
        sessionKey?.let { return it }
        val passphrase = storedPassphrase() ?: return null
        return BackupCrypto.deriveKey(passphrase).also { sessionKey = it }
    }

    /** Membuat passphrase baru untuk perangkat ini. */
    @Synchronized
    fun setup(passphrase: CharArray) {
        require(passphrase.size >= BackupCrypto.MIN_PASSPHRASE_CHARS) { "Passphrase minimal 12 karakter" }
        val key = BackupCrypto.deriveKey(passphrase)
        store(passphrase)
        sessionKey = key
    }

    /** Membuka passphrase yang sudah ada (mis. setelah pasang ulang) dan memverifikasinya dengan amplop cadangan. */
    @Synchronized
    fun unlock(passphrase: CharArray, envelope: String) {
        require(passphrase.size >= BackupCrypto.MIN_PASSPHRASE_CHARS) { "Passphrase minimal 12 karakter" }
        val kind = BackupCrypto.kindOf(envelope) ?: error("Format cadangan terenkripsi tidak valid")
        val key = if (BackupCrypto.isLegacy(envelope)) {
            BackupCrypto.openLegacy(passphrase.copyOf(), envelope, kind) // verifikasi sebelum menyimpan apa pun
            null
        } else {
            BackupCrypto.deriveKey(passphrase).also { BackupCrypto.open(it, envelope, kind) }
        }
        store(passphrase)
        sessionKey = key
    }

    fun encrypt(kind: String, plain: String): String {
        val key = keyOrNull() ?: error("Enkripsi cadangan belum aktif")
        return BackupCrypto.seal(key, kind, plain)
    }

    fun decrypt(kind: String, raw: String): String {
        val readKind = BackupCrypto.kindOf(raw)?.let { BackupCrypto.readKindFor(it, kind) } ?: kind
        if (BackupCrypto.isLegacy(raw)) {
            val passphrase = storedPassphrase() ?: error("Buka cadangan terenkripsi dengan passphrase dulu")
            return BackupCrypto.openLegacy(passphrase, raw, readKind)
        }
        val key = keyOrNull() ?: error("Buka cadangan terenkripsi dengan passphrase dulu")
        return BackupCrypto.open(key, raw, readKind)
    }

    private fun store(passphrase: CharArray) {
        val wrapped = SecretVault.wrapText(String(passphrase))
        val saved = prefs.edit()
            .putString(KEY_PASSPHRASE, wrapped)
            .commit()
        check(saved) { "Gagal menyimpan passphrase cadangan" }
    }

    private companion object {
        const val PREFS_NAME = "ketikku_backup_cipher"
        const val KEY_PASSPHRASE = "passphrase_wrapped"
    }
}
