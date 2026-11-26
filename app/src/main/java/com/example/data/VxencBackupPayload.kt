package com.example.data

import com.example.model.PersonalDictionaryEntry
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** The UTF-8 JSON carried by a KetikKu VXENC container, never written as plaintext. */
internal data class VxencBackupPayload(
    val updatedAtMillis: Long,
    val folders: List<String>,
    val entries: List<PortableBackupEntry>
) {
    fun encode(): String = JSONObject()
        .put("format", FORMAT).put("schemaVersion", 1).put("updatedAtMillis", updatedAtMillis)
        .put("folders", JSONArray(folders))
        .put("entries", JSONArray(entries.sortedBy { it.entry.id }.map { it.toJson() }))
        .toString()

    companion object {
        private const val FORMAT = "ketikku-device-backup"

        fun decode(json: String): VxencBackupPayload {
            try {
                val parser = JSONTokener(json)
                val root = parser.nextValue() as JSONObject
                require(parser.nextClean() == '\u0000')
                require(root.get("format") == FORMAT && root.get("schemaVersion") == 1)
                val folders = root.getJSONArray("folders").let { array ->
                    (0 until array.length()).map { array.get(it) as String }
                }
                val seen = HashSet<String>()
                val entries = root.getJSONArray("entries").let { array ->
                    (0 until array.length()).map { index ->
                        PortableBackupEntry.fromJson(array.getJSONObject(index)).also {
                            require(seen.add(it.entry.id))
                        }
                    }
                }
                return VxencBackupPayload(root.timestamp(), folders, entries)
            } catch (_: Exception) {
                throw IllegalArgumentException("Isi VXENC bukan cadangan KetikKu yang didukung atau datanya rusak.")
            }
        }
    }
}

internal data class PortableBackupEntry(val entry: PersonalDictionaryEntry, val seed: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("id", entry.id).put("word", entry.word)
        .put("shortcut", entry.shortcut).put("folder", entry.folder).put("updatedAtMillis", entry.updatedAtMillis)
        .put("deleted", entry.deleted).apply {
            if (entry.trashedFrom.isNotEmpty()) put("trashedFrom", entry.trashedFrom)
            if (seed != null) put("seed", seed)
        }

    companion object {
        fun fromLocal(entry: PersonalDictionaryEntry): PortableBackupEntry {
            val seed = if (!entry.deleted && isTwoFactor(entry)) {
                SecretVault.decryptOrNull(entry.secretCipher)
                    ?: error("Seed A2F tidak dapat dibaca. Cadangan tidak diubah.")
            } else null
            return PortableBackupEntry(entry.copy(secretCipher = ""), seed)
        }

        fun fromJson(row: JSONObject): PortableBackupEntry {
            require(!row.has("secretCipher"))
            val entry = PersonalDictionaryEntry(
                id = row.get("id") as String,
                word = row.get("word") as String,
                shortcut = row.get("shortcut") as String,
                folder = SettingsRepository.normalizeFolderPath(row.get("folder") as String),
                trashedFrom = SettingsRepository.normalizeFolderPath(if (row.has("trashedFrom")) row.get("trashedFrom") as String else ""),
                updatedAtMillis = row.timestamp(),
                deleted = row.get("deleted") as Boolean
            )
            require(entry.id.isNotBlank() && (entry.deleted || entry.word.isNotBlank()))
            val seed = if (row.has("seed")) row.get("seed") as String else null
            require((seed != null) == (!entry.deleted && isTwoFactor(entry)))
            if (seed != null) require(TotpGenerator.isValidBase32Secret(seed))
            if (!entry.deleted && DictionaryEngine.isA2fEntry(entry)) {
                require(entry.shortcut.length <= 30 &&
                    entry.shortcut.all { it.isLetterOrDigit() || it == '.' })
            }
            return PortableBackupEntry(entry, seed?.let(TotpGenerator::normalizeBase32))
        }

        fun isTwoFactor(entry: PersonalDictionaryEntry): Boolean = DictionaryEngine.isA2fEntry(entry) ||
            (entry.folder == SettingsRepository.FOLDER_SAMPAH && entry.trashedFrom == SettingsRepository.FOLDER_A2F)
    }
}

private fun JSONObject.timestamp(): Long = when (val value = get("updatedAtMillis")) {
    is Int -> value.toLong()
    is Long -> value
    else -> error("Timestamp bukan bilangan bulat")
}.also { require(it >= 0) }
