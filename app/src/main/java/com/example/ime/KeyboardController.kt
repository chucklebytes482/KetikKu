package com.example.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.KeyPoint
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry

enum class KeyboardPage {
    QWERTY,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMPAD
}

enum class UtilityPanel {
    NONE,
    CLIPBOARD,
    TEXT_EDIT
}

enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK
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
    fun replaceWordBeforeCursor(oldWordLength: Int, newText: String)
    fun moveCursor(delta: Int, extendSelection: Boolean = false)
    fun moveCursorVertical(deltaLines: Int)
    fun moveCursorToEdge(toStart: Boolean)
    fun selectAll()
    fun performEnterAction(actionType: EnterActionType)
    fun switchLanguageOrIme() {}
    fun openAppSettings() {}
}

class TextFieldImeTarget(
    private val getValue: () -> TextFieldValue,
    private val setValue: (TextFieldValue) -> Unit,
    private val onSwitchLanguage: () -> Unit = {}
) : ImeTarget {

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
            val newText = v.text.substring(0, min) + v.text.substring(max)
            setValue(TextFieldValue(text = newText, selection = TextRange(min)))
        } else if (min > 0) {
            val delStart = (min - count).coerceAtLeast(0)
            val newText = v.text.substring(0, delStart) + v.text.substring(min)
            setValue(TextFieldValue(text = newText, selection = TextRange(delStart)))
        }
    }

    override fun replaceWordBeforeCursor(oldWordLength: Int, newText: String) {
        val v = getValue()
        val end = v.selection.start.coerceIn(0, v.text.length)
        val start = (end - oldWordLength).coerceAtLeast(0)
        val updated = v.text.substring(0, start) + newText + v.text.substring(end)
        val cursor = start + newText.length
        setValue(TextFieldValue(text = updated, selection = TextRange(cursor)))
    }

    override fun moveCursor(delta: Int, extendSelection: Boolean) {
        val v = getValue()
        val next = (v.selection.end + delta).coerceIn(0, v.text.length)
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

    override fun performEnterAction(actionType: EnterActionType) {
        commitText("\n")
    }

    override fun switchLanguageOrIme() {
        onSwitchLanguage()
    }
}

