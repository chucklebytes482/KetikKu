package com.example.data

import com.example.model.PersonalDictionaryEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Payload A2F untuk cadangan otomatis. Enkripsi dilakukan oleh BackupCrypto (AES-GCM, kunci SHA-256 passphrase).
 * Tidak ada ekspor/impor berpassword terpisah.
 */
object A2fBackup {
    /** Isi A2F aktif sebagai JSON array (id, shortcut, seed). Seed dibaca dari Keystore. */
    fun payloadJson(entries: List<PersonalDictionaryEntry>): String {
        val payload = JSONArray()
        entries.filter { DictionaryEngine.isA2fEntry(it) && !it.deleted }.forEach { entry ->
            val seed = SecretVault.decryptOrNull(entry.secretCipher)
                ?: error("Seed akun tidak bisa dibaca; cadangan dibatalkan tanpa mengubah data")
            payload.put(JSONObject().put("id", entry.id).put("shortcut", entry.shortcut)
                .put("word", entry.word).put("seed", seed).put("updatedAtMillis", entry.updatedAtMillis))
        }
        return payload.toString()
    }

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
            id = row.getString("id"), word = row.optString("word", "").ifBlank { "••••••••" }, shortcut = row.getString("shortcut").trim(),
            folder = SettingsRepository.FOLDER_A2F,
            updatedAtMillis = row.optLong("updatedAtMillis", 0L),
            secretCipher = SecretVault.encrypt(row.getString("seed"))
        ) }
    }
}
