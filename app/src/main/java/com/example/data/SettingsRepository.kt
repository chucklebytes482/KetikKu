package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
import com.example.model.SyncScheduleInterval
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
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
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
        val SWAP_PERIOD_COMMA = booleanPreferencesKey("swap_period_comma")
        val SWAP_SYMBOL_PANELS = booleanPreferencesKey("swap_symbol_panels")
        val AUTO_SYNC_DRIVE = booleanPreferencesKey("auto_sync_drive")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val DRIVE_FOLDER_NAME = stringPreferencesKey("drive_folder_name")
        val DRIVE_SHORTCUT_FOLDER_ID = stringPreferencesKey("drive_shortcut_folder_id")
        val SETTINGS_UPDATED_AT = longPreferencesKey("settings_updated_at")

        val DICTIONARY_JSON = stringPreferencesKey("dictionary_json")
        val FOLDERS_JSON = stringPreferencesKey("dictionary_folders_json")
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

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

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
                swapPeriodCommaEnabled = prefs[Keys.SWAP_PERIOD_COMMA] ?: false,
                swapSymbolPanelsEnabled = prefs[Keys.SWAP_SYMBOL_PANELS] ?: false,
                autoSyncDriveEnabled = prefs[Keys.AUTO_SYNC_DRIVE] ?: true,
                syncIntervalMinutes = SyncScheduleInterval.fromMinutes(
                    prefs[Keys.SYNC_INTERVAL_MINUTES] ?: SyncScheduleInterval.DEFAULT.minutes
                ).minutes,
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
            }.map { entry ->
                val normalizedFolder = normalizeFolderPath(entry.folder)
                if (normalizedFolder == entry.folder) entry else entry.copy(folder = normalizedFolder)
            }
            java.util.Collections.unmodifiableList(list)
        }

    val foldersFlow: Flow<List<String>> = dataStore.data
        .map { prefs -> prefs[Keys.FOLDERS_JSON] to prefs[Keys.DICTIONARY_JSON] }
        .distinctUntilChanged()
        .map { (rawFolders, rawDict) ->
            val explicit = parseFolderList(rawFolders)
            val entries = parseDictionaryList(rawDict)
            val normalized = normalizeOrderedFolders(explicit, entries)
            java.util.Collections.unmodifiableList(normalized)
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

    suspend fun getCurrentFolders(): List<String> = foldersFlow.first()

    private fun parseDictionaryList(raw: String?): List<PersonalDictionaryEntry> {
        val list = if (raw.isNullOrBlank()) {
            defaultDictionaryEntries()
        } else {
            runCatching { dictListAdapter.fromJson(raw) }.getOrNull() ?: defaultDictionaryEntries()
        }
        return list.map { entry ->
            val norm = normalizeFolderPath(entry.folder)
            if (norm == entry.folder) entry else entry.copy(folder = norm)
        }
    }

    private fun parseFolderList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { stringListAdapter.fromJson(raw) }.getOrNull().orEmpty()
    }

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
                swapPeriodCommaEnabled = prefs[Keys.SWAP_PERIOD_COMMA] ?: false,
                swapSymbolPanelsEnabled = prefs[Keys.SWAP_SYMBOL_PANELS] ?: false,
                autoSyncDriveEnabled = prefs[Keys.AUTO_SYNC_DRIVE] ?: true,
                syncIntervalMinutes = SyncScheduleInterval.fromMinutes(
                    prefs[Keys.SYNC_INTERVAL_MINUTES] ?: SyncScheduleInterval.DEFAULT.minutes
                ).minutes,
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
        existingId: String? = null,
        folder: String = ""
    ) {
        val trimmedWord = word.trim()
        if (trimmedWord.isEmpty()) return
        val trimmedShortcut = shortcut.trim().lowercase(Locale.ROOT)
        val normalizedFolder = normalizeFolderPath(folder)
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val current = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val id = existingId ?: UUID.randomUUID().toString().take(12)
            val updatedList = current.filterNot { it.id == id } + PersonalDictionaryEntry(
                id = id,
                word = trimmedWord,
                shortcut = trimmedShortcut,
                folder = normalizedFolder,
                language = language,
                updatedAtMillis = now,
                deleted = false
            )
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedList)
            if (normalizedFolder.isNotEmpty()) {
                val updatedFolders = prependFoldersToStorage(
                    existingFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                    newFolderPaths = listOf(normalizedFolder),
                    entries = updatedList
                )
                prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(updatedFolders)
            }
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun deleteDictionaryEntry(entryId: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val current = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val updatedList = current.map { entry ->
                if (entry.id == entryId) entry.copy(deleted = true, updatedAtMillis = now) else entry
            }
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedList)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun createFolder(folderPath: String) {
        val normalized = normalizeFolderPath(folderPath)
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val currentEntries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val currentFolders = parseFolderList(prefs[Keys.FOLDERS_JSON])
            val updated = prependFoldersToStorage(
                existingFolders = currentFolders,
                newFolderPaths = listOf(normalized),
                entries = currentEntries
            )
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(updated)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun renameFolder(oldPath: String, newNameOrPath: String) {
        val normOld = normalizeFolderPath(oldPath)
        if (normOld.isEmpty()) return
        val parent = normOld.substringBeforeLast('/', missingDelimiterValue = "")
        val cleanedLeaf = newNameOrPath.replace('/', ' ').replace('\\', ' ').trim()
        if (cleanedLeaf.isEmpty()) return
        val normNew = normalizeFolderPath(
            if (parent.isEmpty()) cleanedLeaf else "$parent/$cleanedLeaf"
        )
        if (normNew.isEmpty() || normNew == normOld) return

        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val currentEntries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val updatedEntries = currentEntries.map { entry ->
                val rewritten = rewriteFolderPrefix(entry.folder, normOld, normNew)
                if (rewritten != entry.folder) {
                    entry.copy(folder = rewritten, updatedAtMillis = now)
                } else {
                    entry
                }
            }
            val currentFolders = normalizeOrderedFolders(
                explicitFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                entries = currentEntries
            )
            val rewrittenFolders = currentFolders.map { path ->
                rewriteFolderPrefix(path, normOld, normNew)
            }
            val finalFolders = normalizeOrderedFolders(rewrittenFolders, updatedEntries)
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedEntries)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(finalFolders)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun moveEntriesAndFolders(
        entryIds: Set<String>,
        folderPaths: Set<String>,
        targetFolderPath: String
    ) {
        val normTarget = normalizeFolderPath(targetFolderPath)
        val normSources = folderPaths.map(::normalizeFolderPath).filter { it.isNotEmpty() }
        if (entryIds.isEmpty() && normSources.isEmpty()) return

        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val currentEntries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val currentFolders = normalizeOrderedFolders(
                explicitFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                entries = currentEntries
            )

            // Build top-level folder move mapping (skip moving a folder into itself or its descendant)
            val topMoves = normSources
                .filter { src ->
                    normTarget != src && !normTarget.startsWith("$src/")
                }
                .map { src ->
                    val leaf = src.substringAfterLast('/')
                    val dest = normalizeFolderPath(if (normTarget.isEmpty()) leaf else "$normTarget/$leaf")
                    src to dest
                }
                .filter { (src, dest) -> dest.isNotEmpty() && src != dest }

            val updatedEntries = currentEntries.map { entry ->
                var nextFolder = entry.folder
                var changed = false
                if (entry.id in entryIds && entry.folder != normTarget) {
                    nextFolder = normTarget
                    changed = true
                }
                for ((src, dest) in topMoves) {
                    val shifted = rewriteFolderPrefix(nextFolder, src, dest)
                    if (shifted != nextFolder) {
                        nextFolder = shifted
                        changed = true
                    }
                }
                if (changed) entry.copy(folder = nextFolder, updatedAtMillis = now) else entry
            }

            val updatedFoldersRaw = currentFolders.map { folderPath ->
                var next = folderPath
                for ((src, dest) in topMoves) {
                    val shifted = rewriteFolderPrefix(next, src, dest)
                    if (shifted != next) {
                        next = shifted
                    }
                }
                next
            }
            val addedFolders = buildList {
                if (normTarget.isNotEmpty()) add(normTarget)
                topMoves.forEach { (_, dest) -> add(dest) }
            }
            val finalFolders = prependFoldersToStorage(
                existingFolders = updatedFoldersRaw,
                newFolderPaths = addedFolders,
                entries = updatedEntries
            )

            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedEntries)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(finalFolders)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun deleteEntriesAndFolders(
        entryIds: Set<String>,
        folderPaths: Set<String>
    ) {
        val normFolders = folderPaths.map(::normalizeFolderPath).filter { it.isNotEmpty() }
        if (entryIds.isEmpty() && normFolders.isEmpty()) return
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val currentEntries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val updatedEntries = currentEntries.map { entry ->
                val inDeletedFolder = !entry.deleted && normFolders.any { f ->
                    isSameOrDescendantFolder(entry.folder, f)
                }
                if (entry.id in entryIds || inDeletedFolder) {
                    entry.copy(deleted = true, updatedAtMillis = now)
                } else {
                    entry
                }
            }
            val currentFolders = parseFolderList(prefs[Keys.FOLDERS_JSON])
            val remainingFolders = currentFolders.filterNot { path ->
                val np = normalizeFolderPath(path)
                normFolders.any { del -> isSameOrDescendantFolder(np, del) }
            }
            val finalFolders = normalizeOrderedFolders(remainingFolders, updatedEntries)
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedEntries)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(finalFolders)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    suspend fun mergeRemoteDictionary(
        remoteEntries: List<PersonalDictionaryEntry>,
        remoteFolders: List<String> = emptyList()
    ): List<PersonalDictionaryEntry> {
        var mergedResult = emptyList<PersonalDictionaryEntry>()
        dataStore.edit { prefs ->
            val local = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            mergedResult = mergeDictionaryLists(local, remoteEntries)
            val localFolders = parseFolderList(prefs[Keys.FOLDERS_JSON])
            val mergedFolders = prependFoldersToStorage(
                existingFolders = localFolders,
                newFolderPaths = remoteFolders,
                entries = mergedResult
            )
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(mergedResult)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(mergedFolders)
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
            val autoEntries = extractAutoSavableCategorizedEntries(text)
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
                    val currentDict = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
                    val existingActiveWords = currentDict
                        .filter { !it.deleted }
                        .map { it.word.trim().lowercase(Locale.ROOT) }
                        .toMutableSet()
                    val addedEntries = mutableListOf<PersonalDictionaryEntry>()
                    val newAutoFolders = linkedSetOf<String>()
                    for (candidate in autoEntries) {
                        val cleanWord = candidate.word.trim()
                        val key = cleanWord.lowercase(Locale.ROOT)
                        if (key.isNotEmpty() && existingActiveWords.add(key)) {
                            val targetFolder = normalizeFolderPath(candidate.folder)
                            if (targetFolder.isNotEmpty()) {
                                newAutoFolders.add(targetFolder)
                            }
                            addedEntries.add(
                                PersonalDictionaryEntry(
                                    id = "auto_${UUID.randomUUID().toString().take(8)}",
                                    word = cleanWord,
                                    shortcut = "",
                                    folder = targetFolder,
                                    language = KeyboardLanguage.INDONESIAN.code,
                                    updatedAtMillis = now,
                                    deleted = false
                                )
                            )
                        }
                    }
                    if (addedEntries.isNotEmpty()) {
                        val combinedDict = currentDict + addedEntries
                        prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(combinedDict)
                        val updatedFolders = prependFoldersToStorage(
                            existingFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                            newFolderPaths = newAutoFolders.toList(),
                            entries = combinedDict
                        )
                        prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(updatedFolders)
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
        val settingsObj = JSONObject().apply {
            put("floatingModeEnabled", settings.floatingModeEnabled)
            put("floatingPosX", settings.floatingPosX.toDouble())
            put("floatingPosY", settings.floatingPosY.toDouble())
            put("hapticEnabled", settings.hapticEnabled)
            put("clipboardHistoryEnabled", settings.clipboardHistoryEnabled)
            put("serifMiringEnabled", settings.serifMiringEnabled)
            put("swapPeriodCommaEnabled", settings.swapPeriodCommaEnabled)
            put("swapSymbolPanelsEnabled", settings.swapSymbolPanelsEnabled)
            put("autoSyncDriveEnabled", settings.autoSyncDriveEnabled)
            put("syncIntervalMinutes", SyncScheduleInterval.fromMinutes(settings.syncIntervalMinutes).minutes)
            put("driveFolderName", settings.driveFolderName.ifBlank { "KetikKu" })
            if (settings.driveShortcutFolderId.isNotBlank()) {
                put("driveShortcutFolderId", settings.driveShortcutFolderId)
            }
        }
        return JSONObject().apply {
            put("updatedAtMillis", settings.updatedAtMillis)
            put("settings", settingsObj)
        }.toString(2)
    }

    fun encodeCompactSettingsJson(settings: KeyboardSettings): String = settingsAdapter.toJson(settings)

    fun decodeSettingsBackupJson(json: String): SettingsBackupEnvelope? {
        runCatching {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) return@runCatching null
            val root = JSONObject(trimmed)
            val sObj = root.optJSONObject("settings")
                ?: if (root.has("floatingModeEnabled") ||
                    root.has("hapticEnabled") ||
                    root.has("driveFolderName") ||
                    root.has("swapPeriodCommaEnabled") ||
                    root.has("swapSymbolPanelsEnabled")
                ) {
                    root
                } else {
                    return@runCatching null
                }
            val topUpdatedAt = root.optLong("updatedAtMillis", 0L)
            val innerUpdatedAt = sObj.optLong("updatedAtMillis", 0L)
            val effectiveUpdatedAt = maxOf(topUpdatedAt, innerUpdatedAt)
            val decodedSettings = KeyboardSettings(
                heightPreset = KeyboardHeightPreset.fromName(sObj.optString("heightPreset", KeyboardHeightPreset.NORMAL.name)).name,
                floatingModeEnabled = sObj.optBoolean("floatingModeEnabled", false),
                floatingPosX = sObj.optDouble("floatingPosX", 0.5).toFloat().coerceIn(0f, 1f),
                floatingPosY = sObj.optDouble("floatingPosY", 0.85).toFloat().coerceIn(0f, 1f),
                hapticEnabled = sObj.optBoolean("hapticEnabled", true),
                clipboardHistoryEnabled = sObj.optBoolean("clipboardHistoryEnabled", true),
                arialItalicEnabled = sObj.optBoolean("arialItalicEnabled", false),
                serifMiringEnabled = sObj.optBoolean("serifMiringEnabled", false),
                swapPeriodCommaEnabled = sObj.optBoolean("swapPeriodCommaEnabled", false),
                swapSymbolPanelsEnabled = sObj.optBoolean("swapSymbolPanelsEnabled", false),
                autoSyncDriveEnabled = sObj.optBoolean("autoSyncDriveEnabled", true),
                syncIntervalMinutes = SyncScheduleInterval.fromMinutes(
                    sObj.optInt("syncIntervalMinutes", SyncScheduleInterval.DEFAULT.minutes)
                ).minutes,
                driveFolderName = sObj.optString("driveFolderName", "KetikKu").ifBlank { "KetikKu" },
                driveShortcutFolderId = sObj.optString("driveShortcutFolderId", ""),
                updatedAtMillis = effectiveUpdatedAt
            )
            SettingsBackupEnvelope(
                schemaVersion = root.optInt("schemaVersion", 1),
                updatedAtMillis = effectiveUpdatedAt,
                settings = decodedSettings
            )
        }.getOrNull()?.let { return it }

        return runCatching { settingsEnvelopeAdapter.fromJson(json) }.getOrNull()
    }

    fun encodeDictionaryBackupJson(
        entries: List<PersonalDictionaryEntry>,
        updatedAtMillis: Long,
        folders: List<String> = emptyList()
    ): String {
        val effectiveFolders = normalizeOrderedFolders(folders, entries)
        val foldersArr = JSONArray().apply {
            effectiveFolders.forEach { put(it) }
        }
        val array = JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("word", entry.word)
                    put("shortcut", entry.shortcut)
                    put("folder", normalizeFolderPath(entry.folder))
                    put("updatedAtMillis", entry.updatedAtMillis)
                    put("deleted", entry.deleted)
                })
            }
        }
        return JSONObject().apply {
            put("updatedAtMillis", updatedAtMillis)
            put("folders", foldersArr)
            put("entries", array)
        }.toString(2)
    }

    fun decodeDictionaryBackupJson(json: String): DictionaryBackupEnvelope? {
        runCatching {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) return@runCatching null
            val fallbackNow = System.currentTimeMillis()
            val parsed = JSONTokener(trimmed).nextValue()
            val rootUpdatedAt: Long
            val arr: JSONArray
            val rawFolders = mutableListOf<String>()
            when (parsed) {
                is JSONObject -> {
                    rootUpdatedAt = parsed.optLong("updatedAtMillis", 0L)
                    val foldersJson = parsed.optJSONArray("folders")
                    if (foldersJson != null) {
                        for (i in 0 until foldersJson.length()) {
                            val rawFolder = foldersJson.optString(i, "").trim()
                            val norm = normalizeFolderPath(rawFolder)
                            if (norm.isNotEmpty()) rawFolders.add(norm)
                        }
                    }
                    arr = parsed.optJSONArray("entries")
                        ?: parsed.optJSONArray("dictionary")
                        ?: return@runCatching null
                }
                is JSONArray -> {
                    rootUpdatedAt = 0L
                    arr = parsed
                }
                else -> return@runCatching null
            }
            val fallbackEntryTs = if (rootUpdatedAt > 0L) rootUpdatedAt else fallbackNow
            val seenIds = HashSet<String>()
            val entries = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val word = obj.optString("word", "").trim()
                    if (word.isEmpty()) continue
                    val shortcut = obj.optString("shortcut", "").trim().lowercase(Locale.ROOT)
                    val folder = normalizeFolderPath(obj.optString("folder", ""))
                    var id = obj.optString("id", "").trim().ifEmpty {
                        stableEntryId(word, shortcut)
                    }
                    if (!seenIds.add(id)) {
                        id = "${id}_$i"
                        seenIds.add(id)
                    }
                    val rawEntryTs = obj.optLong("updatedAtMillis", 0L)
                    val entryTs = if (rawEntryTs > 0L) rawEntryTs else fallbackEntryTs
                    val deleted = obj.optBoolean("deleted", false)
                    add(
                        PersonalDictionaryEntry(
                            id = id,
                            word = word,
                            shortcut = shortcut,
                            folder = folder,
                            updatedAtMillis = entryTs,
                            deleted = deleted
                        )
                    )
                }
            }
            val normalizedFolders = normalizeOrderedFolders(rawFolders, entries)
            DictionaryBackupEnvelope(
                schemaVersion = 1,
                updatedAtMillis = rootUpdatedAt,
                folders = normalizedFolders,
                entries = entries
            )
        }.getOrNull()?.let { return it }

        return runCatching { dictEnvelopeAdapter.fromJson(json) }.getOrNull()
    }

    fun encodeFullBackupJson(
        settings: KeyboardSettings,
        entries: List<PersonalDictionaryEntry>,
        exportedAtMillis: Long = System.currentTimeMillis(),
        folders: List<String> = emptyList()
    ): String {
        val settingsObj = JSONObject(encodeSettingsBackupJson(settings)).getJSONObject("settings")
        val dictRoot = JSONObject(encodeDictionaryBackupJson(entries, exportedAtMillis, folders))
        return JSONObject().apply {
            put("appName", "KetikKu")
            put("exportedAtMillis", exportedAtMillis)
            put("settings", settingsObj)
            put("folders", dictRoot.getJSONArray("folders"))
            put("dictionary", dictRoot.getJSONArray("entries"))
        }.toString(2)
    }

    fun decodeFullBackupJson(json: String): FullBackupEnvelope? =
        runCatching { fullBackupAdapter.fromJson(json) }.getOrNull()

    private fun stableEntryId(word: String, shortcut: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$word|$shortcut".lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        return "ai_" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    suspend fun importLocalBackupJson(rawJson: String): Result<String> {
        val full = decodeFullBackupJson(rawJson)
        if (full != null && full.appName.isNotBlank()) {
            val now = System.currentTimeMillis()
            applyRemoteSettings(full.settings.copy(updatedAtMillis = maxOf(full.settings.updatedAtMillis, now)))
            val merged = mergeRemoteDictionary(full.dictionary, full.folders)
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
            val merged = mergeRemoteDictionary(dictOnly.entries, dictOnly.folders)
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
        prefs[Keys.SWAP_PERIOD_COMMA] = s.swapPeriodCommaEnabled
        prefs[Keys.SWAP_SYMBOL_PANELS] = s.swapSymbolPanelsEnabled
        prefs[Keys.AUTO_SYNC_DRIVE] = s.autoSyncDriveEnabled
        prefs[Keys.SYNC_INTERVAL_MINUTES] = SyncScheduleInterval.fromMinutes(s.syncIntervalMinutes).minutes
        prefs[Keys.DRIVE_FOLDER_NAME] = s.driveFolderName.ifBlank { "KetikKu" }
        prefs[Keys.DRIVE_SHORTCUT_FOLDER_ID] = s.driveShortcutFolderId
        prefs[Keys.SETTINGS_UPDATED_AT] = s.updatedAtMillis
    }

    data class AutoSavableCandidate(
        val word: String,
        val folder: String
    )

    companion object {
        private const val PERSONALITY_MAX_ENTRIES = 600
        const val FOLDER_GMAIL = "Gmail"
        const val FOLDER_NOMOR = "Nomor"

        fun normalizeFolderPath(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val segments = raw.replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }
            if (segments.isEmpty()) return ""
            val adjusted = segments.toMutableList()
            when {
                adjusted[0].equals(FOLDER_GMAIL, ignoreCase = true) -> adjusted[0] = FOLDER_GMAIL
                adjusted[0].equals(FOLDER_NOMOR, ignoreCase = true) -> adjusted[0] = FOLDER_NOMOR
            }
            return adjusted.joinToString("/")
        }

        fun expandFolderAncestors(path: String): List<String> {
            val normalized = normalizeFolderPath(path)
            if (normalized.isEmpty()) return emptyList()
            val parts = normalized.split('/')
            return buildList(parts.size) {
                for (i in 1..parts.size) {
                    add(parts.subList(0, i).joinToString("/"))
                }
            }
        }

        fun isSameOrDescendantFolder(path: String, parent: String): Boolean {
            val normPath = normalizeFolderPath(path)
            val normParent = normalizeFolderPath(parent)
            if (normParent.isEmpty()) return false
            return normPath == normParent || normPath.startsWith("$normParent/")
        }

        fun rewriteFolderPrefix(path: String, oldPrefix: String, newPrefix: String): String {
            val normPath = normalizeFolderPath(path)
            val normOld = normalizeFolderPath(oldPrefix)
            val normNew = normalizeFolderPath(newPrefix)
            if (normOld.isEmpty() || normNew.isEmpty()) return normPath
            return when {
                normPath == normOld -> normNew
                normPath.startsWith("$normOld/") -> "$normNew/${normPath.removePrefix("$normOld/")}"
                else -> normPath
            }
        }

        /**
         * Preserves ordered folder list:
         * - Root auto-folders pinned at top in exact order: "Gmail", then "Nomor" (if present).
         * - All other custom folders preserve existing order (newest-first on creation).
         * - Ancestors for active entry folders are automatically ensured.
         */
        fun normalizeOrderedFolders(
            explicitFolders: List<String>,
            entries: List<PersonalDictionaryEntry>
        ): List<String> {
            val orderedAll = linkedSetOf<String>()
            explicitFolders.forEach { raw ->
                expandFolderAncestors(raw).forEach { orderedAll.add(it) }
            }
            entries.forEach { entry ->
                if (!entry.deleted && entry.folder.isNotBlank()) {
                    expandFolderAncestors(entry.folder).forEach { orderedAll.add(it) }
                }
            }

            val result = ArrayList<String>(orderedAll.size)
            if (orderedAll.contains(FOLDER_GMAIL)) result.add(FOLDER_GMAIL)
            if (orderedAll.contains(FOLDER_NOMOR)) result.add(FOLDER_NOMOR)
            orderedAll.forEach { path ->
                if (path != FOLDER_GMAIL && path != FOLDER_NOMOR) {
                    result.add(path)
                }
            }
            return result
        }

        fun prependFoldersToStorage(
            existingFolders: List<String>,
            newFolderPaths: List<String>,
            entries: List<PersonalDictionaryEntry>
        ): List<String> {
            val existingNorm = existingFolders.map(::normalizeFolderPath).filter { it.isNotEmpty() }
            val existingSet = existingNorm.toHashSet()
            val addedInOrder = linkedSetOf<String>()
            newFolderPaths.asReversed().forEach { raw ->
                expandFolderAncestors(raw).forEach { ancestor ->
                    if (!existingSet.contains(ancestor)) {
                        addedInOrder.add(ancestor)
                    }
                }
            }
            // Newly created custom folders go before existing custom folders (Gmail & Nomor stay pinned first via normalizeOrderedFolders)
            val combined = addedInOrder.toList() + existingNorm
            return normalizeOrderedFolders(combined, entries)
        }

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
                folder = "",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 1L
            ),
            PersonalDictionaryEntry(
                id = "preset_omw",
                word = "Sedang dalam perjalanan",
                shortcut = "omw",
                folder = "",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 1L
            ),
            PersonalDictionaryEntry(
                id = "preset_asap",
                word = "As soon as possible",
                shortcut = "asap",
                folder = "",
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
         * Mengekstrak kandidat yang otomatis disimpan ke Kamus Pribadi (beserta folder otomatisnya):
         * 1. Alamat email berakhiran @gmail.com (>= 20 karakter) -> folder "Gmail".
         * 2. Nomor (angka telepon/rekening/identitas, >= 11 digit) -> folder "Nomor".
         */
        fun extractAutoSavableCategorizedEntries(rawText: String): List<AutoSavableCandidate> {
            val (start, end) = TextChunker.trimmedBounds(rawText)
            if (start >= end) return emptyList()

            val results = linkedMapOf<String, AutoSavableCandidate>()
            val emailRanges = mutableListOf<IntRange>()

            GMAIL_REGEX.findAll(rawText, start).forEach { match ->
                if (match.range.first >= end) return@forEach
                emailRanges.add(match.range)
                val email = match.value.trim()
                if (email.length >= 20) {
                    results.putIfAbsent(email.lowercase(Locale.ROOT), AutoSavableCandidate(email, FOLDER_GMAIL))
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
                    results.putIfAbsent(rawNumber.lowercase(Locale.ROOT), AutoSavableCandidate(rawNumber, FOLDER_NOMOR))
                }
            }
            return results.values.toList()
        }

        fun extractAutoSavableEntries(rawText: String): List<String> =
            extractAutoSavableCategorizedEntries(rawText).map { it.word }
    }
}
