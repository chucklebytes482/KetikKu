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
import kotlinx.coroutines.flow.onEach
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
        val SETTINGS_UPDATED_AT = longPreferencesKey("settings_updated_at")

        val DICTIONARY_JSON = stringPreferencesKey("dictionary_json")
        val FOLDERS_JSON = stringPreferencesKey("dictionary_folders_json")
        val PERSONALITY_JSON = stringPreferencesKey("suggestion_personality_json")
        val CLIPBOARD_REVISION = longPreferencesKey("clipboard_revision")
        val DISMISSED_CLIPBOARD_ID = stringPreferencesKey("dismissed_clipboard_id")
        val DISMISSED_CLIPBOARD_SIGNATURE = stringPreferencesKey("dismissed_clipboard_signature")

        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val DEVICE_BACKUP_TREE_URI = stringPreferencesKey("device_backup_tree_uri")
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
                updatedAtMillis = prefs[Keys.SETTINGS_UPDATED_AT] ?: 0L
            )
        }
        .distinctUntilChanged()

    val dictionaryFlow: Flow<List<PersonalDictionaryEntry>> = dataStore.data
        .onEach { prefs ->
            val entries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            if (entries.any { DictionaryEngine.isA2fEntry(it) && it.secretCipher.isNotBlank() && !SecretVault.isDeviceCipher(it.secretCipher) }) {
                migrateLegacyA2f()
            }
        }
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
                deviceBackupTreeUri = prefs[Keys.DEVICE_BACKUP_TREE_URI] ?: "",
                pendingChanges = prefs[Keys.PENDING_CHANGES] ?: false
            )
        }
        .distinctUntilChanged()

    suspend fun getCurrentSettings(): KeyboardSettings = settingsFlow.first()

    suspend fun getCurrentDictionary(): List<PersonalDictionaryEntry> = dictionaryFlow.first()

    suspend fun getCurrentFolders(): List<String> = foldersFlow.first()

    suspend fun getCurrentSyncLocalState(): SyncLocalState = syncLocalStateFlow.first()

    private suspend fun migrateLegacyA2f() {
        dataStore.edit { prefs ->
            val entries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val migrated = entries.map { entry ->
                if (!DictionaryEngine.isA2fEntry(entry) || entry.secretCipher.isBlank() || SecretVault.isDeviceCipher(entry.secretCipher)) entry
                else {
                    val seed = SecretVault.decryptOrNull(entry.secretCipher)
                        ?: error("Seed A2F lama tidak dapat dimigrasi; data asli dipertahankan")
                    entry.copy(secretCipher = SecretVault.encrypt(seed))
                }
            }
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(migrated)
        }
    }

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
                updatedAtMillis = prefs[Keys.SETTINGS_UPDATED_AT] ?: 0L
            )
            val updated = transform(current).copy(
                updatedAtMillis = if (markPendingSync) now else current.updatedAtMillis
            )
            result = updated
            writeSettingsToPrefs(prefs, updated)
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
        folder: String = "",
        secretCipher: String? = null,
        email: String = "",
        website: String = ""
    ) {
        val now = System.currentTimeMillis()
        val trimmedWord = word.trim()
        val trimmedShortcut = shortcut.trim().lowercase(Locale.ROOT)
        val normalizedFolder = normalizeFolderPath(folder)
        val cleanEmail = email.trim()
        val cleanSite = website.trim()
        dataStore.edit { prefs ->
            val current = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val existing = existingId?.let { id -> current.firstOrNull { it.id == id && !it.deleted } }
            val id = existingId ?: UUID.randomUUID().toString().take(12)
            // Edit hanya boleh mengubah pintasan; folder dan kata tetap dari entri asli.
            val targetFolder = existing?.let { normalizeFolderPath(it.folder) } ?: normalizedFolder
            require(targetFolder != FOLDER_SAMPAH) { "Entri di Sampah harus dipulihkan dulu" }
            val isA2f = targetFolder == FOLDER_A2F

            val wordStored: String
            var cipher = ""
            var seedLeft = 0
            var newA2f = false
            if (isA2f) {
                if (existing == null) {
                    require(EMAIL_REGEX.matches(cleanEmail)) { "Email A2F wajib diisi dengan benar" }
                    require(cleanSite.isNotBlank()) { "Website A2F wajib diisi" }
                    require(!secretCipher.isNullOrBlank() && SecretVault.decryptOrNull(secretCipher) != null) { "Seed A2F wajib diisi" }
                    wordStored = "$cleanEmail\n$cleanSite"
                    cipher = secretCipher
                    newA2f = true
                } else {
                    wordStored = existing.word
                    cipher = existing.secretCipher
                    seedLeft = existing.seedEditsLeft
                    if (secretCipher != null) {
                        require(existing.seedEditsLeft > 0) { "Seed A2F sudah terkunci" }
                        require(SecretVault.decryptOrNull(secretCipher) != null) { "Seed A2F tidak dapat dibaca" }
                        cipher = secretCipher
                        seedLeft = existing.seedEditsLeft - 1
                    }
                }
                require(trimmedShortcut.isNotBlank() && trimmedShortcut.length <= 30 && trimmedShortcut.all { it.isLetterOrDigit() }) {
                    "Pintasan A2F wajib 1–30 huruf/angka"
                }
            } else if (existing != null) {
                wordStored = existing.word
            } else {
                require(trimmedWord.isNotEmpty()) { "Kata wajib diisi" }
                if (targetFolder == FOLDER_GMAIL) require(trimmedWord.contains('@')) { "Gmail harus berupa alamat email" }
                wordStored = trimmedWord
            }

            val shortcutChanged = existing == null || existing.shortcut.trim().lowercase(Locale.ROOT) != trimmedShortcut
            if (trimmedShortcut.isNotEmpty() && shortcutChanged) {
                require(current.none { other ->
                    other.id != id && !other.deleted && normalizeFolderPath(other.folder) != FOLDER_SAMPAH &&
                        other.shortcut.trim().equals(trimmedShortcut, ignoreCase = true)
                }) { "Pintasan sudah dipakai" }
            }
            val finalShortcut = when {
                isA2f -> trimmedShortcut
                trimmedShortcut.isNotEmpty() -> trimmedShortcut
                else -> autoShortcut(targetFolder, wordStored, current, id)
            }

            val entry = PersonalDictionaryEntry(
                id = id,
                word = wordStored,
                shortcut = finalShortcut,
                folder = targetFolder,
                language = language,
                updatedAtMillis = now,
                deleted = false,
                secretCipher = if (isA2f) cipher else "",
                trashedFrom = "",
                seedEditsLeft = if (isA2f) seedLeft else 0
            )
            var updatedList = current.filterNot { it.id == id } + entry
            if (newA2f) {
                // Email A2F otomatis masuk Gmail jika belum ada.
                val gmailExists = updatedList.any { other ->
                    !other.deleted && other.word.trim().equals(cleanEmail, ignoreCase = true) &&
                        normalizeFolderPath(other.folder) in setOf(FOLDER_GMAIL, FOLDER_SAMPAH)
                }
                if (!gmailExists) {
                    updatedList = updatedList + PersonalDictionaryEntry(
                        id = UUID.randomUUID().toString().take(12),
                        word = cleanEmail,
                        shortcut = autoShortcut(FOLDER_GMAIL, cleanEmail, updatedList, null),
                        folder = FOLDER_GMAIL,
                        language = language,
                        updatedAtMillis = now
                    )
                }
            }
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedList)
            val folderNames = buildList {
                if (targetFolder.isNotEmpty()) add(targetFolder)
                if (newA2f) add(FOLDER_GMAIL)
            }
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(
                prependFoldersToStorage(
                    existingFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                    newFolderPaths = folderNames,
                    entries = updatedList
                )
            )
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    /** Duplikasi A2F: seed dan email/website ikut, pintasan baru, seed boleh diedit 1 kali. */
    suspend fun duplicateA2fEntry(sourceId: String): String {
        var newId = ""
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val current = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val src = current.firstOrNull {
                it.id == sourceId && !it.deleted && normalizeFolderPath(it.folder) == FOLDER_A2F
            } ?: error("Entri A2F tidak ditemukan")
            val id = UUID.randomUUID().toString().take(12)
            val copy = src.copy(
                id = id,
                shortcut = nextFreeShortcut(src.shortcut.trim().lowercase(Locale.ROOT), current, null),
                updatedAtMillis = now,
                trashedFrom = "",
                seedEditsLeft = 1
            )
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(current + copy)
            prefs[Keys.PENDING_CHANGES] = true
            newId = id
        }
        return newId
    }

    suspend fun deleteDictionaryEntry(entryId: String) {
        deleteEntriesAndFolders(setOf(entryId), emptySet())
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
        if (normOld.isEmpty() || isPrimaryFolder(normOld)) return
        val normNew = normalizeFolderPath(newNameOrPath)
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
        val normSources = folderPaths
            .map(::normalizeFolderPath)
            .filter { it.isNotEmpty() && !isPrimaryFolder(it) }
            .toSet()
        if (entryIds.isEmpty() && normSources.isEmpty()) return

        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val currentEntries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val currentFolders = normalizeOrderedFolders(
                explicitFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                entries = currentEntries
            )

            // Entri bersifat read-only untuk folder: entri tidak pernah dipindah. Folder yang masih berisi
            // entri juga tidak ikut dipindah/digabung, karena itu akan memindahkan entri di dalamnya.
            val foldersWithEntries = currentEntries
                .filter { !it.deleted }
                .map { normalizeFolderPath(it.folder) }
                .toSet()
            val foldersToMerge = normSources.filter { it != normTarget && it !in foldersWithEntries }.toSet()
            val updatedEntries = currentEntries

            val remainingFolders = currentFolders.filterNot { it in foldersToMerge }
            val addedFolders = if (normTarget.isNotEmpty()) listOf(normTarget) else emptyList()
            val finalFolders = prependFoldersToStorage(
                existingFolders = remainingFolders,
                newFolderPaths = addedFolders,
                entries = updatedEntries
            )

            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedEntries)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(finalFolders)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    /** Hapus = pindah ke Sampah. Tidak ada hapus permanen. */
    suspend fun deleteEntriesAndFolders(
        entryIds: Set<String>,
        folderPaths: Set<String>
    ) {
        val normFolders = folderPaths
            .map(::normalizeFolderPath)
            .filter { it.isNotEmpty() && !isPrimaryFolder(it) }
            .toSet()
        if (entryIds.isEmpty() && normFolders.isEmpty()) return
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val currentEntries = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val updatedEntries = moveToTrash(currentEntries, entryIds, normFolders, now)
            val remainingFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]).filterNot {
                normalizeFolderPath(it) in normFolders
            }
            val finalFolders = normalizeOrderedFolders(remainingFolders, updatedEntries)
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updatedEntries)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(finalFolders)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    /**
     * Generate ulang pintasan otomatis untuk semua entri aktif di folder Gmail, Kata Sandi, dan Nomor.
     * Urutan: entri terlama dulu, supaya hasilnya deterministik. Mengembalikan jumlah entri yang berubah.
     */
    suspend fun regenerateAutoShortcuts(): Int {
        val now = System.currentTimeMillis()
        var changed = 0
        dataStore.edit { prefs ->
            val list = parseDictionaryList(prefs[Keys.DICTIONARY_JSON]).toMutableList()
            val autoFolders = setOf(FOLDER_GMAIL, FOLDER_KATA_SANDI, FOLDER_NOMOR)
            val targets = list
                .filter { !it.deleted && normalizeFolderPath(it.folder) in autoFolders }
                .sortedWith(compareBy({ it.updatedAtMillis }, { it.id }))
            if (targets.isNotEmpty()) {
                val originalShortcut = targets.associate { it.id to it.shortcut }
                // Kosongkan dulu pintasan target supaya entri tidak bentrok dengan lama dirinya sendiri.
                targets.forEach { t ->
                    val i = list.indexOfFirst { it.id == t.id }
                    list[i] = list[i].copy(shortcut = "")
                }
                targets.forEach { t ->
                    val i = list.indexOfFirst { it.id == t.id }
                    val e = list[i]
                    val fresh = autoShortcut(normalizeFolderPath(e.folder), e.word, list, e.id)
                    if (fresh != originalShortcut[t.id]) {
                        changed++
                        list[i] = e.copy(shortcut = fresh, updatedAtMillis = now)
                    } else {
                        list[i] = e.copy(shortcut = fresh)
                    }
                }
                if (changed > 0) {
                    prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(list)
                    prefs[Keys.PENDING_CHANGES] = true
                }
            }
        }
        return changed
    }

    /** Pulihkan dari Sampah ke folder asal. Pintasan yang bentrok diberi angka. */
    suspend fun restoreFromTrash(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val updated = parseDictionaryList(prefs[Keys.DICTIONARY_JSON]).toMutableList()
            val restoredTargets = mutableListOf<String>()
            for (i in updated.indices) {
                val e = updated[i]
                if (e.id !in entryIds || e.deleted || normalizeFolderPath(e.folder) != FOLDER_SAMPAH) continue
                val target = normalizeFolderPath(e.trashedFrom)
                val shortcut = if (e.shortcut.isBlank()) e.shortcut
                else nextFreeShortcut(e.shortcut.trim().lowercase(Locale.ROOT), updated, e.id)
                updated[i] = e.copy(folder = target, trashedFrom = "", shortcut = shortcut, updatedAtMillis = now)
                if (target.isNotEmpty()) restoredTargets.add(target)
            }
            val finalFolders = normalizeOrderedFolders(
                parseFolderList(prefs[Keys.FOLDERS_JSON]) + restoredTargets,
                updated
            )
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(updated)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(finalFolders)
            prefs[Keys.PENDING_CHANGES] = true
        }
    }

    private fun moveToTrash(
        current: List<PersonalDictionaryEntry>,
        ids: Set<String>,
        folders: Set<String>,
        now: Long
    ): List<PersonalDictionaryEntry> = current.map { entry ->
        val norm = normalizeFolderPath(entry.folder)
        if (!entry.deleted && norm != FOLDER_SAMPAH && (entry.id in ids || norm in folders)) {
            entry.copy(folder = FOLDER_SAMPAH, trashedFrom = norm, updatedAtMillis = now)
        } else {
            entry
        }
    }

    suspend fun mergeRemoteDictionary(
        remoteEntries: List<PersonalDictionaryEntry>,
        remoteFolders: List<String> = emptyList()
    ): List<PersonalDictionaryEntry> {
        var mergedResult = emptyList<PersonalDictionaryEntry>()
        dataStore.edit { prefs ->
            val local = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val safeRemote = remoteEntries.map { entry ->
                if (!DictionaryEngine.isA2fEntry(entry)) entry else {
                    val seed = SecretVault.decryptOrNull(entry.secretCipher)
                        ?: error("Cadangan A2F terkunci untuk perangkat lain; seed tidak dapat dipulihkan di sini")
                    entry.copy(word = "••••••••", secretCipher = if (SecretVault.isDeviceCipher(entry.secretCipher)) entry.secretCipher else SecretVault.encrypt(seed))
                }
            }
            require(safeRemote.map { it.id }.distinct().size == safeRemote.size) { "ID cadangan duplikat" }
            safeRemote.forEach { incoming ->
                val existing = local.firstOrNull { it.id == incoming.id }
                if (existing != null && (DictionaryEngine.isA2fEntry(existing) || DictionaryEngine.isA2fEntry(incoming))) {
                    require(DictionaryEngine.isA2fEntry(existing) && DictionaryEngine.isA2fEntry(incoming) &&
                        SecretVault.decryptOrNull(existing.secretCipher) == SecretVault.decryptOrNull(incoming.secretCipher)) {
                        "Akun A2F bentrok; data tidak diubah"
                    }
                }
            }
            mergedResult = mergeDictionaryLists(local, safeRemote)
            val activeA2f = mergedResult.filter { DictionaryEngine.isA2fEntry(it) && !it.deleted }
            require(activeA2f.all { it.shortcut.isNotBlank() && it.shortcut.length <= 30 && it.shortcut.all { c -> c.isLetterOrDigit() } }) { "Pintasan A2F tidak valid" }
            require(activeA2f.map { it.shortcut.lowercase(Locale.ROOT) }.distinct().size == activeA2f.size) { "Pintasan A2F duplikat; data tidak diubah" }
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

    /**
     * Cadangan A2F otomatis hanya MENAMBAH akun yang id-nya belum ada lokal (termasuk yang sudah dihapus lokal,
     * agar tidak bangkit lagi). Pintasan yang bentrok dengan akun aktif lokal dilewati. Tidak pernah menghapus.
     */
    suspend fun addMissingA2fAccounts(incoming: List<PersonalDictionaryEntry>): Int {
        var added = 0
        dataStore.edit { prefs ->
            val local = parseDictionaryList(prefs[Keys.DICTIONARY_JSON])
            val localIds = local.map { it.id }.toSet()
            val activeShortcuts = local
                .filter { DictionaryEngine.isA2fEntry(it) && !it.deleted }
                .map { it.shortcut.lowercase(Locale.ROOT) }
                .toMutableSet()
            val toAdd = mutableListOf<PersonalDictionaryEntry>()
            for (entry in incoming) {
                if (entry.id in localIds || entry.id.isBlank()) continue
                val shortcut = entry.shortcut.lowercase(Locale.ROOT)
                if (!activeShortcuts.add(shortcut)) continue
                toAdd += entry.copy(word = "••••••••", folder = FOLDER_A2F)
            }
            if (toAdd.isNotEmpty()) {
                val merged = local + toAdd
                val folders = prependFoldersToStorage(
                    existingFolders = parseFolderList(prefs[Keys.FOLDERS_JSON]),
                    newFolderPaths = listOf(FOLDER_A2F),
                    entries = merged
                )
                prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(merged)
                prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(folders)
                added = toAdd.size
            }
        }
        return added
    }

    suspend fun replaceDictionaryAndFolders(
        entries: List<PersonalDictionaryEntry>,
        folders: List<String> = emptyList()
    ): Pair<List<PersonalDictionaryEntry>, List<String>> {
        var normalizedEntries = entries.map { entry ->
            val normFolder = normalizeFolderPath(entry.folder)
            if (normFolder == entry.folder) entry else entry.copy(folder = normFolder)
        }
        var normalizedFolders = normalizeOrderedFolders(folders, normalizedEntries)
        dataStore.edit { prefs ->
            // Generic automatic backups must not remove local A2F accounts.
            val existingA2f = parseDictionaryList(prefs[Keys.DICTIONARY_JSON]).filter { DictionaryEngine.isA2fEntry(it) }
            val protectedIds = existingA2f.map { it.id }.toSet()
            require(normalizedEntries.none { it.id in protectedIds && !DictionaryEngine.isA2fEntry(it) }) { "ID cadangan bentrok dengan A2F; data tidak diubah" }
            // Automatic backup is never an authority for A2F, even when IDs match.
            normalizedEntries = normalizedEntries.filterNot { DictionaryEngine.isA2fEntry(it) } + existingA2f
            normalizedFolders = normalizeOrderedFolders(folders, normalizedEntries)
            prefs[Keys.DICTIONARY_JSON] = dictListAdapter.toJson(normalizedEntries)
            prefs[Keys.FOLDERS_JSON] = stringListAdapter.toJson(normalizedFolders)
            prefs[Keys.PENDING_CHANGES] = false
        }
        return normalizedEntries to normalizedFolders
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
        pendingChanges: Boolean = false
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_AT] = lastSyncedAtMillis
            prefs[Keys.LAST_SYNC_MESSAGE] = message
            prefs[Keys.PENDING_CHANGES] = pendingChanges
        }
    }

    suspend fun saveDeviceBackupTreeUri(treeUri: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DEVICE_BACKUP_TREE_URI] = treeUri.trim()
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
                    if (entry.secretCipher.isNotBlank()) put("secretCipher", entry.secretCipher)
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
                    val secretCipher = obj.optString("secretCipher", "")
                    add(
                        PersonalDictionaryEntry(
                            id = id,
                            word = word,
                            shortcut = shortcut,
                            folder = folder,
                            updatedAtMillis = entryTs,
                            deleted = deleted,
                            secretCipher = secretCipher
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
        val dictRoot = JSONObject(encodeDictionaryBackupJson(
            entries.filterNot { DictionaryEngine.isA2fEntry(it) }, exportedAtMillis,
            folders.filterNot { normalizeFolderPath(it) == FOLDER_A2F }
        ))
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
            if (full.dictionary.any { DictionaryEngine.isA2fEntry(it) }) {
                return Result.failure(IllegalArgumentException("Berkas ini berisi seed A2F dan tidak dapat dipulihkan di sini; setelan tidak diubah"))
            }
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
            if (dictOnly.entries.any { DictionaryEngine.isA2fEntry(it) }) {
                return Result.failure(IllegalArgumentException("Berkas ini berisi seed A2F dan tidak dapat dipulihkan di sini"))
            }
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
        const val FOLDER_PRIBADI = "Pribadi"
        const val FOLDER_KATA_SANDI = "Kata Sandi"
        const val FOLDER_KREDENSIAL = "Kredensial"
        const val FOLDER_A2F = "A2F"
        const val FOLDER_SAMPAH = "Sampah"
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Jenis berkas cadangan untuk entri pada folder ini (satu berkas per jenis). */
        fun backupKindFor(folder: String): String =
            when (normalizeFolderPath(folder).substringBefore('/')) {
                FOLDER_KATA_SANDI -> "kata_sandi"
                FOLDER_KREDENSIAL -> "kredensial"
                FOLDER_A2F -> "a2f"
                else -> "umum"
            }
        const val FOLDER_BALASAN_CEPAT = "Balasan Cepat"
        private const val LEGACY_FOLDER_KEUANGAN = "Keuangan"

        val PRIMARY_FOLDERS: List<String> = listOf(
            FOLDER_GMAIL,
            FOLDER_KATA_SANDI,
            FOLDER_NOMOR,
            FOLDER_A2F,
            FOLDER_KREDENSIAL,
            FOLDER_PRIBADI,
            FOLDER_BALASAN_CEPAT,
            FOLDER_SAMPAH
        )

        /** Dasar pintasan otomatis: Gmail = huruf/angka pertama + gm, Kata Sandi = huruf pertama + ks, Nomor = 3 digit terakhir. */
        fun autoShortcutBase(folder: String, word: String): String {
            val w = word.trim()
            return when (normalizeFolderPath(folder)) {
                FOLDER_GMAIL -> w.substringBefore('@').firstOrNull { it.isLetterOrDigit() }
                    ?.lowercaseChar()?.let { "${it}gm" }.orEmpty()
                FOLDER_KATA_SANDI -> w.firstOrNull { it.isLetterOrDigit() }
                    ?.lowercaseChar()?.let { "${it}ks" }.orEmpty()
                FOLDER_NOMOR -> w.filter { it.isDigit() }.takeLast(3)
                else -> ""
            }
        }

        /** Pintasan otomatis unik: bentrok diberi angka (agm2, agm3, ...). Kosong jika folder tidak mendukung. */
        fun autoShortcut(
            folder: String,
            word: String,
            others: List<PersonalDictionaryEntry>,
            selfId: String?
        ): String {
            val base = autoShortcutBase(folder, word)
            return if (base.isEmpty()) "" else nextFreeShortcut(base, others, selfId)
        }

        /** Pintasan pertama yang belum dipakai entri aktif lain (entri Sampah tidak dihitung). */
        fun nextFreeShortcut(
            base: String,
            others: List<PersonalDictionaryEntry>,
            selfId: String?
        ): String {
            if (base.isEmpty()) return base
            val taken = others.asSequence()
                .filter { it.id != selfId && !it.deleted && normalizeFolderPath(it.folder) != FOLDER_SAMPAH }
                .map { it.shortcut.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .toSet()
            if (base !in taken) return base
            var n = 2
            while ("$base$n" in taken) n++
            return "$base$n"
        }

        fun isPrimaryFolder(name: String?): Boolean {
            val norm = normalizeFolderPath(name)
            return norm in PRIMARY_FOLDERS
        }

        fun normalizeFolderPath(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val cleaned = raw.replace('\\', ' ')
                .replace('/', ' ')
                .trim()
                .replace(Regex("\\s+"), " ")
            if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return ""
            return when {
                cleaned.equals(FOLDER_GMAIL, ignoreCase = true) -> FOLDER_GMAIL
                cleaned.equals(FOLDER_NOMOR, ignoreCase = true) -> FOLDER_NOMOR
                cleaned.equals(FOLDER_PRIBADI, ignoreCase = true) -> FOLDER_PRIBADI
                cleaned.equals(FOLDER_KATA_SANDI, ignoreCase = true) -> FOLDER_KATA_SANDI
                cleaned.equals(FOLDER_KREDENSIAL, ignoreCase = true) -> FOLDER_KREDENSIAL
                cleaned.equals(FOLDER_A2F, ignoreCase = true) -> FOLDER_A2F
                cleaned.equals(FOLDER_SAMPAH, ignoreCase = true) -> FOLDER_SAMPAH
                cleaned.equals(FOLDER_BALASAN_CEPAT, ignoreCase = true) ||
                    cleaned.equals(LEGACY_FOLDER_KEUANGAN, ignoreCase = true) -> FOLDER_BALASAN_CEPAT
                else -> cleaned
            }
        }

        fun expandFolderAncestors(path: String): List<String> {
            val normalized = normalizeFolderPath(path)
            return if (normalized.isEmpty()) emptyList() else listOf(normalized)
        }

        fun isSameOrDescendantFolder(path: String, parent: String): Boolean {
            val normPath = normalizeFolderPath(path)
            val normParent = normalizeFolderPath(parent)
            if (normParent.isEmpty()) return false
            return normPath == normParent
        }

        fun rewriteFolderPrefix(path: String, oldPrefix: String, newPrefix: String): String {
            val normPath = normalizeFolderPath(path)
            val normOld = normalizeFolderPath(oldPrefix)
            val normNew = normalizeFolderPath(newPrefix)
            if (normOld.isEmpty() || normNew.isEmpty()) return normPath
            return if (normPath == normOld) normNew else normPath
        }

        /**
         * Preserves ordered single-level folder list (no subfolders):
         * - 6 permanent primary folders pinned at top in exact order:
         *   Gmail, Nomor, Pribadi, Kata Sandi, Kredensial, Balasan Cepat.
         * - All other custom folders preserve existing order (newest-first on creation).
         */
        fun normalizeOrderedFolders(
            explicitFolders: List<String>,
            entries: List<PersonalDictionaryEntry>
        ): List<String> {
            val orderedAll = linkedSetOf<String>()
            orderedAll.addAll(PRIMARY_FOLDERS)

            explicitFolders.forEach { raw ->
                val norm = normalizeFolderPath(raw)
                if (norm.isNotEmpty()) orderedAll.add(norm)
            }
            entries.forEach { entry ->
                if (!entry.deleted && entry.folder.isNotBlank()) {
                    val norm = normalizeFolderPath(entry.folder)
                    if (norm.isNotEmpty()) orderedAll.add(norm)
                }
            }

            val result = ArrayList<String>(orderedAll.size)
            PRIMARY_FOLDERS.forEach { p ->
                if (orderedAll.contains(p)) result.add(p)
            }
            orderedAll.forEach { path ->
                if (path !in PRIMARY_FOLDERS) {
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
                val norm = normalizeFolderPath(raw)
                if (norm.isNotEmpty() && !existingSet.contains(norm)) {
                    addedInOrder.add(norm)
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
                folder = "Pribadi",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 1L
            ),
            PersonalDictionaryEntry(
                id = "preset_omw",
                word = "Sedang dalam perjalanan",
                shortcut = "omw",
                folder = "Pribadi",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 1L
            ),
            PersonalDictionaryEntry(
                id = "preset_asap",
                word = "As soon as possible",
                shortcut = "asap",
                folder = "Pribadi",
                language = KeyboardLanguage.ENGLISH.code,
                updatedAtMillis = 1L
            )
        )

        private val GMAIL_REGEX = Regex(
            "(?<![A-Za-z0-9._%+-])([A-Za-z0-9._%+-]{1,244}@gmail\\.com)(?![A-Za-z0-9._%+-])",
            RegexOption.IGNORE_CASE
        )
        private val NUMBER_REGEX = Regex(
            "(?<![A-Za-z0-9._%+@-])((?:\\+62|62|08)[\\d\\s-]{8,30}\\d)(?![A-Za-z0-9._%+@-])"
        )

        /**
         * Mengekstrak kandidat yang otomatis disimpan ke Kamus Pribadi (beserta folder otomatisnya):
         * 1. Alamat email berakhiran @gmail.com (>= 20 karakter) -> folder "Gmail".
         * 2. Nomor telepon berawalan +62, 62, atau 08 (>= 11 digit) -> folder "Nomor".
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
                val hasValidPrefix = rawNumber.startsWith("+62") ||
                    rawNumber.startsWith("62") ||
                    rawNumber.startsWith("08")
                val digitCount = rawNumber.count { it.isDigit() }
                if (hasValidPrefix && digitCount >= 11 && rawNumber.length >= 11) {
                    results.putIfAbsent(rawNumber.lowercase(Locale.ROOT), AutoSavableCandidate(rawNumber, FOLDER_NOMOR))
                }
            }
            return results.values.toList()
        }

        fun extractAutoSavableEntries(rawText: String): List<String> =
            extractAutoSavableCategorizedEntries(rawText).map { it.word }
    }
}
