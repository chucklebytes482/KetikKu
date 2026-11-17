package com.example.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.SettingsRepository
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.KeyboardThemePreset
import com.example.model.OneHandedMode
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KetikKuImeService : InputMethodService() {

    private val lifecycleOwner = ImeLifecycleOwner()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var controller: KeyboardController

    private var latestSettings = KeyboardSettings()
    private var latestDictionary = emptyList<PersonalDictionaryEntry>()
    private var clipboardManager: ClipboardManager? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!latestSettings.clipboardHistoryEnabled) return@OnPrimaryClipChangedListener
        val clip = clipboardManager?.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) {
                serviceScope.launch {
                    settingsRepository.addClipboardText(text)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)

        controller = KeyboardController(
            target = InputConnectionImeTarget(
                icProvider = { currentInputConnection },
                onSwitchIme = {
                    if (shouldOfferSwitchingToNextInputMethod()) {
                        switchToNextInputMethod(false)
                    }
                },
                onOpenSettings = {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            )
        )

        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                latestSettings = settings
                controller.refreshState(latestSettings, latestDictionary)
            }
        }
        serviceScope.launch {
            settingsRepository.dictionaryFlow.collectLatest { entries ->
                latestDictionary = entries
                controller.refreshState(latestSettings, latestDictionary)
            }
        }
    }

    override fun onCreateInputView(): View {
        val decorView = window?.window?.decorView
        if (decorView != null) {
            decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)
            decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val settings by settingsRepository.settingsFlow.collectAsState(initial = latestSettings)
                val dictionary by settingsRepository.dictionaryFlow.collectAsState(initial = latestDictionary)
                val clipboard by settingsRepository.clipboardFlow.collectAsState(initial = emptyList())

                KetikKuKeyboardView(
                    controller = controller,
                    settings = settings,
                    personalEntries = dictionary,
                    clipboardItems = clipboard,
                    onPasteClipboard = { text ->
                        controller.pasteText(text, settings, dictionary)
                    },
                    onToggleClipboardPin = { id ->
                        serviceScope.launch { settingsRepository.toggleClipboardPin(id) }
                    },
                    onDeleteClipboardItem = { id ->
                        serviceScope.launch { settingsRepository.removeClipboardItem(id) }
                    },
                    onClearUnpinnedClipboard = {
                        serviceScope.launch { settingsRepository.clearUnpinnedClipboard() }
                    },
                    onCopySelectionToClipboard = { selected ->
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("KetikKu", selected))
                        serviceScope.launch { settingsRepository.addClipboardText(selected) }
                    },
                    onToggleOneHanded = { mode: OneHandedMode ->
                        serviceScope.launch {
                            settingsRepository.updateSettings { it.copy(oneHandedMode = mode.name) }
                        }
                    },
                    onCycleTheme = {
                        serviceScope.launch {
                            val presets = KeyboardThemePreset.entries
                            val nextIdx = (presets.indexOf(settings.themeEnum) + 1) % presets.size
                            settingsRepository.updateSettings { it.copy(themePreset = presets[nextIdx].name) }
                        }
                    },
                    onToggleLanguage = {
                        serviceScope.launch {
                            val nextLang = if (settings.languageEnum == KeyboardLanguage.INDONESIAN) {
                                KeyboardLanguage.ENGLISH
                            } else {
                                KeyboardLanguage.INDONESIAN
                            }
                            settingsRepository.updateSettings { it.copy(language = nextLang.code) }
                        }
                    }
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onWindowShown()
        val inputType = info?.inputType ?: 0
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isNumeric = typeClass == InputType.TYPE_CLASS_NUMBER || typeClass == InputType.TYPE_CLASS_PHONE
        val isPassword = (typeClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )) || (typeClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        val action = when ((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> EnterActionType.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterActionType.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterActionType.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterActionType.DONE
            EditorInfo.IME_ACTION_GO -> EnterActionType.GO
            else -> EnterActionType.ENTER
        }

        controller.configureForEditor(
            isNumeric = isNumeric,
            isPassword = isPassword,
            actionType = action,
            settings = latestSettings,
            personalEntries = latestDictionary
        )
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleOwner.onWindowHidden()
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private class InputConnectionImeTarget(
        private val icProvider: () -> InputConnection?,
        private val onSwitchIme: () -> Unit,
        private val onOpenSettings: () -> Unit
    ) : ImeTarget {

        override fun getTextBeforeCursor(n: Int): String =
            icProvider()?.getTextBeforeCursor(n, 0)?.toString().orEmpty()

        override fun getSelectedText(): String =
            icProvider()?.getSelectedText(0)?.toString().orEmpty()

        override fun commitText(text: String) {
            icProvider()?.commitText(text, 1)
        }

        override fun deleteBeforeCursor(count: Int) {
            val ic = icProvider() ?: return
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(count, 0)
            }
        }

        override fun replaceWordBeforeCursor(oldWordLength: Int, newText: String) {
            val ic = icProvider() ?: return
            ic.beginBatchEdit()
            ic.deleteSurroundingText(oldWordLength, 0)
            ic.commitText(newText, 1)
            ic.endBatchEdit()
        }

        override fun moveCursor(delta: Int, extendSelection: Boolean) {
            val ic = icProvider() ?: return
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            if (extracted != null) {
                val curEnd = extracted.selectionEnd
                val next = (curEnd + delta).coerceIn(0, extracted.text?.length ?: curEnd)
                if (extendSelection) {
                    ic.setSelection(extracted.selectionStart, next)
                } else {
                    ic.setSelection(next, next)
                }
            } else {
                val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                repeat(kotlin.math.abs(delta)) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }
        }

        override fun moveCursorVertical(deltaLines: Int) {
            val ic = icProvider() ?: return
            val keyCode = if (deltaLines < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }

        override fun moveCursorToEdge(toStart: Boolean) {
            val ic = icProvider() ?: return
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            val target = if (toStart) 0 else (extracted.text?.length ?: 0)
            ic.setSelection(target, target)
        }

        override fun selectAll() {
            icProvider()?.performContextMenuAction(android.R.id.selectAll)
        }

        override fun performEnterAction(actionType: EnterActionType) {
            val ic = icProvider() ?: return
            val editorAction = when (actionType) {
                EnterActionType.SEARCH -> EditorInfo.IME_ACTION_SEARCH
                EnterActionType.SEND -> EditorInfo.IME_ACTION_SEND
                EnterActionType.NEXT -> EditorInfo.IME_ACTION_NEXT
                EnterActionType.DONE -> EditorInfo.IME_ACTION_DONE
                EnterActionType.GO -> EditorInfo.IME_ACTION_GO
                EnterActionType.ENTER -> EditorInfo.IME_ACTION_UNSPECIFIED
            }
            if (editorAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(editorAction)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }

        override fun switchLanguageOrIme() {
            onSwitchIme()
        }

        override fun openAppSettings() {
            onOpenSettings()
        }
    }
}
