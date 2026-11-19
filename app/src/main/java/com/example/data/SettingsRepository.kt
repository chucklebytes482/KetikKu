package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ClipboardItem
import com.example.model.DictionaryBackupEnvelope
import com.example.model.FullBackupEnvelope
import com.example.model.KeyboardHeightPreset
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.SettingsBackupEnvelope
import com.example.model.SyncLocalState
import com.example.model.WordPersonalityStat
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

val Context.ketikKuDataStore: DataStore<Preferences> by preferencesDataStore(name = "ketikku_prefs")

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clipboardStore: ClipboardBackingStore = InMemoryClipboardBackingStore()
) {

    constructor(context: Context) : this(
        dataStore = context.applicationContext.ketikKuDataStore,
        clipboardStore = ClipboardDatabaseHelper(context.applicationContext)
    )

    private val moshi: Moshi = Moshi.Builder().build()

    private val settingsEnvelopeAdapter = moshi.adapter(SettingsBackupEnvelope::class.java)
    private val fullBackupAdapter = moshi.adapter(FullBackupEnvelope::class.java)
    private val settingsAdapter = moshi.adapter(KeyboardSettings::class.java)
    private val dictEnvelopeAdapter = moshi.adapter(DictionaryBackupEnvelope::class.java)
    private val dictListType = Types.newParameterizedType(List::class.java, PersonalDictionaryEntry::class.java)
    private val dictListAdapter = moshi.adapter<List<PersonalDictionaryEntry>>(dictListType)
    private val personalityMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WordPersonalityStat::class.java
    )
    private val personalityMapAdapter = moshi.adapter<Map<String, WordPersonalityStat>>(personalityMapType)

    private object Keys {
        val HEIGHT_PRESET = stringPreferencesKey("height_preset")
        val FLOATING_MODE_ENABLED = booleanPreferencesKey("floating_mode_enabled")
        val FLOATING_POS_X = floatPreferencesKey("floating_pos_x")
        val FLOATING_POS_Y = floatPreferencesKey("floating_pos_y")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        val SERIF_MIRING_ENABLED = booleanPreferencesKey("serif_miring_enabled")
        val AUTO_SYNC_DRIVE = booleanPreferencesKey("auto_sync_drive")
        val DRIVE_FOLDER_NAME = stringPreferencesKey("drive_folder_name")
        val DRIVE_SHORTCUT_FOLDER_ID = stringPreferencesKey("drive_shortcut_folder_id")
        val SETTINGS_UPDATED_AT = longPreferencesKey("settings_updated_at")

        val DICTIONARY_JSON = stringPreferencesKey("dictionary_json")
        val PERSONALITY_JSON = stringPreferencesKey("suggestion_personality_json")
        val CLIPBOARD_REVISION = longPreferencesKey("clipboard_revision")
        val DISMISSED_CLIPBOARD_ID = stringPreferencesKey("dismissed_clipboard_id")
        val DISMISSED_CLIPBOARD_SIGNATURE = stringPreferencesKey("dismissed_clipboard_signature")

        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val DRIVE_SETTINGS_FILE_ID = stringPreferencesKey("drive_settings_file_id")
        val DRIVE_DICT_FILE_ID = stringPreferencesKey("drive_dict_file_id")
        val PENDING_CHANGES = booleanPreferencesKey("pending_changes")
    }

    val settingsFlow: Flow<KeyboardSettings> = dataStore.data
        .map { prefs ->
            KeyboardSettings(
                heightPreset = KeyboardHeightPreset.fromName(prefs[Keys.HEIGHT_PRESET]).name,
                floatingModeEnabled = prefs[Keys.FLOATING_MODE_ENABLED] ?: false,
                floatingPosX = (prefs[Keys.FLOATING_POS_X] ?: 0.5f).coerceIn(0f, 1f),
                floatingPosY = (prefs[Keys.FLOATING_POS_Y] ?: 0.85f).coerceIn(0f, 1f),
                hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
                clipboardHistoryEnabled = prefs[Keys.CLIPBOARD_HISTORY] ?: true,
                serifMiringEnabled = prefs[Keys.SERIF_MIRING_ENABLED] ?: false,
                autoSyncDriveEnabled = prefs[Keys.AUTO_SYNC_DRIVE] ?: true,
                driveFolderName = prefs[Keys.DRIVE_FOLDER_NAME] ?: "KetikKu",
                driveShortcutFolderId = prefs[Keys.DRIVE_SHORTCUT_FOLDER_ID] ?: "",
                updatedAtMillis = prefs[Keys.SETTINGS_UPDATED_AT] ?: 0L
            )
        }
        .distinctUntilChanged()

    val dictionaryFlow: Flow<List<PersonalDictionaryEntry>> = dataStore.data
        .map { prefs -> prefs[Keys.DICTIONARY_JSON] }
        .distinctUntilChanged()
        .map { raw ->
            val list = if (raw.isNullOrBlank()) {
                defaultDictionaryEntries()
            } else {
                runCatching { dictListAdapter.fromJson(raw) }.getOrNull() ?: defaultDictionaryEntries()
            }
            java.util.Collections.unmodifiableList(list)
        }

    val clipboardFlow: Flow<List<ClipboardItem>> = dataStore.data
        .map { prefs -> prefs[Keys.CLIPBOARD_REVISION] ?: 0L }
        .distinctUntilChanged()
        .map {
            withContext(Dispatchers.IO) {
                runCatching { clipboardStore.getPreviews(ClipboardDatabaseHelper.HISTORY_LIMIT) }
                    .getOrDefault(emptyList())
            }
        }

    val dismissedClipboardIdFlow: Flow<String?> = dataStore.data
        .map { prefs -> prefs[Keys.DISMISSED_CLIPBOARD_ID]?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()

    val dismissedClipboardSignatureFlow: Flow<String?> = dataStore.data
        .map { prefs -> prefs[Keys.DISMISSED_CLIPBOARD_SIGNATURE]?.takeIf { it.isNotBlank() } }
        .distinctUntilChanged()

    val personalityFlow: Flow<Map<String, WordPersonalityStat>> = dataStore.data
        .map { prefs -> prefs[Keys.PERSONALITY_JSON] }
        .distinctUntilChanged()
        .map { raw ->
            if (raw.isNullOrBlank()) {
                emptyMap()
            } else {
                runCatching { personalityMapAdapter.fromJson(raw) }.getOrNull() ?: emptyMap()
            }
        }

    val syncLocalStateFlow: Flow<SyncLocalState> = dataStore.data
        .map { prefs ->
            SyncLocalState(
                lastSyncedAtMillis = prefs[Keys.LAST_SYNCED_AT] ?: 0L,
                lastSyncMessage = prefs[Keys.LAST_SYNC_MESSAGE] ?: "Belum pernah disinkronkan",
                driveFolderId = prefs[Keys.DRIVE_FOLDER_ID] ?: "",
                driveSettingsFileId = prefs[Keys.DRIVE_SETTINGS_FILE_ID] ?: "",
                driveDictFileId = prefs[Keys.DRIVE_DICT_FILE_ID] ?: "",
                pendingChanges = prefs[Keys.PENDING_CHANGES] ?: false
            )
        }
        .distinctUntilChanged()

    suspend fun getCurrentSettings(): KeyboardSettings = settingsFlow.first()

    suspend fun getCurrentDictionary(): List<PersonalDictionaryEntry> = dictionaryFlow.first()

    suspend fun updateSettings(
        markPendingSync: Boolean = true,
        transform: (KeyboardSettings) -> KeyboardSettings
    ): KeyboardSettings {
        val now = System.currentTimeMillis()
        var result = getCurrentSettings()
        dataStore.edit { prefs ->
            val current = KeyboardSettings(
                heightPreset = KeyboardHeightPreset.fromName(prefs[Keys.HEIGHT_PRESET]).name,
                floatingModeEnabled = prefs[Keys.FLOATING_MODE_ENABLED] ?: false,
                floatingPosX = (prefs[Keys.FLOATING_POS_X] ?: 0.5f).coerceIn(0f, 1f),
                floatingPosY = (prefs[Keys.FLOATING_POS_Y] ?: 0.85f).coerceIn(0f, 1f),
                hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
                clipboardHistoryEnabled = prefs[Keys.CLIPBOARD_HISTORY] ?: true,
                serifMiringEnabled = prefs[Keys.SERIF_MIRING_ENABLED] ?: false,
                autoSyncDriveEnabled = prefs[Keys.AUTO_SYNC_DRIVE] ?: true,
                driveFolderName = prefs[Keys.DRIVE_FOLDER_NAME] ?: "KetikKu",
                driveShortcutFolderId = prefs[Keys.DRIVE_SHORTCUT_FOLDER_ID] ?: "",
                updatedAtMillis = prefs[Keys.SETTINGS_UPDATED_AT] ?: 0L
            )
            val updated = transform(current).copy(
                updatedAtMillis = if (markPendingSync) now else current.updatedAtMillis
            )
            result = updated
            writeSettingsToPrefs(prefs, updated)
            if (markPendingSync) {
                prefs[Keys.PENDING_CHANGES] = true
            }
        }
        return result
    }

    suspend fun applyRemoteSettings(remote: KeyboardSettings) {
        dataStore.edit { prefs ->
            writeSettingsToPrefs(prefs, remote)
            prefs[Keys.PENDING_CHANGES] = false
        }
    }

    suspend fun upsertDictionaryEntry(
        word: String,
        shortcut: String,
        language: String,
        existingId: String? = null
    ) {
        val trimmedWord = word.trim()
        if (trimmedWord.isEmpty()) return
        val trimmedShortcut = shortcut.trim().lowercase()
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val current = prefs[Keys.DICTIONARY_JSON]?.let {
                runCatching { dictListAdapter.fromJson(it) }.getOrNull()
            } ?: defaultDictionaryEntries()
            val id = existingId ?: UUID.randomUUID().toString().take(12)
            val updatedList = current.filterNot { it.id == id } + PersonalDictionaryEntry(
                id = id,
                word = trimmedWord,
                shortcut = trimmedShortcut,
                language = language,
                updatedAtMillis = now,
                deleted = false
            )
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedList)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun deleteDictionaryEntry(entryId: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val current = prefs[Keys.DICTIONARY_JSON]?.let {
                runCatching { dictListAdapter.fromJson(it) }.getOrNull()
            } ?: defaultDictionaryEntries()
            val updatedList = current.map { entry ->
                if (entry.id == entryId) entry.copy(deleted = true, updatedAtMillis = now) else entry
            }
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedList)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun mergeRemoteDictionary(remoteEntries: List<PersonalDictionaryEntry>): List<PersonalDictionaryEntry> {
        var mergedResult = emptyList<PersonalDictionaryEntry>()
        dataStore.edit { prefs ->
            val local = prefs[Keys.DICTIONARY_JSON]?.let {
                runCatching { dictListAdapter.fromJson(it) }.getOrNull()
            } ?: defaultDictionaryEntries()
            mergedResult = mergeDictionaryLists(local, remoteEntries)
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(mergedResult)
        }
        return mergedResult
    }

    suspend fun recordWordUsage(word: String, countDelta: Int = 1) {
        val clean = word.trim()
        if (clean.isEmpty() || countDelta <= 0) return
        val key = clean.lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        runCatching {
            dataStore.edit { prefs ->
                val current = prefs[Keys.PERSONALITY_JSON]?.let { raw ->
                    runCatching { personalityMapAdapter.fromJson(raw) }.getOrNull()
                }?.toMutableMap() ?: LinkedHashMap()

                val existing = current[key]
                current[key] = WordPersonalityStat(
                    word = clean,
                    usageCount = (existing?.usageCount ?: 0) + countDelta,
                    lastUsedMillis = now
                )

                val trimmed = if (current.size > PERSONALITY_MAX_ENTRIES) {
                    current.entries
                        .sortedByDescending { DictionaryEngine.personalityScore(it.key, current, now) }
                        .take(PERSONALITY_MAX_ENTRIES)
                        .associate { it.key to it.value }
                } else {
                    current
                }
                prefs[Keys.PERSONALITY_JSON] = personalityMapAdapter.toJson(trimmed)
            }
        }
    }

    suspend fun addClipboardText(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (start, end) = TextChunker.trimmedBounds(text)
            if (start >= end) return@runCatching
            if (clipboardStore.isLatestSame(text)) return@runCatching
            val now = System.currentTimeMillis()
            val saved = clipboardStore.saveClipboardText(text, now) ?: return@runCatching
            val newSignature = saved.dismissSignature()
            val autoEntries = extractAutoSavableEntries(text)
            dataStore.edit { prefs ->
                prefs[Keys.CLIPBOARD_REVISION] = (prefs[Keys.CLIPBOARD_REVISION] ?: 0L) + 1L
                val dismissedSig = prefs[Keys.DISMISSED_CLIPBOARD_SIGNATURE]
                if (dismissedSig != newSignature) {
                    prefs.remove(Keys.DISMISSED_CLIPBOARD_ID)
                    prefs.remove(Keys.DISMISSED_CLIPBOARD_SIGNATURE)
                } else {
                    prefs[Keys.DISMISSED_CLIPBOARD_ID] = saved.id
                }

                if (autoEntries.isNotEmpty()) {
                    val currentDict = prefs[Keys.DICTIONARY_JSON]?.let {
                        runCatching { dictListAdapter.fromJson(it) }.getOrNull()
                    } ?: defaultDictionaryEntries()
                    val existingActiveWords = currentDict
                        .filter { !it.deleted }
                        .map { it.word.trim().lowercase(java.util.Locale.ROOT) }
                        .toMutableSet()
                    val addedEntries = mutableListOf<PersonalDictionaryEntry>()
                    for (candidate in autoEntries) {
                        val key = candidate.trim().lowercase(java.util.Locale.ROOT)
                        if (key.isNotEmpty() && existingActiveWords.add(key)) {
                            addedEntries.add(
                                PersonalDictionaryEntry(
                                    id = "auto_${UUID.randomUUID().toString().take(8)}",
                                    word = candidate.trim(),
                                    shortcut = "",
                                    language = KeyboardLanguage.INDONESIAN.code,
                                    updatedAtMillis = now,
                                    deleted = false
                                )
                            )
                        }
                    }
                    if (addedEntries.isNotEmpty()) {
                        prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(currentDict + addedEntries)
                        prefs[Keys.PENDING_CHANGES] = true
                    }
                }
            }
            Unit
        }
    }

    suspend fun syncSystemClipboardIfChanged(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (start, end) = TextChunker.trimmedBounds(text)
            if (start >= end) return@runCatching
            if (!clipboardStore.isLatestSame(text)) {
                addClipboardText(text).getOrThrow()
            }
        }
    }

    suspend fun getClipboardFullText(clipboardId: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            clipboardStore.getFullText(clipboardId)
        }
    }

    suspend fun getLatestClipboardFullText(): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            clipboardStore.getLatestFullText()
        }
    }

    suspend fun dismissRecentClipboard(clipboardId: String, signature: String? = null) {
        if (clipboardId.isBlank() && signature.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedSig = signature?.takeIf { it.isNotBlank() }
                    ?: clipboardId.takeIf { it.isNotBlank() }?.let { clipboardStore.getContentHash(it) }
                dataStore.edit { prefs ->
                    if (clipboardId.isNotBlank()) {
                        prefs[Keys.DISMISSED_CLIPBOARD_ID] = clipboardId
                    }
                    if (!resolvedSig.isNullOrBlank()) {
                        prefs[Keys.DISMISSED_CLIPBOARD_SIGNATURE] = resolvedSig
                    }
                }
            }
        }
    }

    suspend fun clearAllClipboard(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            clipboardStore.clearAll()
            dataStore.edit { prefs ->
                prefs[Keys.CLIPBOARD_REVISION] = (prefs[Keys.CLIPBOARD_REVISION] ?: 0L) + 1L
                prefs.remove(Keys.DISMISSED_CLIPBOARD_ID)
                prefs.remove(Keys.DISMISSED_CLIPBOARD_SIGNATURE)
            }
            Unit
        }
    }

    suspend fun updateSyncState(
        lastSyncedAtMillis: Long,
        message: String,
        driveFolderId: String? = null,
        driveSettingsFileId: String? = null,
        driveDictFileId: String? = null,
        pendingChanges: Boolean = false
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_AT] = lastSyncedAtMillis
            prefs[Keys.LAST_SYNC_MESSAGE] = message
            if (driveFolderId != null) prefs[Keys.DRIVE_FOLDER_ID] = driveFolderId
            if (driveSettingsFileId != null) prefs[Keys.DRIVE_SETTINGS_FILE_ID] = driveSettingsFileId
            if (driveDictFileId != null) prefs[Keys.DRIVE_DICT_FILE_ID] = driveDictFileId
            prefs[Keys.PENDING_CHANGES] = pendingChanges
        }
    }

    suspend fun clearDriveIdsOnDisconnect() {
        dataStore.edit { prefs ->
            prefs[Keys.DRIVE_FOLDER_ID] = ""
            prefs[Keys.DRIVE_SETTINGS_FILE_ID] = ""
            prefs[Keys.DRIVE_DICT_FILE_ID] = ""
            prefs[Keys.LAST_SYNC_MESSAGE] = "Akun terputus"
        }
    }

    fun encodeSettingsBackupJson(settings: KeyboardSettings): String {
        val envelope = SettingsBackupEnvelope(
            schemaVersion = 1,
            updatedAtMillis = settings.updatedAtMillis,
            settings = settings
        )
        return settingsEnvelopeAdapter.toJson(envelope)
    }

    fun encodeCompactSettingsJson(settings: KeyboardSettings): String = settingsAdapter.toJson(settings)

    fun decodeSettingsBackupJson(json: String): SettingsBackupEnvelope? =
        runCatching { settingsEnvelopeAdapter.fromJson(json) }.getOrNull()

    fun encodeDictionaryBackupJson(entries: List<PersonalDictionaryEntry>, updatedAtMillis: Long): String {
        val envelope = DictionaryBackupEnvelope(
            schemaVersion = 1,
            updatedAtMillis = updatedAtMillis,
            entries = entries
        )
        return dictEnvelopeAdapter.toJson(envelope)
    }

    fun decodeDictionaryBackupJson(json: String): DictionaryBackupEnvelope? =
        runCatching { dictEnvelopeAdapter.fromJson(json) }.getOrNull()

    fun encodeFullBackupJson(
        settings: KeyboardSettings,
        entries: List<PersonalDictionaryEntry>,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val envelope = FullBackupEnvelope(
            schemaVersion = 1,
            appName = "KetikKu",
            exportedAtMillis = exportedAtMillis,
            settings = settings,
            dictionary = entries
        )
        return fullBackupAdapter.toJson(envelope)
    }

    fun decodeFullBackupJson(json: String): FullBackupEnvelope? =
        runCatching { fullBackupAdapter.fromJson(json) }.getOrNull()

    suspend fun importLocalBackupJson(rawJson: String): Result<String> {
        val full = decodeFullBackupJson(rawJson)
        if (full != null && full.appName.isNotBlank()) {
            val now = System.currentTimeMillis()
            applyRemoteSettings(full.settings.copy(updatedAtMillis = maxOf(full.settings.updatedAtMillis, now)))
            val merged = mergeRemoteDictionary(full.dictionary)
            updateSyncState(
                lastSyncedAtMillis = now,
                message = "Dipulihkan dari berkas JSON lokal",
                pendingChanges = false
            )
            return Result.success("Setelan & ${merged.count { !it.deleted }} kata kamus dipulihkan dari JSON lokal.")
        }

        val settingsOnly = decodeSettingsBackupJson(rawJson)
        if (settingsOnly != null) {
            val now = System.currentTimeMillis()
            applyRemoteSettings(settingsOnly.settings.copy(updatedAtMillis = maxOf(settingsOnly.settings.updatedAtMillis, now)))
            updateSyncState(
                lastSyncedAtMillis = now,
                message = "Setelan dipulihkan dari berkas JSON lokal",
                pendingChanges = false
            )
            return Result.success("Setelan keyboard berhasil dipulihkan dari JSON lokal.")
        }

        val dictOnly = decodeDictionaryBackupJson(rawJson)
        if (dictOnly != null) {
            val merged = mergeRemoteDictionary(dictOnly.entries)
            return Result.success("${merged.count { !it.deleted }} kata kamus berhasil digabungkan dari JSON lokal.")
        }

        return Result.failure(IllegalArgumentException("Format berkas JSON cadangan KetikKu tidak valid."))
    }

    private fun writeSettingsToPrefs(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        s: KeyboardSettings
    ) {
        prefs[Keys.HEIGHT_PRESET] = KeyboardHeightPreset.fromName(s.heightPreset).name
        prefs[Keys.FLOATING_MODE_ENABLED] = s.floatingModeEnabled
        prefs[Keys.FLOATING_POS_X] = s.floatingPosX.coerceIn(0f, 1f)
        prefs[Keys.FLOATING_POS_Y] = s.floatingPosY.coerceIn(0f, 1f)
        prefs[Keys.HAPTIC_ENABLED] = s.hapticEnabled
        prefs[Keys.CLIPBOARD_HISTORY] = s.clipboardHistoryEnabled
        prefs[Keys.SERIF_MIRING_ENABLED] = s.serifMiringEnabled
        prefs[Keys.AUTO_SYNC_DRIVE] = s.autoSyncDriveEnabled
        prefs[Keys.DRIVE_FOLDER_NAME] = s.driveFolderName.ifBlank { "KetikKu" }
        prefs[Keys.DRIVE_SHORTCUT_FOLDER_ID] = s.driveShortcutFolderId
        prefs[Keys.SETTINGS_UPDATED_AT] = s.updatedAtMillis
    }

    companion object {
        private const val PERSONALITY_MAX_ENTRIES = 600

        fun mergeDictionaryLists(
            local: List<PersonalDictionaryEntry>,
            remote: List<PersonalDictionaryEntry>
        ): List<PersonalDictionaryEntry> {
            val byId = linkedMapOf<String, PersonalDictionaryEntry>()
            for (item in local) {
                byId[item.id] = item
            }
            for (remoteItem in remote) {
                val existing = byId[remoteItem.id]
                if (existing == null || remoteItem.updatedAtMillis >= existing.updatedAtMillis) {
                    byId[remoteItem.id] = remoteItem
                }
            }
            return byId.values.toList()
        }

        private fun defaultDictionaryEntries(): List<PersonalDictionaryEntry> = listOf(
            PersonalDictionaryEntry(
                id = "preset_ttd",
                word = "Terima kasih, salam hangat",
                shortcut = "ttd",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 1L
            ),
            PersonalDictionaryEntry(
                id = "preset_omw",
                word = "Sedang dalam perjalanan",
                shortcut = "omw",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 1L
            ),
            PersonalDictionaryEntry(
                id = "preset_asap",
                word = "As soon as possible",
                shortcut = "asap",
                language = KeyboardLanguage.ENGLISH.code,
                updatedAtMillis = 1L
            )
        )

        private val GMAIL_REGEX = Regex(
            "(?<![A-Za-z0-9._%+-])([A-Za-z0-9._%+-]{1,244}@gmail\\.com)(?![A-Za-z0-9._%+-])",
            RegexOption.IGNORE_CASE
        )
        private val NUMBER_REGEX = Regex(
            "(?<![A-Za-z0-9._%+-@])(\\+?\\d[\\d\\s-]{9,30}\\d)(?![A-Za-z0-9._%+-@])"
        )

        /**
         * Mengekstrak kandidat yang otomatis disimpan ke Kamus Pribadi dari teks yang disalin:
         * 1. Alamat email berakhiran @gmail.com dengan panjang minimal 20 karakter.
         * 2. Nomor (angka telepon/rekening/identitas) dengan minimal 11 digit/karakter angka.
         * Diproses tanpa menyalin ulang teks panjang di memori.
         */
        fun extractAutoSavableEntries(rawText: String): List<String> {
            val (start, end) = TextChunker.trimmedBounds(rawText)
            if (start >= end) return emptyList()

            val results = linkedSetOf<String>()
            val emailRanges = mutableListOf<IntRange>()

            GMAIL_REGEX.findAll(rawText, start).forEach { match ->
                if (match.range.first >= end) return@forEach
                emailRanges.add(match.range)
                val email = match.value.trim()
                if (email.length >= 20) {
                    results.add(email)
                }
            }

            NUMBER_REGEX.findAll(rawText, start).forEach { match ->
                if (match.range.first >= end) return@forEach
                val overlapsEmail = emailRanges.any { er ->
                    match.range.first <= er.last && match.range.last >= er.first
                }
                if (overlapsEmail) return@forEach
                val rawNumber = match.value.trim()
                val digitCount = rawNumber.count { it.isDigit() }
                if (digitCount >= 11 && rawNumber.length >= 11) {
                    results.add(rawNumber)
                }
            }
            return results.toList()
        }
    }
}
