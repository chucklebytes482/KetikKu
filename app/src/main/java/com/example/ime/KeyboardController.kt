package com.example.ime

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.FontStyleConverter
import com.example.data.TextChunker
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import com.example.model.WordPersonalityStat
import kotlinx.coroutines.delay
import java.util.Locale

enum class KeyboardPage {
    QWERTY,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMPAD
}

enum class UtilityPanel {
    NONE,
    CLIPBOARD
}

enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK
}

enum class CtrlState {
    OFF,
    ARMED,
    LOCKED
}

enum class EnterActionType(val label: String) {
    ENTER("↵"),
    SEARCH("Cari"),
    SEND("Kirim"),
    NEXT("Lanjut"),
    DONE("Selesai"),
    GO("Buka")
}

interface ImeTarget {
    fun getTextBeforeCursor(n: Int): String
    fun getSelectedText(): String
    fun commitText(text: String)
    fun deleteBeforeCursor(count: Int)
    fun deleteSingleCodePointBeforeCursor() = deleteBeforeCursor(1)
    fun replaceWordBeforeCursor(oldWordLength: Int, newText: String)
    fun moveCursor(delta: Int, extendSelection: Boolean = false)
    fun moveCursorVertical(deltaLines: Int)
    fun moveCursorToEdge(toStart: Boolean)
    fun selectAll()
    fun undo(): Boolean = false
    fun redo(): Boolean = false
    fun copy(): String = getSelectedText()
    fun cut(): String = getSelectedText()
    fun paste(fallbackText: String): Boolean {
        if (fallbackText.isNotEmpty()) {
            commitText(fallbackText)
            return true
        }
        return false
    }
    fun sendCtrlKey(letter: Char): Boolean = false
    fun insertNewline() = commitText("\n")
    fun performEnterAction(actionType: EnterActionType)
    fun openAppSettings() {}
}

internal fun shiftIndexByCodePoints(text: CharSequence, startIndex: Int, codePointDelta: Int): Int {
    var pos = startIndex.coerceIn(0, text.length)
    if (codePointDelta < 0) {
        repeat(-codePointDelta) {
            if (pos <= 0) return 0
            val cp = Character.codePointBefore(text, pos)
            pos = (pos - Character.charCount(cp)).coerceAtLeast(0)
        }
    } else if (codePointDelta > 0) {
        repeat(codePointDelta) {
            if (pos >= text.length) return text.length
            val cp = Character.codePointAt(text, pos)
            pos = (pos + Character.charCount(cp)).coerceAtMost(text.length)
        }
    }
    return pos
}

