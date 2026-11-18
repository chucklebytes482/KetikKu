package com.example.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.FontStyleConverter
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
    fun openAppSettings() {}
}

class TextFieldImeTarget(
    private val getValue: () -> TextFieldValue,
    private val setValue: (TextFieldValue) -> Unit
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
}

class KeyboardController(
    private val target: ImeTarget
) {
    var currentPage by mutableStateOf(KeyboardPage.QWERTY)
        private set
    var utilityPanel by mutableStateOf(UtilityPanel.NONE)
        private set
    var shiftState by mutableStateOf(ShiftState.OFF)
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

    private var lastShiftTapMillis = 0L

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

    private fun formatOutput(text: String, settings: KeyboardSettings): String =
        if (settings.serifMiringEnabled && !isPasswordField) {
            FontStyleConverter.toArialItalic(text)
        } else {
            text
        }

    fun onCharacterTap(
        rawChar: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val letterTransformed = if (shiftState != ShiftState.OFF && rawChar.length == 1 && rawChar[0].isLetter()) {
            rawChar.uppercase()
        } else {
            rawChar
        }

        if (rawChar.length == 1 && rawChar[0] in listOf('.', ',', '!', '?', ';', ':')) {
            applyPersonalShortcutIfNeeded(settings, personalEntries, appendSpace = false)
        }

        target.commitText(formatOutput(letterTransformed, settings))

        if (shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.OFF
        }
        refreshState(settings, personalEntries)
    }

    fun onSpaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val expanded = applyPersonalShortcutIfNeeded(settings, personalEntries, appendSpace = true)
        if (!expanded) {
            target.commitText(" ")
        }
        refreshState(settings, personalEntries)
    }

    fun onBackspaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val before = target.getTextBeforeCursor(4)
        target.deleteBeforeCursor(FontStyleConverter.lastCodePointCharCount(before))
        refreshState(settings, personalEntries)
    }

    fun onBackspaceSelectStep(delta: Int) {
        target.moveCursor(delta, extendSelection = true)
    }

    fun cancelBackspaceSelection() {
        val selected = target.getSelectedText()
        if (selected.isNotEmpty()) {
            target.moveCursor(0, extendSelection = false)
        }
    }

    fun onBackspaceReleaseOrTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val selected = target.getSelectedText()
        if (selected.isNotEmpty()) {
            target.deleteBeforeCursor(0)
        } else {
            onBackspaceTap(settings, personalEntries)
        }
        refreshState(settings, personalEntries)
    }

    fun onDeleteWordSwipe(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val before = target.getTextBeforeCursor(64)
        if (before.isEmpty()) return
        val pair = FontStyleConverter.extractCurrentWordRawAndPlain(before)
        val deleteCount = if (pair.first > 0) pair.first else 1
        target.deleteBeforeCursor(deleteCount)
        refreshState(settings, personalEntries)
    }

    fun onEnterTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        applyPersonalShortcutIfNeeded(settings, personalEntries, appendSpace = false)
        target.performEnterAction(enterActionType)
        refreshState(settings, personalEntries)
    }

    fun onSuggestionTap(
        word: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val (rawLen, plainWord) = FontStyleConverter.extractCurrentWordRawAndPlain(target.getTextBeforeCursor(64))
        val styled = formatOutput(word, settings)
        if (plainWord.isNotEmpty() && rawLen > 0) {
            target.replaceWordBeforeCursor(rawLen, "$styled ")
        } else {
            target.commitText("$styled ")
        }
        refreshState(settings, personalEntries)
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

    fun convertSelectionToSerifMiring(enableItalic: Boolean) {
        val selected = target.getSelectedText()
        if (selected.isEmpty() || isPasswordField) return
        val converted = if (enableItalic) {
            FontStyleConverter.toArialItalic(selected)
        } else {
            FontStyleConverter.fromArialItalic(selected)
        }
        if (converted != selected) {
            target.commitText(converted)
        }
    }

    fun convertSelectionToArialItalic(enableItalic: Boolean) {
        convertSelectionToSerifMiring(enableItalic)
    }

    fun onOpenSettings() {
        target.openAppSettings()
    }

    fun refreshState(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (isPasswordField) {
            suggestions = emptyList()
            primaryAutoCorrect = null
            return
        }

        val before = target.getTextBeforeCursor(96)
        val (rawLen, currentWord) = FontStyleConverter.extractCurrentWordRawAndPlain(before)
        val previousWord = FontStyleConverter.extractPreviousWordPlain(before, rawLen)
        val result = DictionaryEngine.getSuggestions(
            currentWord = currentWord,
            previousWord = previousWord,
            personalEntries = personalEntries
        )
        suggestions = result.suggestions
        primaryAutoCorrect = result.primaryAutoCorrect
    }

    private fun applyPersonalShortcutIfNeeded(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>,
        appendSpace: Boolean
    ): Boolean {
        if (isPasswordField) return false
        val (rawLen, currentWord) = FontStyleConverter.extractCurrentWordRawAndPlain(target.getTextBeforeCursor(96))
        if (currentWord.isEmpty() || rawLen == 0) return false

        val shortcut = personalEntries.firstOrNull {
            !it.deleted && it.shortcut.isNotBlank() && it.shortcut.equals(currentWord, ignoreCase = true)
        }
        if (shortcut != null) {
            val suffix = if (appendSpace) " " else ""
            target.replaceWordBeforeCursor(rawLen, formatOutput(shortcut.word, settings) + suffix)
            return true
        }
        return false
    }

    companion object {
        fun extractCurrentWord(before: String): String =
            FontStyleConverter.extractCurrentWordRawAndPlain(before).second

        fun extractPreviousWord(before: String, currentWordLen: Int): String =
            FontStyleConverter.extractPreviousWordPlain(before, currentWordLen)
    }
}