class KeyboardController(
    private val target: ImeTarget
) {
    var currentPage by mutableStateOf(KeyboardPage.QWERTY)
        private set
    var utilityPanel by mutableStateOf(UtilityPanel.NONE)
        private set
    var shiftState by mutableStateOf(ShiftState.SHIFTED)
        private set
    var suggestions by mutableStateOf<List<String>>(listOf("Saya", "Tidak", "Terima"))
        private set
    var primaryAutoCorrect by mutableStateOf<String?>(null)
        private set
    var isSelectionMode by mutableStateOf(false)
        private set
    var enterActionType by mutableStateOf(EnterActionType.ENTER)
        private set
    var isPasswordField by mutableStateOf(false)
        private set
    var showToolbarIcons by mutableStateOf(true)
        private set

    private var lastShiftTapMillis = 0L
    private var lastSpaceTapMillis = 0L
    private var lastGlideInsertedLength = 0

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
        refreshState(settings, personalEntries)
    }

    fun setPage(page: KeyboardPage) {
        currentPage = page
        utilityPanel = UtilityPanel.NONE
    }

    fun toggleUtilityPanel(panel: UtilityPanel) {
        utilityPanel = if (utilityPanel == panel) UtilityPanel.NONE else panel
    }

    fun toggleToolbarMode() {
        showToolbarIcons = !showToolbarIcons
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

    fun onCharacterTap(
        rawChar: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        lastGlideInsertedLength = 0
        val out = if (shiftState != ShiftState.OFF && rawChar.length == 1 && rawChar[0].isLetter()) {
            rawChar.uppercase()
        } else {
            rawChar
        }

        // Punctuation triggers autocorrect on composing word
        if (rawChar.length == 1 && rawChar[0] in listOf('.', ',', '!', '?', ';', ':')) {
            applyAutoCorrectIfNeeded(settings, personalEntries, appendSpace = false)
        }

        target.commitText(out)

        if (shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.OFF
        }
        refreshState(settings, personalEntries)
    }

    fun onSpaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val now = System.currentTimeMillis()
        val before = target.getTextBeforeCursor(4)
        val isDoubleSpacePeriod = settings.doubleSpacePeriodEnabled &&
            now - lastSpaceTapMillis < 400L &&
            before.endsWith(" ") &&
            before.length >= 2 &&
            before[before.length - 2].isLetterOrDigit()

        if (isDoubleSpacePeriod) {
            target.deleteBeforeCursor(1)
            target.commitText(". ")
            lastSpaceTapMillis = 0L
        } else {
            val corrected = applyAutoCorrectIfNeeded(settings, personalEntries, appendSpace = true)
            if (!corrected) {
                target.commitText(" ")
            }
            lastSpaceTapMillis = now
        }
        lastGlideInsertedLength = 0
        refreshState(settings, personalEntries)
    }

    fun onBackspaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (lastGlideInsertedLength > 0) {
            target.deleteBeforeCursor(lastGlideInsertedLength)
            lastGlideInsertedLength = 0
        } else {
            target.deleteBeforeCursor(1)
        }
        refreshState(settings, personalEntries)
    }

    fun onDeleteWordSwipe(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val before = target.getTextBeforeCursor(40)
        if (before.isEmpty()) return
        var idx = before.length
        while (idx > 0 && before[idx - 1].isWhitespace()) idx--
        while (idx > 0 && !before[idx - 1].isWhitespace()) idx--
        val deleteCount = (before.length - idx).coerceAtLeast(1)
        target.deleteBeforeCursor(deleteCount)
        lastGlideInsertedLength = 0
        refreshState(settings, personalEntries)
    }

    fun onEnterTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        applyAutoCorrectIfNeeded(settings, personalEntries, appendSpace = false)
        target.performEnterAction(enterActionType)
        lastGlideInsertedLength = 0
        refreshState(settings, personalEntries)
    }

    fun onSuggestionTap(
        word: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val activeWord = extractCurrentWord(target.getTextBeforeCursor(48))
        if (activeWord.isNotEmpty()) {
            target.replaceWordBeforeCursor(activeWord.length, "$word ")
        } else if (lastGlideInsertedLength > 0) {
            target.deleteBeforeCursor(lastGlideInsertedLength)
            target.commitText("$word ")
        } else {
            target.commitText("$word ")
        }
        lastGlideInsertedLength = 0
        refreshState(settings, personalEntries)
    }

    fun onGlideGestureComplete(
        normalizedPath: List<KeyPoint>,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (!settings.glideTypingEnabled) return
        val matches = DictionaryEngine.decodeGlidePath(
            pathPoints = normalizedPath,
            language = settings.languageEnum,
            personalEntries = personalEntries
        )
        val topWord = matches.firstOrNull() ?: return
        val formatted = if (shiftState != ShiftState.OFF) {
            topWord.replaceFirstChar { it.uppercaseChar() }
        } else {
            topWord
        }
        val before = target.getTextBeforeCursor(2)
        val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val committed = (if (needsLeadingSpace) " " else "") + formatted + " "
        target.commitText(committed)
        lastGlideInsertedLength = committed.length
        if (shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.OFF
        }
        suggestions = matches.map {
            if (formatted.firstOrNull()?.isUpperCase() == true) it.replaceFirstChar { c -> c.uppercaseChar() } else it
        }
        primaryAutoCorrect = null
    }

    fun onSpacebarTrackpadStep(steps: Int) {
        if (steps != 0) {
            target.moveCursor(steps, extendSelection = false)
        }
    }

    fun onArrowMove(dx: Int, dy: Int) {
        if (dx != 0) target.moveCursor(dx, extendSelection = isSelectionMode)
        if (dy != 0) target.moveCursorVertical(dy)
    }

    fun onMoveEdge(toStart: Boolean) {
        target.moveCursorToEdge(toStart)
    }

    fun onSelectAll() {
        target.selectAll()
    }

    fun getSelectedText(): String = target.getSelectedText()

    fun pasteText(
        text: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        target.commitText(text)
        refreshState(settings, personalEntries)
    }

    fun onSwitchLanguageOrIme() {
        target.switchLanguageOrIme()
    }

    fun onOpenSettings() {
        target.openAppSettings()
    }

    fun refreshState(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val before = target.getTextBeforeCursor(64)
        if (settings.autoCapitalizeEnabled && currentPage == KeyboardPage.QWERTY && shiftState != ShiftState.CAPS_LOCK) {
            val trimmed = before.trimEnd()
            val shouldCap = before.isEmpty() ||
                before.endsWith("\n") ||
                (before.endsWith(" ") && trimmed.isNotEmpty() && trimmed.last() in listOf('.', '!', '?'))
            if (shouldCap) {
                shiftState = ShiftState.SHIFTED
            }
        }

        if (isPasswordField || !settings.suggestionStripEnabled) {
            suggestions = emptyList()
            primaryAutoCorrect = null
            return
        }

        val currentWord = extractCurrentWord(before)
        val previousWord = extractPreviousWord(before, currentWord.length)
        val result = DictionaryEngine.getSuggestions(
            currentWord = currentWord,
            previousWord = previousWord,
            language = settings.languageEnum,
            personalEntries = personalEntries
        )
        suggestions = result.suggestions
        primaryAutoCorrect = result.primaryAutoCorrect
    }

    private fun applyAutoCorrectIfNeeded(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>,
        appendSpace: Boolean
    ): Boolean {
        if (isPasswordField) return false
        val currentWord = extractCurrentWord(target.getTextBeforeCursor(48))
        if (currentWord.isEmpty()) return false

        // Always allow personal shortcut expansion, and autocorrect if enabled
        val shortcut = personalEntries.firstOrNull {
            !it.deleted && it.shortcut.isNotBlank() && it.shortcut.equals(currentWord, ignoreCase = true)
        }
        if (shortcut != null) {
            val suffix = if (appendSpace) " " else ""
            target.replaceWordBeforeCursor(currentWord.length, shortcut.word + suffix)
            return true
        }

        if (!settings.autoCorrectEnabled) return false
        val correction = primaryAutoCorrect
        if (!correction.isNullOrBlank() && !correction.equals(currentWord, ignoreCase = false)) {
            val suffix = if (appendSpace) " " else ""
            target.replaceWordBeforeCursor(currentWord.length, correction + suffix)
            return true
        }
        return false
    }

    companion object {
        fun extractCurrentWord(before: String): String {
            if (before.isEmpty()) return ""
            var i = before.length
            while (i > 0 && (before[i - 1].isLetter() || before[i - 1] == '\'')) {
                i--
            }
            return before.substring(i)
        }

        fun extractPreviousWord(before: String, currentWordLen: Int): String {
            val end = (before.length - currentWordLen).coerceAtLeast(0)
            var i = end
            while (i > 0 && before[i - 1].isWhitespace()) i--
            val wordEnd = i
            while (i > 0 && before[i - 1].isLetter()) i--
            return before.substring(i, wordEnd)
        }
    }
}