class TextFieldImeTarget(
    private val getValue: () -> TextFieldValue,
    private val setValue: (TextFieldValue) -> Unit,
    private val onOpenAppSettings: () -> Unit = {}
) : ImeTarget {

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    var lastSentCtrlCombo: String? = null
        private set

    private fun pushUndoSnapshot(current: TextFieldValue) {
        if (current.text.length > 50_000) return
        if (undoStack.lastOrNull()?.text == current.text) return
        if (undoStack.size >= 50) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current)
        redoStack.clear()
    }

    override fun openAppSettings() {
        onOpenAppSettings()
    }

    override fun getTextBeforeCursor(n: Int): String {
        val v = getValue()
        val end = v.selection.start.coerceIn(0, v.text.length)
        val start = (end - n).coerceAtLeast(0)
        return v.text.substring(start, end)
    }

    override fun getSelectedText(): String {
        val v = getValue()
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        return if (max > min) v.text.substring(min, max) else ""
    }

    override fun commitText(text: String) {
        val v = getValue()
        pushUndoSnapshot(v)
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        val newText = v.text.substring(0, min) + text + v.text.substring(max)
        val newCursor = min + text.length
        setValue(TextFieldValue(text = newText, selection = TextRange(newCursor)))
    }

    override fun deleteBeforeCursor(count: Int) {
        val v = getValue()
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        if (max > min) {
            pushUndoSnapshot(v)
            val newText = v.text.substring(0, min) + v.text.substring(max)
            setValue(TextFieldValue(text = newText, selection = TextRange(min)))
        } else if (min > 0) {
            pushUndoSnapshot(v)
            val delStart = (min - count).coerceAtLeast(0)
            val newText = v.text.substring(0, delStart) + v.text.substring(min)
            setValue(TextFieldValue(text = newText, selection = TextRange(delStart)))
        }
    }

    override fun deleteSingleCodePointBeforeCursor() {
        val v = getValue()
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        if (max > min) {
            pushUndoSnapshot(v)
            val newText = v.text.substring(0, min) + v.text.substring(max)
            setValue(TextFieldValue(text = newText, selection = TextRange(min)))
        } else if (min > 0) {
            pushUndoSnapshot(v)
            val delStart = shiftIndexByCodePoints(v.text, min, -1)
            val newText = v.text.substring(0, delStart) + v.text.substring(min)
            setValue(TextFieldValue(text = newText, selection = TextRange(delStart)))
        }
    }

    override fun replaceWordBeforeCursor(oldWordLength: Int, newText: String) {
        val v = getValue()
        pushUndoSnapshot(v)
        val end = v.selection.start.coerceIn(0, v.text.length)
        val start = (end - oldWordLength).coerceAtLeast(0)
        val updated = v.text.substring(0, start) + newText + v.text.substring(end)
        val cursor = start + newText.length
        setValue(TextFieldValue(text = updated, selection = TextRange(cursor)))
    }

    override fun moveCursor(delta: Int, extendSelection: Boolean) {
        val v = getValue()
        val next = shiftIndexByCodePoints(v.text, v.selection.end, delta)
        val newSel = if (extendSelection) TextRange(v.selection.start, next) else TextRange(next)
        setValue(v.copy(selection = newSel))
    }

    override fun moveCursorVertical(deltaLines: Int) {
        val v = getValue()
        val pos = v.selection.end.coerceIn(0, v.text.length)
        if (deltaLines < 0) {
            val prevNewline = v.text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0))
            val target = if (prevNewline >= 0) prevNewline else 0
            setValue(v.copy(selection = TextRange(target)))
        } else {
            val nextNewline = v.text.indexOf('\n', pos)
            val target = if (nextNewline >= 0) (nextNewline + 1).coerceAtMost(v.text.length) else v.text.length
            setValue(v.copy(selection = TextRange(target)))
        }
    }

    override fun moveCursorToEdge(toStart: Boolean) {
        val v = getValue()
        val target = if (toStart) 0 else v.text.length
        setValue(v.copy(selection = TextRange(target)))
    }

    override fun selectAll() {
        val v = getValue()
        setValue(v.copy(selection = TextRange(0, v.text.length)))
    }

    override fun undo(): Boolean {
        val prev = if (undoStack.isNotEmpty()) undoStack.removeLast() else return false
        val current = getValue()
        if (redoStack.size >= 50) {
            redoStack.removeFirst()
        }
        redoStack.addLast(current)
        setValue(prev)
        return true
    }

    override fun redo(): Boolean {
        val next = if (redoStack.isNotEmpty()) redoStack.removeLast() else return false
        val current = getValue()
        if (undoStack.size >= 50) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current)
        setValue(next)
        return true
    }

    override fun copy(): String = getSelectedText()

    override fun cut(): String {
        val selected = getSelectedText()
        if (selected.isNotEmpty()) {
            deleteSingleCodePointBeforeCursor()
        }
        return selected
    }

    override fun sendCtrlKey(letter: Char): Boolean {
        val lower = letter.lowercaseChar()
        if (lower !in 'a'..'z') return false
        lastSentCtrlCombo = "Ctrl+${lower.uppercaseChar()}"
        return true
    }

    override fun performEnterAction(actionType: EnterActionType) {
        commitText("\n")
    }
}

