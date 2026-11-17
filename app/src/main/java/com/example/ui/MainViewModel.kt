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

    val syncLocalState: StateFlow<SyncLocalState> = settingsRepository.syncLocalStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncLocalState())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _imeStatus = MutableStateFlow(ImeActivationStatus.DISABLED)
    val imeStatus: StateFlow<ImeActivationStatus> = _imeStatus.asStateFlow()

    var playgroundText by mutableStateOf(TextFieldValue(""))
        private set

    val playgroundController = KeyboardController(
        target = TextFieldImeTarget(
            getValue = { playgroundText },
            setValue = { updated -> playgroundText = updated }
        )
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updatePlaygroundText(value: TextFieldValue) {
        playgroundText = value
        playgroundController.refreshState(settings.value, dictionary.value)
    }

    fun clearPlaygroundText() {
        playgroundText = TextFieldValue("")
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
            settingsRepository.updateSettings(markPendingSync = true, transform = transform)
        }
    }

    fun toggleArialItalic() {
        updateSettings { it.copy(arialItalicEnabled = !it.arialItalicEnabled) }
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
        }
    }

    fun clearAllClipboard() {
        viewModelScope.launch {
            settingsRepository.clearAllClipboard()
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
