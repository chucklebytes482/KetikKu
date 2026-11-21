package com.example.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.EXTRA_START_ROUTE
import com.example.MainActivity
import com.example.ROUTE_SETTINGS
import com.example.data.SettingsRepository
import com.example.data.TextChunker
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KetikKuImeService : InputMethodService() {

    private val lifecycleOwner = ImeLifecycleOwner()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var controller: KeyboardController

    private var latestSettings = KeyboardSettings()
    private var latestDictionary = emptyList<PersonalDictionaryEntry>()
    private var clipboardManager: ClipboardManager? = null
    private var floatingBoundsRect = Rect()

    private fun readSystemClipboardTextSafe(): String? {
        return try {
            val cm = clipboardManager ?: return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0).coerceToText(this)?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    private fun showBriefClipboardError(msg: String) {
        controller.showClipboardStatus(msg)
        serviceScope.launch {
            delay(2500L)
            if (controller.clipboardStatusMessage == msg) {
                controller.showClipboardStatus(null)
            }
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!latestSettings.clipboardHistoryEnabled) return@OnPrimaryClipChangedListener
        serviceScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readSystemClipboardTextSafe() }
                if (!text.isNullOrBlank()) {
                    settingsRepository.addClipboardText(text)
                        .onFailure { showBriefClipboardError("Gagal menyimpan klip") }
                }
            } catch (_: Throwable) {
                showBriefClipboardError("Gagal membaca papan klip")
            }
        }
    }

    private val target = InputConnectionImeTarget(
        icProvider = { currentInputConnection },
        onOpenSettings = {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_START_ROUTE, ROUTE_SETTINGS)
            }
            startActivity(intent)
        }
    )

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)

        controller = KeyboardController(target = target).apply {
            onRecordWordUsage = { word ->
                serviceScope.launch {
                    settingsRepository.recordWordUsage(word)
                }
            }
        }

        serviceScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                val floatingChanged = latestSettings.floatingModeEnabled != settings.floatingModeEnabled
                latestSettings = settings
                if (floatingChanged) {
                    window?.window?.decorView?.requestLayout()
                }
            }
        }
        serviceScope.launch {
            settingsRepository.dictionaryFlow.collectLatest { entries ->
                withContext(Dispatchers.Default) {
                    com.example.data.DictionaryEngine.prewarmIndex(entries)
                }
                latestDictionary = entries
                controller.refreshState(latestSettings, latestDictionary)
            }
        }
        serviceScope.launch {
            settingsRepository.personalityFlow.collectLatest { stats ->
                controller.updatePersonality(stats, latestSettings, latestDictionary)
            }
        }
    }

    override fun onCreateInputView(): View {
        applyImeWindowSystemBars()

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

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    ViewCompat.requestApplyInsets(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })

            setContent {
                val settings by settingsRepository.settingsFlow.collectAsState(initial = latestSettings)
                val dictionary by settingsRepository.dictionaryFlow.collectAsState(initial = latestDictionary)
                val clipboard by settingsRepository.clipboardFlow.collectAsState(initial = emptyList())
                val dismissedClipboardId by settingsRepository.dismissedClipboardIdFlow.collectAsState(initial = null)
                val dismissedClipboardSignature by settingsRepository.dismissedClipboardSignatureFlow.collectAsState(initial = null)

                KetikKuKeyboardView(
                    controller = controller,
                    settings = settings,
                    personalEntries = dictionary,
                    clipboardItems = clipboard,
                    dismissedClipboardId = dismissedClipboardId,
                    dismissedClipboardSignature = dismissedClipboardSignature,
                    applySystemBarInsets = true,
                    isImeFloatingOverlay = true,
                    onPasteClipboard = { text ->
                        serviceScope.launch {
                            controller.pasteTextChunked(text, settings, dictionary)
                        }
                    },
                    onPasteClipboardItem = { item: ClipboardItem ->
                        serviceScope.launch {
                            try {
                                val fullResult = settingsRepository.getClipboardFullText(item.id)
                                val fullText = fullResult.getOrElse {
                                    showBriefClipboardError("Gagal memuat klip")
                                    return@launch
                                }
                                val textToPaste = fullText ?: item.preview
                                controller.pasteTextChunked(textToPaste, settings, dictionary)
                            } catch (_: Throwable) {
                                showBriefClipboardError("Gagal menempel klip")
                            }
                        }
                    },
                    onRequestCtrlPaste = {
                        serviceScope.launch {
                            try {
                                val sysText = withContext(Dispatchers.IO) {
                                    readSystemClipboardTextSafe().orEmpty()
                                }
                                val fullText = sysText.ifEmpty {
                                    settingsRepository.getLatestClipboardFullText().getOrNull().orEmpty()
                                }
                                if (fullText.length >= TextChunker.DIRECT_PASTE_MAX_CHARS) {
                                    controller.pasteTextChunked(fullText, settings, dictionary)
                                } else {
                                    controller.onCtrlPaste(fullText, settings, dictionary)
                                }
                            } catch (_: Throwable) {
                                showBriefClipboardError("Gagal menempel dari papan klip")
                            }
                        }
                    },
                    onDismissRecentClipboard = { id ->
                        serviceScope.launch { settingsRepository.dismissRecentClipboard(id) }
                    },
                    onCopySelectionToClipboard = { selected ->
                        serviceScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("KetikKu", selected))
                                    }
                                }
                                settingsRepository.addClipboardText(selected)
                                    .onFailure { showBriefClipboardError("Gagal menyimpan salinan") }
                            } catch (_: Throwable) {
                                showBriefClipboardError("Gagal menyalin teks")
                            }
                        }
                    },
                    onClearAllClipboard = {
                        serviceScope.launch {
                            settingsRepository.clearAllClipboard()
                                .onFailure { showBriefClipboardError("Gagal membersihkan papan klip") }
                        }
                    },
                    onToggleFloatingMode = {
                        serviceScope.launch {
                            settingsRepository.updateSettings { it.copy(floatingModeEnabled = !it.floatingModeEnabled) }
                        }
                    },
                    onSaveFloatingPosition = { posX, posY ->
                        serviceScope.launch {
                            settingsRepository.updateSettings(markPendingSync = false) {
                                it.copy(
                                    floatingPosX = posX.coerceIn(0f, 1f),
                                    floatingPosY = posY.coerceIn(0f, 1f)
                                )
                            }
                        }
                        window?.window?.decorView?.requestLayout()
                    },
                    onFloatingBoundsChanged = { newRect ->
                        val prev = floatingBoundsRect
                        val significantChange = kotlin.math.abs(prev.left - newRect.left) > 2 ||
                            kotlin.math.abs(prev.top - newRect.top) > 2 ||
                            kotlin.math.abs(prev.right - newRect.right) > 2 ||
                            kotlin.math.abs(prev.bottom - newRect.bottom) > 2
                        floatingBoundsRect = Rect(newRect)
                        if (significantChange && latestSettings.floatingModeEnabled) {
                            window?.window?.decorView?.requestLayout()
                        }
                    },
                    onShowInputMethodPicker = {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                    }
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyImeWindowSystemBars()
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

        val imeOptions = info?.imeOptions ?: 0
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val action = if (noEnterAction) {
            EnterActionType.ENTER
        } else {
            when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEARCH -> EnterActionType.SEARCH
                EditorInfo.IME_ACTION_SEND -> EnterActionType.SEND
                EditorInfo.IME_ACTION_NEXT -> EnterActionType.NEXT
                EditorInfo.IME_ACTION_DONE -> EnterActionType.DONE
                EditorInfo.IME_ACTION_GO -> EnterActionType.GO
                else -> EnterActionType.ENTER
            }
        }

        val isRawOrWebCodeEditor = inputType == InputType.TYPE_NULL ||
            (typeClass == InputType.TYPE_CLASS_TEXT &&
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)

        target.onEditorStarted(isRawOrWebCodeEditor = isRawOrWebCodeEditor)
        target.onSelectionUpdated(
            newSelStart = info?.initialSelStart ?: -1,
            newSelEnd = info?.initialSelEnd ?: -1
        )
        controller.configureForEditor(
            isNumeric = isNumeric,
            isPassword = isPassword,
            actionType = action,
            settings = latestSettings,
            personalEntries = latestDictionary
        )
        if (!isPassword && latestSettings.clipboardHistoryEnabled) {
            serviceScope.launch {
                try {
                    val clipText = withContext(Dispatchers.IO) { readSystemClipboardTextSafe() }
                    if (!clipText.isNullOrBlank()) {
                        settingsRepository.syncSystemClipboardIfChanged(clipText)
                    }
                } catch (_: Throwable) {
                    showBriefClipboardError("Gagal menyinkronkan papan klip")
                }
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val externalJump = target.onSelectionUpdated(newSelStart, newSelEnd)
        if (externalJump) {
            controller.invalidateLocalWordCache()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImeWindowSystemBars()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        if (latestSettings.floatingModeEnabled) return false
        return super.onEvaluateFullscreenMode()
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets == null || !latestSettings.floatingModeEnabled) return
        val decorView = window?.window?.decorView ?: return
        val decorHeight = decorView.height
        val decorWidth = decorView.width
        if (decorHeight <= 0 || decorWidth <= 0) return

        outInsets.contentTopInsets = decorHeight
        outInsets.visibleTopInsets = decorHeight
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        if (!floatingBoundsRect.isEmpty) {
            outInsets.touchableRegion.set(floatingBoundsRect)
        } else {
            val metrics = resources.displayMetrics
            val floatingW = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.70f).toInt()
            val left = ((decorWidth - floatingW) / 2).coerceAtLeast(0)
            val fallbackTop = (decorHeight / 2).coerceAtLeast(0)
            outInsets.touchableRegion.set(left, fallbackTop, (left + floatingW).coerceAtMost(decorWidth), decorHeight)
        }
    }

    private fun applyImeWindowSystemBars() {
        val win = window?.window ?: return
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(win, false)
        @Suppress("DEPRECATION")
        win.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
        }
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightNavigationBars = !isDark
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

    internal class InputConnectionImeTarget(
        private val icProvider: () -> InputConnection?,
        private val onOpenSettings: () -> Unit
    ) : ImeTarget {

        private var hasKnownSelection: Boolean = false
        private var expectedCursorEnd: Int = -1
        private var pendingSelfEdits: Int = 0
        private var lastSelfEditUptimeMs: Long = 0L
        private var isRawOrWebCodeEditor: Boolean = false

        fun onEditorStarted(isRawOrWebCodeEditor: Boolean) {
            this.isRawOrWebCodeEditor = isRawOrWebCodeEditor
        }

        private fun markSelfEdit(newExpectedEnd: Int = -1, clearSelection: Boolean = true) {
            if (clearSelection) {
                hasKnownSelection = false
            }
            if (newExpectedEnd >= 0) {
                expectedCursorEnd = newExpectedEnd
            } else {
                expectedCursorEnd = -1
            }
            pendingSelfEdits = (pendingSelfEdits + 1).coerceAtMost(32)
            lastSelfEditUptimeMs = SystemClock.uptimeMillis()
        }

        fun onSelectionUpdated(newSelStart: Int, newSelEnd: Int): Boolean {
            hasKnownSelection = newSelStart >= 0 && newSelEnd >= 0 && newSelStart != newSelEnd
            if (newSelStart < 0 || newSelEnd < 0) {
                expectedCursorEnd = -1
                pendingSelfEdits = 0
                return false
            }
            val matchedExpected = expectedCursorEnd >= 0 &&
                newSelStart == newSelEnd &&
                newSelEnd == expectedCursorEnd
            val likelySelfEdit = if (pendingSelfEdits > 0 && newSelStart == newSelEnd) {
                pendingSelfEdits--
                true
            } else {
                val recentSelf = SystemClock.uptimeMillis() - lastSelfEditUptimeMs < 300L
                recentSelf && newSelStart == newSelEnd
            }
            if (!likelySelfEdit) {
                pendingSelfEdits = 0
                expectedCursorEnd = newSelEnd
            } else if (expectedCursorEnd < 0) {
                expectedCursorEnd = newSelEnd
            }
            return !(matchedExpected || likelySelfEdit)
        }

        override fun getTextBeforeCursor(n: Int): String =
            icProvider()?.getTextBeforeCursor(n, 0)?.toString().orEmpty()

        override fun getSelectedText(): String =
            icProvider()?.getSelectedText(0)?.toString().orEmpty()

        override fun commitText(text: String) {
            val ic = icProvider() ?: return
            val nextEnd = if (!hasKnownSelection && expectedCursorEnd >= 0) {
                expectedCursorEnd + text.length
            } else {
                -1
            }
            markSelfEdit(newExpectedEnd = nextEnd)
            ic.commitText(text, 1)
        }

        private fun dispatchHardwareDel(ic: InputConnection) {
            val now = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, 0))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, 0))
        }

        override fun deleteBeforeCursor(count: Int) {
            if (count <= 0) return
            val ic = icProvider() ?: return
            val liveSelection = runCatching { ic.getSelectedText(0)?.toString() }.getOrNull()
            if (!liveSelection.isNullOrEmpty()) {
                markSelfEdit(newExpectedEnd = -1)
                val committed = runCatching { ic.commitText("", 1) }.getOrDefault(false)
                if (!committed) {
                    dispatchHardwareDel(ic)
                }
                return
            }
            if (hasKnownSelection) {
                hasKnownSelection = false
            }

            val nextEnd = if (expectedCursorEnd >= count) expectedCursorEnd - count else -1
            markSelfEdit(newExpectedEnd = nextEnd)
            val textBefore = runCatching { ic.getTextBeforeCursor(count.coerceAtMost(64), 0)?.toString() }.getOrNull()
            if (isRawOrWebCodeEditor || textBefore.isNullOrEmpty()) {
                repeat(count) { dispatchHardwareDel(ic) }
                return
            }
            val deleted = runCatching { ic.deleteSurroundingText(count, 0) }.getOrDefault(false)
            if (!deleted) {
                repeat(count) { dispatchHardwareDel(ic) }
            }
        }

        override fun deleteSingleCodePointBeforeCursor() {
            val ic = icProvider() ?: return
            val liveSelection = runCatching { ic.getSelectedText(0)?.toString() }.getOrNull()
            if (!liveSelection.isNullOrEmpty()) {
                markSelfEdit(newExpectedEnd = -1)
                val committed = runCatching { ic.commitText("", 1) }.getOrDefault(false)
                if (!committed) {
                    dispatchHardwareDel(ic)
                }
                return
            }
            if (hasKnownSelection) {
                hasKnownSelection = false
            }

            markSelfEdit(newExpectedEnd = -1)
            val textBefore = runCatching { ic.getTextBeforeCursor(2, 0)?.toString() }.getOrNull()
            if (isRawOrWebCodeEditor || textBefore.isNullOrEmpty()) {
                dispatchHardwareDel(ic)
                return
            }
            val deletedCp = runCatching { ic.deleteSurroundingTextInCodePoints(1, 0) }.getOrDefault(false)
            if (deletedCp) return
            val deletedChars = runCatching { ic.deleteSurroundingText(1, 0) }.getOrDefault(false)
            if (!deletedChars) {
                dispatchHardwareDel(ic)
            }
        }

        override fun replaceWordBeforeCursor(oldWordLength: Int, newText: String) {
            val ic = icProvider() ?: return
            val nextEnd = if (!hasKnownSelection && expectedCursorEnd >= oldWordLength) {
                expectedCursorEnd - oldWordLength + newText.length
            } else {
                -1
            }
            markSelfEdit(newExpectedEnd = nextEnd)
            if (oldWordLength <= 0) {
                ic.commitText(newText, 1)
                return
            }
            if (isRawOrWebCodeEditor) {
                repeat(oldWordLength) { dispatchHardwareDel(ic) }
                ic.commitText(newText, 1)
                return
            }
            ic.beginBatchEdit()
            val deleted = runCatching { ic.deleteSurroundingText(oldWordLength, 0) }.getOrDefault(false)
            if (!deleted) {
                ic.endBatchEdit()
                repeat(oldWordLength) { dispatchHardwareDel(ic) }
                ic.commitText(newText, 1)
                return
            }
            ic.commitText(newText, 1)
            ic.endBatchEdit()
        }

        override fun moveCursor(delta: Int, extendSelection: Boolean) {
            val ic = icProvider() ?: return
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            if (extracted != null && extracted.text != null) {
                val fullText = extracted.text.toString()
                val curEnd = extracted.selectionEnd.coerceIn(0, fullText.length)
                val next = shiftIndexByCodePoints(fullText, curEnd, delta)
                if (extendSelection) {
                    ic.setSelection(extracted.selectionStart.coerceIn(0, fullText.length), next)
                } else {
                    ic.setSelection(next, next)
                }
            } else {
                val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                val meta = if (extendSelection) KeyEvent.META_SHIFT_ON else 0
                repeat(kotlin.math.abs(delta)) {
                    val now = SystemClock.uptimeMillis()
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
                }
            }
        }

        override fun moveCursorVertical(deltaLines: Int) {
            val ic = icProvider() ?: return
            val keyCode = if (deltaLines < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
            val now = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0))
        }

        override fun moveCursorToEdge(toStart: Boolean) {
            val ic = icProvider() ?: return
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            val target = if (toStart) 0 else (extracted.text?.length ?: 0)
            ic.setSelection(target, target)
        }

        private fun sendCtrlKey(ic: InputConnection, keyCode: Int) {
            val now = SystemClock.uptimeMillis()
            val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }

        override fun sendCtrlKey(letter: Char): Boolean {
            val ic = icProvider() ?: return false
            val lower = letter.lowercaseChar()
            if (lower !in 'a'..'z') return false
            val keyCode = KeyEvent.KEYCODE_A + (lower - 'a')
            return runCatching {
                sendCtrlKey(ic, keyCode)
                true
            }.getOrDefault(false)
        }

        override fun selectAll() {
            val ic = icProvider() ?: return
            runCatching {
                if (!ic.performContextMenuAction(android.R.id.selectAll)) {
                    sendCtrlKey(ic, KeyEvent.KEYCODE_A)
                }
            }
        }

        override fun undo(): Boolean {
            val ic = icProvider() ?: return false
            markSelfEdit()
            return runCatching {
                if (!ic.performContextMenuAction(android.R.id.undo)) {
                    sendCtrlKey(ic, KeyEvent.KEYCODE_Z)
                }
                true
            }.getOrDefault(false)
        }

        override fun redo(): Boolean {
            val ic = icProvider() ?: return false
            markSelfEdit()
            return runCatching {
                if (!ic.performContextMenuAction(android.R.id.redo)) {
                    sendCtrlKey(ic, KeyEvent.KEYCODE_Y)
                }
                true
            }.getOrDefault(false)
        }

        override fun copy(): String {
            val ic = icProvider() ?: return ""
            return runCatching {
                val selected = ic.getSelectedText(0)?.toString().orEmpty()
                if (!ic.performContextMenuAction(android.R.id.copy)) {
                    sendCtrlKey(ic, KeyEvent.KEYCODE_C)
                }
                selected
            }.getOrDefault("")
        }

        override fun cut(): String {
            val ic = icProvider() ?: return ""
            markSelfEdit()
            return runCatching {
                val selected = ic.getSelectedText(0)?.toString().orEmpty()
                if (!ic.performContextMenuAction(android.R.id.cut)) {
                    sendCtrlKey(ic, KeyEvent.KEYCODE_X)
                }
                selected
            }.getOrDefault("")
        }

        override fun paste(fallbackText: String): Boolean {
            val ic = icProvider() ?: return false
            markSelfEdit()
            return runCatching {
                if (ic.performContextMenuAction(android.R.id.paste)) {
                    return true
                }
                if (fallbackText.isNotEmpty()) {
                    ic.commitText(fallbackText, 1)
                    return true
                }
                sendCtrlKey(ic, KeyEvent.KEYCODE_V)
                true
            }.getOrDefault(false)
        }

        private fun dispatchHardwareEnter(ic: InputConnection): Boolean {
            val now = SystemClock.uptimeMillis()
            val downOk = runCatching {
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0))
            }.getOrDefault(false)
            val upOk = runCatching {
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, 0))
            }.getOrDefault(false)
            return downOk || upOk
        }

        override fun insertNewline() {
            val ic = icProvider() ?: return
            val nextEnd = if (!hasKnownSelection && expectedCursorEnd >= 0) {
                expectedCursorEnd + 1
            } else {
                -1
            }
            markSelfEdit(newExpectedEnd = nextEnd)
            if (isRawOrWebCodeEditor) {
                if (!dispatchHardwareEnter(ic)) {
                    runCatching { ic.commitText("\n", 1) }
                }
            } else {
                val committed = runCatching { ic.commitText("\n", 1) }.getOrDefault(false)
                if (!committed) {
                    dispatchHardwareEnter(ic)
                }
            }
        }

        override fun performEnterAction(actionType: EnterActionType) {
            val ic = icProvider() ?: return
            markSelfEdit(newExpectedEnd = -1)
            val editorAction = when (actionType) {
                EnterActionType.SEARCH -> EditorInfo.IME_ACTION_SEARCH
                EnterActionType.SEND -> EditorInfo.IME_ACTION_SEND
                EnterActionType.NEXT -> EditorInfo.IME_ACTION_NEXT
                EnterActionType.DONE -> EditorInfo.IME_ACTION_DONE
                EnterActionType.GO -> EditorInfo.IME_ACTION_GO
                EnterActionType.ENTER -> EditorInfo.IME_ACTION_UNSPECIFIED
            }
            if (editorAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
                val handled = runCatching { ic.performEditorAction(editorAction) }.getOrDefault(false)
                if (!handled && !dispatchHardwareEnter(ic)) {
                    runCatching { ic.commitText("\n", 1) }
                }
            } else {
                if (!dispatchHardwareEnter(ic)) {
                    runCatching { ic.commitText("\n", 1) }
                }
            }
        }

        override fun openAppSettings() {
            onOpenSettings()
        }
    }
}