@Stable
class KeyboardController(
    private val target: ImeTarget
) {
    var currentPage by mutableStateOf(KeyboardPage.QWERTY)
        private set
    var utilityPanel by mutableStateOf(UtilityPanel.NONE)
        private set
    var shiftState by mutableStateOf(ShiftState.OFF)
        private set
    var ctrlState by mutableStateOf(CtrlState.OFF)
        private set
    var lastCtrlComboLabel by mutableStateOf<String?>(null)
        private set
    var clipboardStatusMessage by mutableStateOf<String?>(null)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var primaryAutoCorrect by mutableStateOf<String?>(null)
        private set
    var isSelectionMode by mutableStateOf(false)
        private set
    var enterActionType by mutableStateOf(EnterActionType.ENTER)
        private set
    var isPasswordField by mutableStateOf(false)
        private set
    var dismissedClipboardId by mutableStateOf<String?>(null)
        private set
    var dismissedClipboardSignature by mutableStateOf<String?>(null)
        private set

    var onRecordWordUsage: ((String) -> Unit)? = null

    private var personalityStats: MutableMap<String, WordPersonalityStat> = LinkedHashMap()

    fun updatePersonality(
        stats: Map<String, WordPersonalityStat>,
        settings: KeyboardSettings? = null,
        personalEntries: List<PersonalDictionaryEntry> = emptyList()
    ) {
        val merged = LinkedHashMap<String, WordPersonalityStat>(stats.size + personalityStats.size)
        merged.putAll(stats)
        for ((k, localStat) in personalityStats) {
            val remoteStat = merged[k]
            if (remoteStat == null || localStat.usageCount > remoteStat.usageCount || localStat.lastUsedMillis > remoteStat.lastUsedMillis) {
                merged[k] = localStat
            }
        }
        personalityStats = merged
        if (settings != null) {
            refreshState(settings, personalEntries)
        }
    }

    fun getPersonalitySnapshot(): Map<String, WordPersonalityStat> = personalityStats

    fun dismissClipboardItem(clipboardId: String) {
        dismissedClipboardId = clipboardId
    }

    fun dismissClipboardItem(item: ClipboardItem) {
        dismissedClipboardId = item.id
        dismissedClipboardSignature = item.dismissSignature()
    }

    fun showClipboardStatus(message: String?) {
        clipboardStatusMessage = message
    }

    private var lastShiftTapMillis = 0L
    private var lastCtrlTapMillis = 0L

    // Local word buffer avoids synchronous getTextBeforeCursor() Binder IPC on every fast keystroke
    private val localWordPlain = StringBuilder(32)
    private val localWordRawLengths = ArrayList<Int>(32)
    private var localWordRawTotalLen = 0
    private var localContextValid = false

    fun configureForEditor(
        isNumeric: Boolean,
        isPassword: Boolean,
        actionType: EnterActionType,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        isPasswordField = isPassword
        enterActionType = actionType
        currentPage = if (isNumeric) KeyboardPage.NUMPAD else KeyboardPage.QWERTY
        utilityPanel = UtilityPanel.NONE
        ctrlState = CtrlState.OFF
        clipboardStatusMessage = null
        invalidateLocalWordCache()
        refreshState(settings, personalEntries)
    }

    fun invalidateLocalWordCache() {
        localContextValid = false
        clearLocalWordBuffer()
    }

    private fun clearLocalWordBuffer() {
        localWordPlain.setLength(0)
        localWordRawLengths.clear()
        localWordRawTotalLen = 0
    }

    fun setPage(page: KeyboardPage) {
        currentPage = page
        utilityPanel = UtilityPanel.NONE
    }

    fun toggleUtilityPanel(panel: UtilityPanel) {
        utilityPanel = if (utilityPanel == panel) UtilityPanel.NONE else panel
    }

    fun closeUtilityPanel() {
        utilityPanel = UtilityPanel.NONE
    }

    fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
    }

    fun onShiftTap() {
        val now = System.currentTimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> {
                if (now - lastShiftTapMillis < 350L) ShiftState.CAPS_LOCK else ShiftState.SHIFTED
            }
            ShiftState.SHIFTED -> {
                if (now - lastShiftTapMillis < 350L) ShiftState.CAPS_LOCK else ShiftState.OFF
            }
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
        lastShiftTapMillis = now
    }

    fun onCtrlTap() {
        val now = System.currentTimeMillis()
        ctrlState = when (ctrlState) {
            CtrlState.OFF -> CtrlState.ARMED
            CtrlState.ARMED -> {
                if (now - lastCtrlTapMillis < 380L) CtrlState.LOCKED else CtrlState.OFF
            }
            CtrlState.LOCKED -> CtrlState.OFF
        }
        lastCtrlTapMillis = now
    }

    private fun formatOutput(text: String, @Suppress("UNUSED_PARAMETER") settings: KeyboardSettings): String = text

    fun onCharacterTap(
        rawChar: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>,
        onCopyResult: (String) -> Unit = {},
        onPasteRequest: (() -> Unit)? = null
    ) {
        if (ctrlState != CtrlState.OFF && rawChar.length == 1 && rawChar[0].isLetter()) {
            val lower = rawChar[0].lowercaseChar()
            runCatching {
                when (lower) {
                    'a' -> onCtrlSelectAll()
                    'c' -> {
                        val copied = onCtrlCopy()
                        if (copied.isNotEmpty()) onCopyResult(copied)
                    }
                    'x' -> {
                        val cut = onCtrlCut(settings, personalEntries)
                        if (cut.isNotEmpty()) onCopyResult(cut)
                    }
                    'v' -> {
                        if (onPasteRequest != null) {
                            onPasteRequest()
                        } else {
                            onCtrlPaste("", settings, personalEntries)
                        }
                    }
                    'z' -> onCtrlUndo(settings, personalEntries)
                    'y' -> onCtrlRedo(settings, personalEntries)
                    else -> target.sendCtrlKey(lower)
                }
            }.onFailure {
                clipboardStatusMessage = "Gagal mengirim kombinasi Ctrl+${lower.uppercaseChar()}"
            }
            lastCtrlComboLabel = "Ctrl+${lower.uppercaseChar()}"
            if (ctrlState == CtrlState.ARMED) {
                ctrlState = CtrlState.OFF
            }
            return
        }

        val letterTransformed = if (shiftState != ShiftState.OFF && rawChar.length == 1 && rawChar[0].isLetter()) {
            rawChar.uppercase()
        } else {
            rawChar
        }

        val isWordChar = rawChar.length == 1 && (
            rawChar[0].isLetterOrDigit() ||
                rawChar[0] == '\'' ||
                rawChar[0] == '@' ||
                rawChar[0] == '_' ||
                rawChar[0] == '+'
            )
        val isBoundaryPunctuation = rawChar.length == 1 && rawChar[0] in BOUNDARY_PUNCTUATION

        if (isBoundaryPunctuation) {
            recordCompletedTypedWordIfEligible(personalEntries)
        }

        val output = formatOutput(letterTransformed, settings)
        target.commitText(output)

        if (shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.OFF
        }

        if (isWordChar) {
            if (!localContextValid) {
                // Seed once after external cursor change, never on subsequent rapid characters
                seedLocalWordFromTarget()
            } else {
                localWordPlain.append(letterTransformed)
                localWordRawLengths.add(output.length)
                localWordRawTotalLen += output.length
                trimLocalWordBufferToLimit()
            }
            applySuggestionResult(personalEntries)
        } else {
            localContextValid = true
            clearLocalWordBuffer()
            applySuggestionResult(personalEntries)
        }
    }

    fun onSpaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        recordCompletedTypedWordIfEligible(personalEntries)
        target.commitText(" ")
        localContextValid = true
        clearLocalWordBuffer()
        applySuggestionResult(personalEntries)
    }

    fun onBackspaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        target.deleteSingleCodePointBeforeCursor()
        if (localContextValid && localWordPlain.isNotEmpty()) {
            localWordPlain.setLength(localWordPlain.length - 1)
            if (localWordRawLengths.isNotEmpty()) {
                val poppedRaw = localWordRawLengths.removeAt(localWordRawLengths.lastIndex)
                localWordRawTotalLen = (localWordRawTotalLen - poppedRaw).coerceAtLeast(0)
            }
            applySuggestionResult(personalEntries)
        } else {
            // Crossed into previous word or whitespace: avoid blocking IPC during rapid backspacing
            invalidateLocalWordCache()
            applySuggestionResult(personalEntries)
        }
    }

    fun onNewlineTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        recordCompletedTypedWordIfEligible(personalEntries)
        target.insertNewline()
        localContextValid = true
        clearLocalWordBuffer()
        applySuggestionResult(personalEntries)
    }

    fun onEnterTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        recordCompletedTypedWordIfEligible(personalEntries)
        target.performEnterAction(enterActionType)
        localContextValid = true
        clearLocalWordBuffer()
        applySuggestionResult(personalEntries)
    }

    fun onSuggestionTap(
        word: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val rawLen: Int
        val plainWord: String
        if (localContextValid) {
            rawLen = localWordRawTotalLen
            plainWord = localWordPlain.toString()
        } else {
            val pair = FontStyleConverter.extractCurrentWordRawAndPlain(target.getTextBeforeCursor(64))
            rawLen = pair.first
            plainWord = pair.second
        }
        val styled = formatOutput(word, settings)
        if (plainWord.isNotEmpty() && rawLen > 0) {
            target.replaceWordBeforeCursor(rawLen, "$styled ")
        } else {
            target.commitText("$styled ")
        }
        recordWordUsageInternal(word, personalEntries, forceRecord = true)
        localContextValid = true
        clearLocalWordBuffer()
        applySuggestionResult(personalEntries)
    }

    fun onSpacebarTrackpadStep(steps: Int) {
        if (steps != 0) {
            invalidateLocalWordCache()
            target.moveCursor(steps, extendSelection = false)
        }
    }

    fun onArrowMove(dx: Int, dy: Int) {
        invalidateLocalWordCache()
        if (dx != 0) target.moveCursor(dx, extendSelection = isSelectionMode)
        if (dy != 0) target.moveCursorVertical(dy)
    }

    fun onMoveEdge(toStart: Boolean) {
        invalidateLocalWordCache()
        target.moveCursorToEdge(toStart)
    }

    fun onSelectAll() {
        invalidateLocalWordCache()
        target.selectAll()
    }

    fun onCtrlUndo(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        target.undo()
        invalidateLocalWordCache()
        refreshState(settings, personalEntries)
    }

    fun onCtrlRedo(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        target.redo()
        invalidateLocalWordCache()
        refreshState(settings, personalEntries)
    }

    fun onCtrlSelectAll() {
        onSelectAll()
    }

    fun onCtrlCopy(): String = target.copy()

    fun onCtrlCut(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ): String {
        val cutText = target.cut()
        invalidateLocalWordCache()
        refreshState(settings, personalEntries)
        return cutText
    }

    fun onCtrlPaste(
        fallbackText: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        try {
            if (fallbackText.length >= TextChunker.DIRECT_PASTE_MAX_CHARS) {
                for (chunk in TextChunker.splitForChunkedPaste(fallbackText)) {
                    target.commitText(chunk)
                }
            } else {
                target.paste(fallbackText)
            }
            clearPostPasteSuggestionContext()
        } catch (_: Throwable) {
            clipboardStatusMessage = "Gagal menempel teks"
        }
    }

    fun getSelectedText(): String = runCatching { target.getSelectedText() }.getOrDefault("")

    fun pasteText(
        text: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (text.isEmpty()) return
        try {
            if (text.length < TextChunker.DIRECT_PASTE_MAX_CHARS) {
                target.commitText(text)
            } else {
                for (chunk in TextChunker.splitForChunkedPaste(text)) {
                    target.commitText(chunk)
                }
            }
            clearPostPasteSuggestionContext()
        } catch (_: Throwable) {
            clipboardStatusMessage = "Gagal menempel teks"
        }
    }

    suspend fun pasteTextChunked(
        text: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (text.isEmpty()) return
        try {
            if (text.length < TextChunker.DIRECT_PASTE_MAX_CHARS) {
                target.commitText(text)
            } else {
                val chunks = TextChunker.splitForChunkedPaste(text)
                for ((idx, chunk) in chunks.withIndex()) {
                    target.commitText(chunk)
                    if (idx < chunks.lastIndex) {
                        delay(8L)
                    }
                }
            }
            clearPostPasteSuggestionContext()
        } catch (_: Throwable) {
            clipboardStatusMessage = "Gagal menempel teks"
        }
    }

    private fun clearPostPasteSuggestionContext() {
        // Paste path strictly separated from typing suggestion path
        clearLocalWordBuffer()
        localContextValid = true
        if (suggestions.isNotEmpty()) suggestions = emptyList()
        if (primaryAutoCorrect != null) primaryAutoCorrect = null
    }

    fun convertSelectionToMonospace(enableMonospace: Boolean) {
        val selected = target.getSelectedText()
        if (selected.isEmpty() || isPasswordField) return
        val converted = if (enableMonospace) {
            FontStyleConverter.toMonospace(selected)
        } else {
            FontStyleConverter.fromMonospace(selected)
        }
        if (converted != selected) {
            target.commitText(converted)
            invalidateLocalWordCache()
        }
    }

    fun convertSelectionToSerifMiring(enableItalic: Boolean) {
        convertSelectionToMonospace(enableItalic)
    }

    fun convertSelectionToArialItalic(enableItalic: Boolean) {
        convertSelectionToMonospace(enableItalic)
    }

    fun onOpenSettings() {
        target.openAppSettings()
    }

    fun refreshState(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (isPasswordField) {
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            if (primaryAutoCorrect != null) primaryAutoCorrect = null
            return
        }
        if (!localContextValid) {
            seedLocalWordFromTarget()
        }
        applySuggestionResult(personalEntries)
    }

    private fun trimLocalWordBufferToLimit() {
        while (localWordPlain.length > MAX_ACTIVE_WORD_CHARS && localWordRawLengths.isNotEmpty()) {
            localWordPlain.deleteCharAt(0)
            val removedRaw = localWordRawLengths.removeAt(0)
            localWordRawTotalLen = (localWordRawTotalLen - removedRaw).coerceAtLeast(0)
        }
    }

    private fun seedLocalWordFromTarget() {
        val before = target.getTextBeforeCursor(MAX_ACTIVE_WORD_READ_UTF16)
        clearLocalWordBuffer()
        val (rawLen, currentWord) = FontStyleConverter.extractCurrentWordRawAndPlain(
            before = before,
            maxCodePoints = MAX_ACTIVE_WORD_CHARS
        )
        if (currentWord.isNotEmpty() && rawLen > 0) {
            localWordPlain.append(currentWord)
            localWordRawTotalLen = rawLen
            // Approximate per-codepoint raw lengths for accurate backspacing
            val rawStart = (before.length - rawLen).coerceAtLeast(0)
            var idx = rawStart
            while (idx < before.length) {
                val cp = before.codePointAt(idx)
                val count = Character.charCount(cp)
                localWordRawLengths.add(count)
                idx += count
            }
        }
        localContextValid = true
    }

    private fun recordCompletedTypedWordIfEligible(personalEntries: List<PersonalDictionaryEntry>) {
        if (isPasswordField) return
        val typed = if (localContextValid) {
            localWordPlain.toString()
        } else {
            FontStyleConverter.extractCurrentWordRawAndPlain(target.getTextBeforeCursor(48)).second
        }
        if (typed.isNotBlank()) {
            recordWordUsageInternal(typed, personalEntries, forceRecord = false)
        }
    }

    private fun recordWordUsageInternal(
        word: String,
        personalEntries: List<PersonalDictionaryEntry>,
        forceRecord: Boolean
    ) {
        if (isPasswordField) return
        val clean = word.trim()
        if (clean.isEmpty()) return
        val lower = clean.lowercase(Locale.ROOT)
        val isPersonalWord = personalEntries.any {
            !it.deleted && it.word.trim().equals(clean, ignoreCase = true)
        }
        if (!forceRecord && !isPersonalWord) return

        val now = System.currentTimeMillis()
        val existing = personalityStats[lower]
        personalityStats[lower] = WordPersonalityStat(
            word = clean,
            usageCount = (existing?.usageCount ?: 0) + 1,
            lastUsedMillis = now
        )
        onRecordWordUsage?.invoke(clean)
    }

    private fun applySuggestionResult(personalEntries: List<PersonalDictionaryEntry>) {
        if (isPasswordField) {
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            if (primaryAutoCorrect != null) primaryAutoCorrect = null
            return
        }
        val currentWord = localWordPlain.toString()
        val result = DictionaryEngine.getSuggestions(
            currentWord = currentWord,
            previousWord = "",
            personalEntries = personalEntries,
            personality = personalityStats
        )
        if (suggestions != result.suggestions) {
            suggestions = result.suggestions
        }
        if (primaryAutoCorrect != result.primaryAutoCorrect) {
            primaryAutoCorrect = result.primaryAutoCorrect
        }
    }

    companion object {
        const val MAX_ACTIVE_WORD_CHARS = 30
        private const val MAX_ACTIVE_WORD_READ_UTF16 = 60
        private val BOUNDARY_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':')

        fun extractCurrentWord(before: String): String =
            FontStyleConverter.extractCurrentWordRawAndPlain(before, MAX_ACTIVE_WORD_CHARS).second

        fun extractPreviousWord(before: String, currentWordLen: Int): String =
            FontStyleConverter.extractPreviousWordPlain(before, currentWordLen)
    }
}
