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
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.KeyboardThemePreset
import com.example.model.OneHandedMode
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
            setValue = { updated -> playgroundText = updated },
            onSwitchLanguage = { toggleLanguage() }
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

    fun toggleLanguage() {
        updateSettings { s ->
            val next = if (s.languageEnum == KeyboardLanguage.INDONESIAN) {
                KeyboardLanguage.ENGLISH
            } else {
                KeyboardLanguage.INDONESIAN
            }
            s.copy(language = next.code)
        }
    }

    fun cycleTheme() {
        updateSettings { s ->
            val presets = KeyboardThemePreset.entries
            val nextIdx = (presets.indexOf(s.themeEnum) + 1) % presets.size
            s.copy(themePreset = presets[nextIdx].name)
        }
    }

    fun setOneHandedMode(mode: OneHandedMode) {
        updateSettings { it.copy(oneHandedMode = mode.name) }
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

    fun toggleClipboardPin(id: String) {
        viewModelScope.launch {
            settingsRepository.toggleClipboardPin(id)
        }
    }

    fun removeClipboardItem(id: String) {
        viewModelScope.launch {
            settingsRepository.removeClipboardItem(id)
        }
    }

    fun clearUnpinnedClipboard() {
        viewModelScope.launch {
            settingsRepository.clearUnpinnedClipboard()
        }
    }
}
