package com.example.data

import android.util.Base64
import com.example.model.PersonalDictionaryEntry
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password is used only during this operation, never saved in preferences or Keystore. */
object A2fBackup {
    private const val FORMAT = "ketikku-a2f-password-v1"
    private const val ITERATIONS = 600_000
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val random = SecureRandom()

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        // HMAC-SHA1 PBKDF2 supports Android API 24; encryption is AES-256-GCM.
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    /** Isi A2F aktif sebagai JSON array (id, shortcut, seed). Seed dibaca dari Keystore. */
    fun payloadJson(entries: List<PersonalDictionaryEntry>): String {
        val payload = JSONArray()
        entries.filter { DictionaryEngine.isA2fEntry(it) && !it.deleted }.forEach { entry ->
            val seed = SecretVault.decryptOrNull(entry.secretCipher)
                ?: error("Seed akun tidak bisa dibaca; ekspor dibatalkan tanpa mengubah data")
            payload.put(JSONObject().put("id", entry.id).put("shortcut", entry.shortcut)
                .put("seed", seed).put("updatedAtMillis", entry.updatedAtMillis))
        }
        return payload.toString()
    }

    fun encode(entries: List<PersonalDictionaryEntry>, password: CharArray): String {
        require(password.size >= 12) { "Password cadangan minimal 12 karakter" }
        val plain = payloadJson(entries).toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_BYTES) { "Cadangan A2F terlalu besar" }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        return JSONObject().put("format", FORMAT).put("iterations", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString(2)
    }

    internal fun decryptPayload(raw: String, password: CharArray): JSONArray {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES * 2) { "Cadangan A2F terlalu besar" }
        val root = JSONObject(raw)
        require(root.getString("format") == FORMAT && root.getInt("iterations") == ITERATIONS) {
            "Format cadangan A2F tidak didukung"
        }
        val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
        require(salt.size == 16 && iv.size == 12 && encrypted.size >= 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
        val plain = try { cipher.doFinal(encrypted) } catch (_: Exception) {
            error("Password salah atau cadangan rusak; data tidak diubah")
        }
        val payload = try { JSONArray(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
        return payload
    }

    fun decode(raw: String, password: CharArray): List<PersonalDictionaryEntry> =
        entriesFromPayload(decryptPayload(raw, password))

    /** Dipakai cadangan A2F otomatis: payload plaintext hasil dekripsi amplop. */
    fun entriesFromPayloadJson(raw: String): List<PersonalDictionaryEntry> = entriesFromPayload(JSONArray(raw))

    private fun entriesFromPayload(payload: JSONArray): List<PersonalDictionaryEntry> {
        val ids = mutableSetOf<String>()
        val shortcuts = mutableSetOf<String>()
        // Validate entire payload before creating any local ciphertext.
        val rows = (0 until payload.length()).map { payload.getJSONObject(it) }
        rows.forEach { row ->
            require(row.getString("id").isNotBlank() && ids.add(row.getString("id")))
            val shortcut = row.getString("shortcut").trim().lowercase(java.util.Locale.ROOT)
            require(shortcut.isNotBlank() && shortcut.length <= 30 && shortcut.all { it.isLetterOrDigit() } && shortcuts.add(shortcut)) { "Pintasan A2F duplikat" }
            require(TotpGenerator.isValidBase32Secret(row.getString("seed"))) { "Seed A2F tidak valid" }
        }
        return rows.map { row -> PersonalDictionaryEntry(
            id = row.getString("id"), word = "••••••••", shortcut = row.getString("shortcut").trim(),
            folder = SettingsRepository.FOLDER_A2F,
            updatedAtMillis = row.optLong("updatedAtMillis", 0L),
            secretCipher = SecretVault.encrypt(row.getString("seed"))
        ) }
    }
}
