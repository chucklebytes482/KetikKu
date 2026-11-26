package com.example.ui

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeviceDictionaryBackupManager
import com.example.data.DeviceSyncResult
import com.example.data.SettingsRepository
import com.example.data.DictionaryEngine
import com.example.data.SecretVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ime.KeyboardController
import com.example.ime.TextFieldImeTarget
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.SyncLocalState
import com.example.model.WordPersonalityStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ImeActivationStatus {
    DISABLED,
    ENABLED_NOT_DEFAULT,
    ACTIVE_DEFAULT
}

sealed interface DeviceSyncUiState {
    data object Idle : DeviceSyncUiState
    data object Syncing : DeviceSyncUiState
    data class NeedsFolderAccess(val message: String) : DeviceSyncUiState
    data class Error(val message: String) : DeviceSyncUiState
}

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val deviceBackupManager: DeviceDictionaryBackupManager? = null
) : ViewModel() {

    val settings: StateFlow<KeyboardSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), KeyboardSettings())

    val dictionary: StateFlow<List<PersonalDictionaryEntry>> = settingsRepository.dictionaryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val folders: StateFlow<List<String>> = settingsRepository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val clipboard: StateFlow<List<ClipboardItem>> = settingsRepository.clipboardFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dismissedClipboardId: StateFlow<String?> = settingsRepository.dismissedClipboardIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val dismissedClipboardSignature: StateFlow<String?> = settingsRepository.dismissedClipboardSignatureFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val personality: StateFlow<Map<String, WordPersonalityStat>> = settingsRepository.personalityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val syncLocalState: StateFlow<SyncLocalState> = settingsRepository.syncLocalStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncLocalState())

    private val _deviceSyncUiState = MutableStateFlow<DeviceSyncUiState>(DeviceSyncUiState.Idle)
    val deviceSyncUiState: StateFlow<DeviceSyncUiState> = _deviceSyncUiState.asStateFlow()

    @Volatile
    private var lastAutoSyncTriggerMillis: Long = 0L

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _imeStatus = MutableStateFlow(ImeActivationStatus.DISABLED)
    val imeStatus: StateFlow<ImeActivationStatus> = _imeStatus.asStateFlow()

    var playgroundText by mutableStateOf(TextFieldValue(""))
        private set

    var onNavigateToSettings: () -> Unit = {}

    val playgroundController = KeyboardController(
        target = TextFieldImeTarget(
            getValue = { playgroundText },
            setValue = { updated -> playgroundText = updated },
            onOpenAppSettings = { onNavigateToSettings() }
        )
    ).apply {
        onRecordWordUsage = { word ->
            viewModelScope.launch {
                settingsRepository.recordWordUsage(word)
            }
        }
    }

    init {
        viewModelScope.launch {
            settingsRepository.personalityFlow.collect { stats ->
                playgroundController.updatePersonality(stats, settings.value, dictionary.value)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updatePlaygroundText(value: TextFieldValue) {
        val textChanged = value.text != playgroundText.text
        playgroundText = value
        if (textChanged) {
            playgroundController.invalidateLocalWordCache()
            playgroundController.refreshState(settings.value, dictionary.value)
        } else {
            playgroundController.onSelectionUpdatedFromIme(
                hasSelection = !value.selection.collapsed,
                externalJump = true
            )
        }
    }

    fun clearPlaygroundText() {
        playgroundText = TextFieldValue("")
        playgroundController.invalidateLocalWordCache()
        playgroundController.refreshState(settings.value, dictionary.value)
    }

    fun refreshImeStatus(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val pkg = context.packageName
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
        val defaultIme = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull().orEmpty()
        val isDefault = defaultIme.startsWith("$pkg/")
        _imeStatus.value = when {
            isDefault -> ImeActivationStatus.ACTIVE_DEFAULT
            enabled -> ImeActivationStatus.ENABLED_NOT_DEFAULT
            else -> ImeActivationStatus.DISABLED
        }
    }

    fun updateSettings(
        markPendingSync: Boolean = true,
        transform: (KeyboardSettings) -> KeyboardSettings
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings(markPendingSync = markPendingSync, transform = transform)
        }
    }

    fun toggleSerifMiring() {
        updateSettings { it.copy(serifMiringEnabled = !it.serifMiringEnabled) }
    }

    fun toggleFloatingMode() {
        updateSettings { it.copy(floatingModeEnabled = !it.floatingModeEnabled) }
    }

    fun saveFloatingPosition(posX: Float, posY: Float) {
        val clampedX = posX.coerceIn(0f, 1f)
        val clampedY = posY.coerceIn(0f, 1f)
        updateSettings(markPendingSync = false) {
            it.copy(
                floatingPosX = clampedX,
                floatingPosY = clampedY
            )
        }
    }

    fun upsertDictionaryEntry(
        word: String,
        shortcut: String,
        language: String,
        existingId: String? = null,
        folder: String = "",
        secretCipher: String? = null,
        email: String = "",
        website: String = ""
    ) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.upsertDictionaryEntry(
                    word = word,
                    shortcut = shortcut,
                    language = language,
                    existingId = existingId,
                    folder = folder,
                    secretCipher = secretCipher,
                    email = email,
                    website = website
                )
                pushLocalChangesToDevice()
            }.onFailure {
                _deviceSyncUiState.value = DeviceSyncUiState.Error(it.message ?: "Entri tidak disimpan")
            }
        }
    }

    fun regenerateDictionaryShortcuts() {
        viewModelScope.launch {
            runCatching {
                val changed = settingsRepository.regenerateAutoShortcuts()
                if (changed > 0) pushLocalChangesToDevice()
            }.onFailure {
                _deviceSyncUiState.value = DeviceSyncUiState.Error(it.message ?: "Gagal generate ulang pintasan")
            }
        }
    }

    fun restoreDictionaryEntries(entryIds: Set<String>) {
        viewModelScope.launch {
            settingsRepository.restoreFromTrash(entryIds)
            pushLocalChangesToDevice()
        }
    }

    fun permanentlyDeleteDictionaryEntries(entryIds: Set<String>) {
        viewModelScope.launch {
            runCatching {
                val removed = settingsRepository.permanentlyDeleteDuplicates(entryIds)
                if (removed > 0) pushLocalChangesToDevice()
            }.onFailure {
                _deviceSyncUiState.value = DeviceSyncUiState.Error(it.message ?: "Gagal menghapus permanen")
            }
        }
    }

    fun createDictionaryFolder(folderPath: String) {
        viewModelScope.launch {
            settingsRepository.createFolder(folderPath)
            pushLocalChangesToDevice()
        }
    }

    fun renameDictionaryFolder(oldPath: String, newName: String) {
        viewModelScope.launch {
            settingsRepository.renameFolder(oldPath, newName)
            pushLocalChangesToDevice()
        }
    }

    fun deleteDictionaryItems(entryIds: Set<String>, folderPaths: Set<String>) {
        viewModelScope.launch {
            settingsRepository.deleteEntriesAndFolders(entryIds, folderPaths)
            pushLocalChangesToDevice()
        }
    }

    fun addClipboardText(text: String) {
        viewModelScope.launch {
            settingsRepository.addClipboardText(text)
                .onFailure { playgroundController.showClipboardStatus("Gagal menyimpan klip") }
        }
    }

    suspend fun loadClipboardFullText(clipboardId: String): String? {
        return settingsRepository.getClipboardFullText(clipboardId)
            .onFailure { playgroundController.showClipboardStatus("Gagal memuat klip") }
            .getOrNull()
    }

    suspend fun loadLatestClipboardFullText(): String? {
        return settingsRepository.getLatestClipboardFullText()
            .onFailure { playgroundController.showClipboardStatus("Gagal memuat papan klip") }
            .getOrNull()
    }

    fun dismissRecentClipboard(clipboardId: String) {
        viewModelScope.launch {
            settingsRepository.dismissRecentClipboard(clipboardId)
        }
    }

    fun syncSystemClipboardFromDevice(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        ?: return@withContext ""
                    val clip = cm.primaryClip ?: return@withContext ""
                    if (clip.itemCount > 0) {
                        clip.getItemAt(0).coerceToText(appContext)?.toString().orEmpty()
                    } else ""
                }
                if (text.isNotBlank()) {
                    settingsRepository.syncSystemClipboardIfChanged(text)
                }
            } catch (_: Throwable) {
                // Keep app running smoothly if system clipboard cannot be read
            }
        }
    }

    fun clearAllClipboard() {
        viewModelScope.launch {
            settingsRepository.clearAllClipboard()
                .onFailure { playgroundController.showClipboardStatus("Gagal membersihkan papan klip") }
        }
    }

    suspend fun buildFullBackupJson(): String {
        val currentSettings = settingsRepository.getCurrentSettings()
        val currentDict = settingsRepository.getCurrentDictionary()
        val currentFolders = settingsRepository.getCurrentFolders()
        return settingsRepository.encodeFullBackupJson(currentSettings, currentDict, folders = currentFolders)
    }

    fun importLocalBackupJson(rawJson: String, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = settingsRepository.importLocalBackupJson(rawJson)
            if (result.isSuccess) {
                pushLocalChangesToDevice()
            }
            onComplete(result)
        }
    }

    private suspend fun pushLocalChangesToDevice() {
        val manager = deviceBackupManager ?: return
        lastAutoSyncTriggerMillis = System.currentTimeMillis()
        _deviceSyncUiState.value = DeviceSyncUiState.Syncing
        val result = try {
            manager.pushLocalToDevice()
        } catch (t: Throwable) {
            DeviceSyncResult.Error(t.localizedMessage ?: "Gagal memperbarui cadangan kamus ke perangkat.")
        }
        _deviceSyncUiState.value = when (result) {
            is DeviceSyncResult.Synced -> DeviceSyncUiState.Idle
            is DeviceSyncResult.NeedsFolderAccess -> DeviceSyncUiState.NeedsFolderAccess(result.message)
            is DeviceSyncResult.Error -> DeviceSyncUiState.Error(result.message)
        }
    }

    /** Gabungkan: tambah id yang belum ada di kedua arah. Tidak menimpa, tidak menghapus. */
    fun gabungkanCadangan() = runDeviceSync { it.gabungkan() }

    private fun runDeviceSync(block: suspend (DeviceDictionaryBackupManager) -> DeviceSyncResult) {
        val manager = deviceBackupManager ?: return
        if (_deviceSyncUiState.value is DeviceSyncUiState.Syncing) return
        lastAutoSyncTriggerMillis = System.currentTimeMillis()
        _deviceSyncUiState.value = DeviceSyncUiState.Syncing
        viewModelScope.launch {
            val result = try {
                block(manager)
            } catch (t: Throwable) {
                DeviceSyncResult.Error(t.localizedMessage ?: "Gagal menyinkronkan kamus pribadi ke perangkat.")
            }
            _deviceSyncUiState.value = when (result) {
                is DeviceSyncResult.Synced -> DeviceSyncUiState.Idle
                is DeviceSyncResult.NeedsFolderAccess -> DeviceSyncUiState.NeedsFolderAccess(result.message)
                is DeviceSyncResult.Error -> DeviceSyncUiState.Error(result.message)
            }
        }
    }

    fun syncDictionaryWithDevice(force: Boolean = false) {
        val manager = deviceBackupManager ?: return
        val now = System.currentTimeMillis()
        if (!force) {
            if (_deviceSyncUiState.value is DeviceSyncUiState.Syncing) return
            if (lastAutoSyncTriggerMillis > 0L &&
                !syncLocalState.value.pendingChanges &&
                (now - lastAutoSyncTriggerMillis) < 3_000L
            ) {
                return
            }
        }
        lastAutoSyncTriggerMillis = now
        _deviceSyncUiState.value = DeviceSyncUiState.Syncing
        viewModelScope.launch {
            val result = try {
                manager.syncNow()
            } catch (t: Throwable) {
                DeviceSyncResult.Error(t.localizedMessage ?: "Gagal menyinkronkan kamus pribadi ke perangkat.")
            }
            _deviceSyncUiState.value = when (result) {
                is DeviceSyncResult.Synced -> DeviceSyncUiState.Idle
                is DeviceSyncResult.NeedsFolderAccess -> DeviceSyncUiState.NeedsFolderAccess(result.message)
                is DeviceSyncResult.Error -> DeviceSyncUiState.Error(result.message)
            }
        }
    }

    fun onDeviceBackupFolderGranted(treeUri: String) {
        if (treeUri.isBlank()) return
        if (!DeviceDictionaryBackupManager.isValidDocumentsTreeUri(treeUri)) {
            _deviceSyncUiState.value = DeviceSyncUiState.Error(
                "Pilih folder di dalam /storage/emulated/0/Documents/"
            )
            return
        }
        viewModelScope.launch {
            settingsRepository.saveDeviceBackupTreeUri(treeUri)
            syncDictionaryWithDevice(force = true)
        }
    }

    fun resetDeviceBackupFolderToDefault() {
        viewModelScope.launch {
            settingsRepository.saveDeviceBackupTreeUri("")
            syncDictionaryWithDevice(force = true)
        }
    }
}
