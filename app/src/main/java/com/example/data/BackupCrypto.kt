package com.example.data

import android.content.Context
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * Pembungkus cadangan terenkripsi di atas [Vx2Codec] (format VAULTX04, `.vx2`).
 *
 * Berkas `.vx2` adalah biner murni; isinya dibawa sebagai String Latin-1 (1 byte = 1 char) agar
 * lossless melewati jalur teks penyimpanan. Setiap berkas memakai salt sendiri, jadi setiap
 * enkripsi/dekripsi menjalankan Argon2id (~256 MiB) sekali.
 *
 * Keterbatasan: VAULTX04 tidak punya AAD per jenis, jadi berkas `.vx2` yang ditukar antar-jenis
 * tidak terdeteksi oleh format ini.
 */
object BackupCrypto {
    const val MIN_PASSPHRASE_CHARS = 12
    const val MAGIC = "VAULTX04"
    private const val MAX_BYTES = 64 * 1024 * 1024

    /** Byte ke String (Latin-1) untuk disimpan lewat jalur teks. */
    fun toStorage(bytes: ByteArray): String = String(bytes, StandardCharsets.ISO_8859_1)

    /** String Latin-1 kembali ke byte asli. */
    fun fromStorage(raw: String): ByteArray = raw.toByteArray(StandardCharsets.ISO_8859_1)

    /** True jika isi berkas adalah amplop VAULTX04. */
    fun isEnvelope(raw: String): Boolean = raw.startsWith(MAGIC)

    /**
     * True jika isi berkas adalah amplop JSON lama (`ketikku-enc-v1/v2`, PBKDF2 atau SHA-256).
     * Format itu tidak dimigrasi; berkas hanya dikenali agar tidak dianggap data kamus.
     */
    fun isLegacyJsonEnvelope(raw: String): Boolean {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("{")) return false
        return try {
            JSONObject(trimmed).optString("format").startsWith("ketikku-enc-")
        } catch (_: Exception) {
            false
        }
    }

    fun seal(passphrase: ByteArray, plain: String): String {
        require(plain.length <= MAX_BYTES) { "Cadangan terlalu besar" }
        return toStorage(Vx2Codec.encrypt(plain.toByteArray(StandardCharsets.UTF_8), passphrase))
    }

    /** Melempar exception jika passphrase salah atau berkas rusak. */
    fun open(passphrase: ByteArray, raw: String): String {
        require(isEnvelope(raw)) { "Format cadangan terenkripsi tidak didukung" }
        require(raw.length <= MAX_BYTES) { "Cadangan terlalu besar" }
        return String(Vx2Codec.decrypt(fromStorage(raw), passphrase), StandardCharsets.UTF_8)
    }

    fun passphraseBytes(passphrase: CharArray): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "Passphrase minimal $MIN_PASSPHRASE_CHARS karakter" }
        return String(passphrase).toByteArray(StandardCharsets.UTF_8)
    }
}

/**
 * Menyimpan passphrase cadangan dalam bentuk terbungkus Keystore (SecretVault.wrapText) di SharedPreferences.
 * Byte passphrase di-cache di memori selama proses hidup. Tanpa passphrase, cadangan terenkripsi tidak bisa dibaca.
 */
class BackupCipher(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var sessionPassphrase: ByteArray? = null

    fun isConfigured(): Boolean = prefs.getString(KEY_PASSPHRASE, null) != null

    private fun storedPassphrase(): CharArray? =
        prefs.getString(KEY_PASSPHRASE, null)?.let { SecretVault.unwrapTextOrNull(it) }?.toCharArray()

    @Synchronized
    private fun passwordOrNull(): ByteArray? {
        sessionPassphrase?.let { return it }
        val chars = storedPassphrase() ?: return null
        return try {
            BackupCrypto.passphraseBytes(chars).also { sessionPassphrase = it }
        } finally {
            chars.fill('\u0000')
        }
    }

    /** Membuat passphrase baru untuk perangkat ini. */
    @Synchronized
    fun setup(passphrase: CharArray) {
        val bytes = BackupCrypto.passphraseBytes(passphrase)
        store(passphrase)
        sessionPassphrase = bytes
    }

    /** Membuka passphrase yang sudah ada (mis. setelah pasang ulang) dan memverifikasinya dengan amplop cadangan. */
    @Synchronized
    fun unlock(passphrase: CharArray, envelope: String) {
        val bytes = BackupCrypto.passphraseBytes(passphrase)
        BackupCrypto.open(bytes, envelope) // verifikasi sebelum menyimpan apa pun
        store(passphrase)
        sessionPassphrase = bytes
    }

    /** Enkripsi memakai passphrase tersimpan. */
    fun encrypt(plain: String): String {
        val pw = passwordOrNull() ?: error("Enkripsi cadangan belum aktif")
        return BackupCrypto.seal(pw, plain)
    }

    /** Dekripsi memakai passphrase tersimpan. Melempar exception jika tidak cocok. */
    fun decrypt(raw: String): String {
        val pw = passwordOrNull() ?: error("Buka cadangan terenkripsi dengan passphrase dulu")
        return BackupCrypto.open(pw, raw)
    }

    /** Enkripsi dengan passphrase yang belum disimpan (untuk penggantian passphrase). */
    fun encryptWith(passphrase: CharArray, plain: String): String =
        BackupCrypto.seal(BackupCrypto.passphraseBytes(passphrase), plain)

    /** Dekripsi dengan passphrase yang diberikan (tanpa menyimpannya). */
    fun decryptWith(passphrase: CharArray, raw: String): String =
        BackupCrypto.open(BackupCrypto.passphraseBytes(passphrase), raw)

    /** Menyimpan passphrase baru sebagai passphrase aktif. Dipanggil setelah semua berkas berhasil ditulis ulang. */
    @Synchronized
    fun commit(passphrase: CharArray) {
        val bytes = BackupCrypto.passphraseBytes(passphrase)
        store(passphrase)
        sessionPassphrase = bytes
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
