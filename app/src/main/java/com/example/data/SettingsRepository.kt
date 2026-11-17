package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val Context.ketikKuDataStore: DataStore<Preferences> by preferencesDataStore(name = "ketikku_prefs")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.ketikKuDataStore)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val settingsEnvelopeAdapter = moshi.adapter(SettingsBackupEnvelope::class.java)
    private val fullBackupAdapter = moshi.adapter(FullBackupEnvelope::class.java)
    private val settingsAdapter = moshi.adapter(KeyboardSettings::class.java)
    private val dictEnvelopeAdapter = moshi.adapter(DictionaryBackupEnvelope::class.java)
    private val dictListType = Types.newParameterizedType(List::class.java, PersonalDictionaryEntry::class.java)
    private val dictListAdapter = moshi.adapter<List<PersonalDictionaryEntry>>(dictListType)

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val HEIGHT_PRESET = stringPreferencesKey("height_preset")
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val KEY_BORDERS = booleanPreferencesKey("key_borders")
        val KEY_POPUP = booleanPreferencesKey("key_popup")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val SUGGESTION_STRIP = booleanPreferencesKey("suggestion_strip")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val GLIDE_TYPING = booleanPreferencesKey("glide_typing")
        val SPACEBAR_TRACKPAD = booleanPreferencesKey("spacebar_trackpad")
        val SWIPE_DELETE = booleanPreferencesKey("swipe_delete")
        val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        val ARIAL_ITALIC_ENABLED = booleanPreferencesKey("arial_italic_enabled")
        val LONG_PRESS_DELAY_MS = intPreferencesKey("long_press_delay_ms")
        val AUTO_SYNC_DRIVE = booleanPreferencesKey("auto_sync_drive")
        val DRIVE_FOLDER_NAME = stringPreferencesKey("drive_folder_name")
        val DRIVE_SHORTCUT_FOLDER_ID = stringPreferencesKey("drive_shortcut_folder_id")
        val SETTINGS_UPDATED_AT = longPreferencesKey("settings_updated_at")

        val DICTIONARY_JSON = stringPreferencesKey("dictionary_json")
        val CLIPBOARD_JSON = stringPreferencesKey("clipboard_json")

        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val DRIVE_SETTINGS_FILE_ID = stringPreferencesKey("drive_settings_file_id")
        val DRIVE_DICT_FILE_ID = stringPreferencesKey("drive_dict_file_id")
        val PENDING_CHANGES = booleanPreferencesKey("pending_changes")
    }

    val settingsFlow: Flow<KeyboardSettings> = dataStore.data.map { prefs ->
        KeyboardSettings(
            language = prefs[Keys.LANGUAGE] ?: KeyboardLanguage.INDONESIAN.code,
            heightPreset = prefs[Keys.HEIGHT_PRESET] ?: KeyboardHeightPreset.NORMAL.name,
            numberRowEnabled = prefs[Keys.NUMBER_ROW] ?: true,
            keyBordersEnabled = prefs[Keys.KEY_BORDERS] ?: true,
            keyPopupEnabled = prefs[Keys.KEY_POPUP] ?: true,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            autoCorrectEnabled = prefs[Keys.AUTO_CORRECT] ?: true,
            suggestionStripEnabled = prefs[Keys.SUGGESTION_STRIP] ?: true,
            autoCapitalizeEnabled = prefs[Keys.AUTO_CAPITALIZE] ?: true,
            doubleSpacePeriodEnabled = prefs[Keys.DOUBLE_SPACE_PERIOD] ?: true,
            glideTypingEnabled = prefs[Keys.GLIDE_TYPING] ?: true,
            spacebarTrackpadEnabled = prefs[Keys.SPACEBAR_TRACKPAD] ?: true,
            swipeDeleteEnabled = prefs[Keys.SWIPE_DELETE] ?: true,
            clipboardHistoryEnabled = prefs[Keys.CLIPBOARD_HISTORY] ?: true,
            arialItalicEnabled = prefs[Keys.ARIAL_ITALIC_ENABLED] ?: false,
            longPressDelayMs = prefs[Keys.LONG_PRESS_DELAY_MS] ?: 280,
            autoSyncDriveEnabled = prefs[Keys.AUTO_SYNC_DRIVE] ?: true,
            driveFolderName = prefs[Keys.DRIVE_FOLDER_NAME] ?: "KetikKu",
            driveShortcutFolderId = prefs[Keys.DRIVE_SHORTCUT_FOLDER_ID] ?: "",
            updatedAtMillis = prefs[Keys.SETTINGS_UPDATED_AT] ?: 0L
        )
    }

    val dictionaryFlow: Flow<List<PersonalDictionaryEntry>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.DICTIONARY_JSON]
        if (raw.isNullOrBlank()) {
            defaultDictionaryEntries()
        } else {
            runCatching { dictListAdapter.fromJson(raw) }.getOrNull() ?: defaultDictionaryEntries()
        }
    }

    val clipboardFlow: Flow<List<ClipboardItem>> = dataStore.data.map { prefs ->
        parseClipboardJson(prefs[Keys.CLIPBOARD_JSON])
    }

    val syncLocalStateFlow: Flow<SyncLocalState> = dataStore.data.map { prefs ->
        SyncLocalState(
            lastSyncedAtMillis = prefs[Keys.LAST_SYNCED_AT] ?: 0L,
            lastSyncMessage = prefs[Keys.LAST_SYNC_MESSAGE] ?: "Belum pernah disinkronkan",
            driveFolderId = prefs[Keys.DRIVE_FOLDER_ID] ?: "",
            driveSettingsFileId = prefs[Keys.DRIVE_SETTINGS_FILE_ID] ?: "",
            driveDictFileId = prefs[Keys.DRIVE_DICT_FILE_ID] ?: "",
            pendingChanges = prefs[Keys.PENDING_CHANGES] ?: false
        )
    }

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
                language = prefs[Keys.LANGUAGE] ?: KeyboardLanguage.INDONESIAN.code,
                heightPreset = prefs[Keys.HEIGHT_PRESET] ?: KeyboardHeightPreset.NORMAL.name,
                numberRowEnabled = prefs[Keys.NUMBER_ROW] ?: true,
                keyBordersEnabled = prefs[Keys.KEY_BORDERS] ?: true,
                keyPopupEnabled = prefs[Keys.KEY_POPUP] ?: true,
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
                hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
                autoCorrectEnabled = prefs[Keys.AUTO_CORRECT] ?: true,
                suggestionStripEnabled = prefs[Keys.SUGGESTION_STRIP] ?: true,
                autoCapitalizeEnabled = prefs[Keys.AUTO_CAPITALIZE] ?: true,
                doubleSpacePeriodEnabled = prefs[Keys.DOUBLE_SPACE_PERIOD] ?: true,
                glideTypingEnabled = prefs[Keys.GLIDE_TYPING] ?: true,
                spacebarTrackpadEnabled = prefs[Keys.SPACEBAR_TRACKPAD] ?: true,
                swipeDeleteEnabled = prefs[Keys.SWIPE_DELETE] ?: true,
                clipboardHistoryEnabled = prefs[Keys.CLIPBOARD_HISTORY] ?: true,
                arialItalicEnabled = prefs[Keys.ARIAL_ITALIC_ENABLED] ?: false,
                longPressDelayMs = prefs[Keys.LONG_PRESS_DELAY_MS] ?: 280,
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

    suspend fun addClipboardText(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        dataStore.edit { prefs ->
            val current = parseClipboardJson(prefs[Keys.CLIPBOARD_JSON])
            val filtered = current.filterNot { it.text == cleaned }
            val newItem = ClipboardItem(
                id = UUID.randomUUID().toString().take(10),
                text = cleaned.take(500),
                timestampMillis = System.currentTimeMillis()
            )
            val combined = (listOf(newItem) + filtered)
                .sortedByDescending { it.timestampMillis }
                .take(25)
            prefs[Keys.CLIPBOARD_JSON] = serializeClipboardJson(combined)
        }
    }

    suspend fun clearAllClipboard() {
        dataStore.edit { prefs ->
            prefs[Keys.CLIPBOARD_JSON] = serializeClipboardJson(emptyList())
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
        prefs[Keys.LANGUAGE] = s.language
        prefs[Keys.HEIGHT_PRESET] = s.heightPreset
        prefs[Keys.NUMBER_ROW] = s.numberRowEnabled
        prefs[Keys.KEY_BORDERS] = s.keyBordersEnabled
        prefs[Keys.KEY_POPUP] = s.keyPopupEnabled
        prefs[Keys.SOUND_ENABLED] = s.soundEnabled
        prefs[Keys.HAPTIC_ENABLED] = s.hapticEnabled
        prefs[Keys.AUTO_CORRECT] = s.autoCorrectEnabled
        prefs[Keys.SUGGESTION_STRIP] = s.suggestionStripEnabled
        prefs[Keys.AUTO_CAPITALIZE] = s.autoCapitalizeEnabled
        prefs[Keys.DOUBLE_SPACE_PERIOD] = s.doubleSpacePeriodEnabled
        prefs[Keys.GLIDE_TYPING] = s.glideTypingEnabled
        prefs[Keys.SPACEBAR_TRACKPAD] = s.spacebarTrackpadEnabled
        prefs[Keys.SWIPE_DELETE] = s.swipeDeleteEnabled
        prefs[Keys.CLIPBOARD_HISTORY] = s.clipboardHistoryEnabled
        prefs[Keys.ARIAL_ITALIC_ENABLED] = s.arialItalicEnabled
        prefs[Keys.LONG_PRESS_DELAY_MS] = s.longPressDelayMs
        prefs[Keys.AUTO_SYNC_DRIVE] = s.autoSyncDriveEnabled
        prefs[Keys.DRIVE_FOLDER_NAME] = s.driveFolderName.ifBlank { "KetikKu" }
        prefs[Keys.DRIVE_SHORTCUT_FOLDER_ID] = s.driveShortcutFolderId
        prefs[Keys.SETTINGS_UPDATED_AT] = s.updatedAtMillis
    }

    companion object {
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

        private fun parseClipboardJson(raw: String?): List<ClipboardItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            ClipboardItem(
                                id = obj.optString("id"),
                                text = obj.optString("text"),
                                timestampMillis = obj.optLong("timestampMillis")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        private fun serializeClipboardJson(items: List<ClipboardItem>): String {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("text", item.text)
                        put("timestampMillis", item.timestampMillis)
                    }
                )
            }
            return arr.toString()
        }
    }
}
