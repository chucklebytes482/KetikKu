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
import com.example.data.SettingsRepository
import com.example.ime.KeyboardController
import com.example.ime.TextFieldImeTarget
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.SyncLocalState
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

class MainViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<KeyboardSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), KeyboardSettings())

    val dictionary: StateFlow<List<PersonalDictionaryEntry>> = settingsRepository.dictionaryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val clipboard: StateFlow<List<ClipboardItem>> = settingsRepository.clipboardFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dismissedClipboardId: StateFlow<String?> = settingsRepository.dismissedClipboardIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val syncLocalState: StateFlow<SyncLocalState> = settingsRepository.syncLocalStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncLocalState())

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
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updatePlaygroundText(value: TextFieldValue) {
        playgroundText = value
        playgroundController.invalidateLocalWordCache()
        playgroundController.refreshState(settings.value, dictionary.value)
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

    fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(markPendingSync = false, transform = transform)
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
        updateSettings {
            it.copy(
                floatingPosX = clampedX,
                floatingPosY = clampedY
            )
        }
    }

    fun upsertDictionaryEntry(word: String, shortcut: String, language: String, existingId: String? = null) {
        viewModelScope.launch {
            settingsRepository.upsertDictionaryEntry(word, shortcut, language, existingId)
        }
    }

    fun deleteDictionaryEntry(entryId: String) {
        viewModelScope.launch {
            settingsRepository.deleteDictionaryEntry(entryId)
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
        return settingsRepository.encodeFullBackupJson(currentSettings, currentDict)
    }

    fun importLocalBackupJson(rawJson: String, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = settingsRepository.importLocalBackupJson(rawJson)
            onComplete(result)
        }
    }
}
