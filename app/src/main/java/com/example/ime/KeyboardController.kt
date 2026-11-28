package com.example.ime

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.SettingsRepository
import com.example.data.SecretVault
import com.example.data.TotpGenerator
import com.example.data.FontStyleConverter
import com.example.data.TextChunker
import com.example.model.ClipboardItem
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Satu kolom bilah saran A2F = satu akun ([entryId]): kode 6 digit (baris atas) dan email (baris bawah).
 * [email] kosong jika akun tidak punya email; [shortEmail] adalah versi terpotong untuk tampilan.
 */
data class A2fChip(
    val entryId: String,
    val code: String,
    val email: String,
    val shortEmail: String
)

enum class KeyboardPage {
    QWERTY,
    SYMBOLS_1,
    SYMBOLS_2
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
    ARMED
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
    fun copy(): String = getSelectedText()
    fun cut(): String = getSelectedText()
    fun paste(fallbackText: String): Boolean {
        if (fallbackText.isNotEmpty()) {
            commitText(fallbackText)
            return true
        }
        return false
    }
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
    var lastSentCtrlCombo: String? = null
        private set

    private fun pushUndoSnapshot(current: TextFieldValue) {
        if (current.text.length > 50_000) return
        if (undoStack.lastOrNull()?.text == current.text) return
        if (undoStack.size >= 50) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current)
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
        setValue(prev)
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
    /** Sel A2F berurutan per akun: kode, lalu email. Hanya terisi saat [isA2fActive]. */
    var a2fChips by mutableStateOf<List<A2fChip>>(emptyList())
        private set
    /** True saat pintasan A2F cocok; bilah menampilkan kode dan email akun, bukan kata saran. */
    var isA2fActive by mutableStateOf(false)
        private set
    /** Saran lengkap untuk `num` dan pintasan kred/pw. */
    var folderSuggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var isSelectionMode by mutableStateOf(false)
        private set
    var enterActionType by mutableStateOf(EnterActionType.ENTER)
        private set
    var isPasswordField by mutableStateOf(false)
        private set
    var currentTypedText by mutableStateOf("")
        private set
    var selectedPreviewText by mutableStateOf("")
        private set
    val activePreviewText: String
        get() = selectedPreviewText.ifEmpty { currentTypedText }
    var ctrlActionsRemaining by mutableIntStateOf(0)
        private set
    var dismissedClipboardId by mutableStateOf<String?>(null)
        private set
    var dismissedClipboardSignature by mutableStateOf<String?>(null)
        private set

    /** Saran kata yang sering diketik (dari SQLite), key = kata huruf kecil, value = jumlah pemakaian. */
    var typedWordSuggester: ((String) -> Map<String, Int>)? = null

    /** Dipanggil saat kata yang diketik selesai (spasi/enter/tanda baca). Penyimpanan diatur pemanggil. */
    var onTypedWordCompleted: ((String) -> Unit)? = null

    /** Jumlah tap per entri A2F (dari SQLite). Dipakai untuk mengurutkan kolom A2F. */
    var entryUsageReader: ((String) -> Int)? = null

    /** Dipanggil saat kolom A2F diketuk. Penyimpanan diatur pemanggil. */
    var onA2fEntryUsed: ((String) -> Unit)? = null

    /** Kata folder yang sedang tampil -> ID entrinya, untuk mencatat pemakaian saat diketuk. */
    private var folderEntryIdByWord: Map<String, String> = emptyMap()

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
    // Live preview buffer for the 3rd suggestion column (tracks all non-whitespace characters currently being typed)
    private val livePreviewBuffer = StringBuilder(64)

    fun configureForEditor(
        isPassword: Boolean,
        actionType: EnterActionType,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        isPasswordField = isPassword
        enterActionType = actionType
        currentPage = KeyboardPage.QWERTY
        utilityPanel = UtilityPanel.NONE
        resetCtrlMode()
        clipboardStatusMessage = null
        invalidateLocalWordCache()
        refreshState(settings, personalEntries)
    }

    fun invalidateLocalWordCache() {
        localContextValid = false
        clearLocalWordBuffer()
        clearLivePreviewBuffer()
    }

    fun onSelectionUpdatedFromIme(hasSelection: Boolean, externalJump: Boolean) {
        val hadSelection = selectedPreviewText.isNotEmpty()
        if (hasSelection) {
            localContextValid = false
            clearLocalWordBuffer()
            syncSelectedPreviewFromTarget(hasSelectionHint = true)
        } else {
            syncSelectedPreviewFromTarget(hasSelectionHint = false)
            if (externalJump && !hadSelection) {
                invalidateLocalWordCache()
            } else if (externalJump) {
                localContextValid = false
                clearLocalWordBuffer()
            }
        }
    }

    fun syncSelectedPreviewFromTarget(hasSelectionHint: Boolean? = null) {
        if (isPasswordField || hasSelectionHint == false) {
            if (selectedPreviewText.isNotEmpty()) {
                selectedPreviewText = ""
            }
            return
        }
        val raw = runCatching { target.getSelectedText() }.getOrDefault("")
        val normalized = normalizeSelectionForPreview(raw)
        if (selectedPreviewText != normalized) {
            selectedPreviewText = normalized
        }
    }

    private fun clearSelectedPreview() {
        if (selectedPreviewText.isNotEmpty()) {
            selectedPreviewText = ""
        }
    }

    private fun normalizeSelectionForPreview(raw: String): String {
        if (raw.isEmpty()) return ""
        val singleLine = buildString(raw.length.coerceAtMost(MAX_LIVE_PREVIEW_CHARS)) {
            var lastWasSpace = false
            for (ch in raw) {
                val mapped = if (ch == '\n' || ch == '\r' || ch == '\t') ' ' else ch
                if (mapped == ' ') {
                    if (!lastWasSpace && isNotEmpty()) {
                        append(' ')
                        lastWasSpace = true
                    }
                } else {
                    append(mapped)
                    lastWasSpace = false
                }
                if (length >= MAX_LIVE_PREVIEW_CHARS) break
            }
        }.trim()
        return singleLine
    }

    private fun clearLocalWordBuffer() {
        localWordPlain.setLength(0)
        localWordRawLengths.clear()
        localWordRawTotalLen = 0
    }

    private fun clearLivePreviewBuffer() {
        if (livePreviewBuffer.isNotEmpty()) {
            livePreviewBuffer.setLength(0)
        }
        if (currentTypedText.isNotEmpty()) {
            currentTypedText = ""
        }
    }

    private fun appendLivePreview(text: String) {
        if (text.isEmpty()) return
        livePreviewBuffer.append(text)
        while (livePreviewBuffer.length > MAX_LIVE_PREVIEW_CHARS) {
            livePreviewBuffer.deleteCharAt(0)
        }
        currentTypedText = livePreviewBuffer.toString()
    }

    private fun popLivePreviewCodePoint() {
        if (livePreviewBuffer.isEmpty()) return
        val lastCp = Character.codePointBefore(livePreviewBuffer, livePreviewBuffer.length)
        val charCount = Character.charCount(lastCp)
        val newLen = (livePreviewBuffer.length - charCount).coerceAtLeast(0)
        livePreviewBuffer.setLength(newLen)
        currentTypedText = livePreviewBuffer.toString()
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

    private var ctrlActionsCount = 0

    private fun resetCtrlMode() {
        ctrlState = CtrlState.OFF
        ctrlActionsCount = 0
        ctrlActionsRemaining = 0
    }

    fun onCtrlTap() {
        val now = System.currentTimeMillis()
        if (ctrlState == CtrlState.OFF) {
            ctrlActionsCount = 0
            ctrlActionsRemaining = CTRL_STICKY_CHAR_LIMIT
            ctrlState = CtrlState.ARMED
        } else {
            resetCtrlMode()
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
            if (lower !in CTRL_ALLOWED_LETTERS) {
                // Ctrl+huruf selain A, C, X, V, Z tidak didukung: Ctrl mati, huruf diketik biasa.
                resetCtrlMode()
            } else {
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
                    }
                }.onFailure {
                    clipboardStatusMessage = "Gagal mengirim kombinasi Ctrl+${lower.uppercaseChar()}"
                }
                lastCtrlComboLabel = "Ctrl+${lower.uppercaseChar()}"
                ctrlActionsCount++
                if (ctrlActionsCount >= CTRL_STICKY_CHAR_LIMIT) {
                    resetCtrlMode()
                } else {
                    ctrlActionsRemaining = (CTRL_STICKY_CHAR_LIMIT - ctrlActionsCount).coerceAtLeast(0)
                }
                return
            }
        }

        val letterTransformed = if (shiftState != ShiftState.OFF && rawChar.length == 1 && rawChar[0].isLetter()) {
            rawChar.uppercase()
        } else {
            rawChar
        }

        val isUsedInPersonalShortcut = rawChar.length == 1 && !rawChar[0].isWhitespace() && personalEntries.any {
            !it.deleted && it.shortcut.isNotBlank() && it.shortcut.contains(rawChar[0], ignoreCase = true)
        } && (rawChar[0] != '.' || run {
            // Titik hanya bagian kata jika membentuk awalan pintasan (pintasan lama berformat "autgoogle.com" sebelum migrasi V6).
            val candidate = localWordPlain.toString() + "."
            personalEntries.any {
                !it.deleted && it.shortcut.isNotBlank() && it.shortcut.startsWith(candidate, ignoreCase = true)
            }
        })
        val isWordChar = (rawChar.length == 1 && (
            rawChar[0].isLetterOrDigit() ||
                rawChar[0] == '\'' ||
                rawChar[0] == '@' ||
                rawChar[0] == '_' ||
                rawChar[0] == '+' ||
                rawChar[0] == '-' ||
                rawChar[0] == '#'
            )) || isUsedInPersonalShortcut
        val isBoundaryPunctuation = !isUsedInPersonalShortcut && rawChar.length == 1 && rawChar[0] in BOUNDARY_PUNCTUATION

        if (isBoundaryPunctuation) {
            recordCompletedTypedWordIfEligible(personalEntries)
        }

        val output = formatOutput(letterTransformed, settings)
        target.commitText(output)
        clearSelectedPreview()

        if (shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.OFF
        }

        if (output.all { it.isWhitespace() }) {
            clearLivePreviewBuffer()
        } else {
            appendLivePreview(output)
        }

        if (isWordChar) {
            if (isPasswordField) {
                // Password boxes mask or withhold getTextBeforeCursor(); track typed shortcut locally instead.
                if (!localContextValid) {
                    clearLocalWordBuffer()
                    localContextValid = true
                }
                localWordPlain.append(letterTransformed)
                localWordRawLengths.add(output.length)
                localWordRawTotalLen += output.length
                trimLocalWordBufferToLimit()
            } else if (!localContextValid) {
                // Seed once after external cursor change, never on subsequent rapid characters.
                // If editor hides/masks/lags getTextBeforeCursor() (e.g. WebView/custom box), keep the typed char locally.
                seedLocalWordFromTarget()
                if (localWordPlain.isEmpty() || !localWordPlain.toString().endsWith(letterTransformed)) {
                    localWordPlain.append(letterTransformed)
                    localWordRawLengths.add(output.length)
                    localWordRawTotalLen += output.length
                    trimLocalWordBufferToLimit()
                }
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
        if (ctrlState != CtrlState.OFF) resetCtrlMode()
        recordCompletedTypedWordIfEligible(personalEntries)
        target.commitText(" ")
        clearSelectedPreview()
        localContextValid = true
        clearLocalWordBuffer()
        clearLivePreviewBuffer()
        applySuggestionResult(personalEntries)
    }

    fun onBackspaceTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (ctrlState != CtrlState.OFF) resetCtrlMode()
        target.deleteSingleCodePointBeforeCursor()
        clearSelectedPreview()
        if (livePreviewBuffer.isNotEmpty()) {
            popLivePreviewCodePoint()
        }
        if (localContextValid && localWordPlain.isNotEmpty()) {
            localWordPlain.setLength(localWordPlain.length - 1)
            if (localWordRawLengths.isNotEmpty()) {
                val poppedRaw = localWordRawLengths.removeAt(localWordRawLengths.lastIndex)
                localWordRawTotalLen = (localWordRawTotalLen - poppedRaw).coerceAtLeast(0)
            }
            applySuggestionResult(personalEntries)
        } else if (isPasswordField) {
            // Crossed into previous word or whitespace: avoid blocking IPC during rapid backspacing
            localContextValid = false
            clearLocalWordBuffer()
            applySuggestionResult(personalEntries)
        } else {
            // Kata sebelumnya kembali menjadi kata aktif (mis. spasi dihapus), jadi baca ulang dari editor.
            seedLocalWordFromTarget()
            applySuggestionResult(personalEntries)
        }
    }

    fun onNewlineTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (ctrlState != CtrlState.OFF) resetCtrlMode()
        recordCompletedTypedWordIfEligible(personalEntries)
        target.insertNewline()
        clearSelectedPreview()
        localContextValid = true
        clearLocalWordBuffer()
        clearLivePreviewBuffer()
        applySuggestionResult(personalEntries)
    }

    fun onEnterTap(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        if (ctrlState != CtrlState.OFF) resetCtrlMode()
        recordCompletedTypedWordIfEligible(personalEntries)
        target.performEnterAction(enterActionType)
        clearSelectedPreview()
        localContextValid = true
        clearLocalWordBuffer()
        clearLivePreviewBuffer()
        applySuggestionResult(personalEntries)
    }

    /** Tap kata folder (num, pw, kred). Pemakaian dicatat per entri, bukan per kata, agar isi rahasia tidak masuk statistik. */
    fun onFolderWordTap(
        word: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        // Isi kata folder (kata sandi, nomor) tidak dimasukkan ke buffer kata aktif.
        onSuggestionTap(word, settings, personalEntries, recordUsage = false, seedActiveWord = false)
    }

    fun onSuggestionTap(
        word: String,
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>,
        recordUsage: Boolean = true,
        seedActiveWord: Boolean = true
    ) {
        val styled = formatOutput(word, settings)
        replaceCurrentWordForSuggestion(styled)
        clearSelectedPreview()
        clearLivePreviewBuffer()
        if (isPasswordField || !seedActiveWord) {
            localContextValid = true
            clearLocalWordBuffer()
        } else {
            // Tanpa spasi, kata hasil tap tetap menjadi kata aktif: saran pintasan dan A2F ikut muncul.
            seedLocalWordFromTarget()
        }
        applySuggestionResult(personalEntries)
    }

    /**
     * Tap kolom A2F. Hanya kode 6 digit yang ditempel (tanpa email). Kode dihitung ulang dari seed saat ini.
     * Kode tidak dicatat; yang dicatat hanya jumlah tap entri akun untuk urutan kolom.
     */
    fun onA2fChipTap(
        chip: A2fChip,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        val seed = personalEntries.firstOrNull { it.id == chip.entryId && !it.deleted }
            ?.let { SecretVault.decryptOrNull(it.secretCipher) } ?: return
        val code = TotpGenerator.currentCodeFromBase32(seed) ?: return
        replaceCurrentWordForSuggestion(code)
        onA2fEntryUsed?.invoke(chip.entryId)
        clearSelectedPreview()
        localContextValid = true
        clearLocalWordBuffer()
        clearLivePreviewBuffer()
        applySuggestionResult(personalEntries)
    }

    private fun replaceCurrentWordForSuggestion(commitValue: String) {
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
        if (plainWord.isNotEmpty() && rawLen > 0) {
            target.replaceWordBeforeCursor(rawLen, commitValue)
        } else {
            target.commitText(commitValue)
        }
    }

    fun onSpacebarTrackpadStep(steps: Int) {
        if (steps != 0) {
            invalidateLocalWordCache()
            clearSelectedPreview()
            target.moveCursor(steps, extendSelection = false)
        }
    }

    fun onArrowMove(dx: Int, dy: Int) {
        if (dx != 0) target.moveCursor(dx, extendSelection = isSelectionMode)
        if (dy != 0) target.moveCursorVertical(dy)
        if (isSelectionMode && dx != 0) {
            localContextValid = false
            clearLocalWordBuffer()
            syncSelectedPreviewFromTarget(hasSelectionHint = null)
        } else {
            invalidateLocalWordCache()
            clearSelectedPreview()
        }
    }

    fun onMoveEdge(toStart: Boolean) {
        invalidateLocalWordCache()
        clearSelectedPreview()
        target.moveCursorToEdge(toStart)
    }

    fun onSelectAll() {
        localContextValid = false
        clearLocalWordBuffer()
        target.selectAll()
        syncSelectedPreviewFromTarget(hasSelectionHint = null)
    }

    fun onCtrlUndo(
        settings: KeyboardSettings,
        personalEntries: List<PersonalDictionaryEntry>
    ) {
        target.undo()
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
        clearLivePreviewBuffer()
        clearSelectedPreview()
        localContextValid = true
        if (suggestions.isNotEmpty()) suggestions = emptyList()
        if (primaryAutoCorrect != null) primaryAutoCorrect = null
        if (folderSuggestions.isNotEmpty()) folderSuggestions = emptyList()
        clearA2f()
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
            clearSelectedPreview()
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
        if (!isPasswordField && !localContextValid) {
            seedLocalWordFromTarget()
        }
        syncSelectedPreviewFromTarget()
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
            onTypedWordCompleted?.invoke(typed.trim())
        }
    }

    private var lastPersonalEntries: List<PersonalDictionaryEntry> = emptyList()

    /** Kode TOTP per entri untuk periode [a2fCodeWindow]. Hanya kode, tanpa seed. */
    private class A2fCodeCacheEntry(val cipher: String, val code: String?)
    private val a2fCodeCache = HashMap<String, A2fCodeCacheEntry>()
    private var a2fCodeWindow = -1L
    private fun applySuggestionResult(personalEntries: List<PersonalDictionaryEntry>) {
        lastPersonalEntries = personalEntries
        val currentWord = localWordPlain.toString()
        if (isPasswordField && currentWord.isEmpty()) {
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            if (primaryAutoCorrect != null) primaryAutoCorrect = null
            if (folderSuggestions.isNotEmpty()) folderSuggestions = emptyList()
            clearA2f()
            return
        }
        val a2f = buildA2fChips(currentWord, personalEntries)
        if (a2f.isNotEmpty()) {
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            if (primaryAutoCorrect != null) primaryAutoCorrect = null
            if (folderSuggestions.isNotEmpty()) folderSuggestions = emptyList()
            if (a2fChips != a2f) a2fChips = a2f
            if (!isA2fActive) isA2fActive = true
            return
        }
        clearA2f()
        val now = System.currentTimeMillis()
        val shortcutChoices = DictionaryEngine.findPagedShortcutChoices(currentWord, personalEntries, nowMillis = now)
        val folderChoices = if (shortcutChoices.isNotEmpty() || isPasswordField) shortcutChoices
        else DictionaryEngine.findFolderEntryChoices(currentWord, personalEntries, nowMillis = now)
        if (folderChoices.isNotEmpty()) {
            val folderWords = folderChoices.map { it.word }.distinct()
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            if (primaryAutoCorrect != null) primaryAutoCorrect = null
            if (folderSuggestions != folderWords) folderSuggestions = folderWords
            folderEntryIdByWord = folderChoices.distinctBy { it.word }.associate { it.word to it.entryId }
            return
        }
        if (folderSuggestions.isNotEmpty()) folderSuggestions = emptyList()
        if (folderEntryIdByWord.isNotEmpty()) folderEntryIdByWord = emptyMap()
        val result = DictionaryEngine.getSuggestions(
            currentWord = currentWord,
            previousWord = "",
            personalEntries = personalEntries,
            nowMillis = now,
            habit = typedWordSuggester?.invoke(currentWord.trim()) ?: emptyMap()
        )
        if (suggestions != result.suggestions) {
            suggestions = result.suggestions
        }
        if (primaryAutoCorrect != result.primaryAutoCorrect) {
            primaryAutoCorrect = result.primaryAutoCorrect
        }
    }

    /** Satu kolom per akun yang berbagi pintasan: kode 6 digit dan email (dipotong di bilah). */
    private fun buildA2fChips(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>
    ): List<A2fChip> {
        val chips = ArrayList<A2fChip>()
        val usage = entryUsageReader
        // Paling sering diketuk dulu, lalu yang paling baru diubah.
        val ordered = DictionaryEngine.findA2fEntries(currentWord, personalEntries).sortedWith(
            compareByDescending<PersonalDictionaryEntry> { usage?.invoke(it.id) ?: 0 }
                .thenByDescending { it.updatedAtMillis }
        )
        val window = System.currentTimeMillis() / 1000L / TotpGenerator.PERIOD_SECONDS
        if (window != a2fCodeWindow) {
            // Periode baru: kode lama tidak berlaku lagi. Buang semua, termasuk entri yang sudah dihapus.
            a2fCodeCache.clear()
            a2fCodeWindow = window
        }
        ordered.forEach { entry ->
            // Kode dihitung ulang hanya sekali per entri per periode 30 detik. Seed tidak disimpan,
            // yang disimpan hanya kode 6 digit. Ciphertext ikut dibandingkan agar edit akun langsung terpakai.
            val cached = a2fCodeCache[entry.id]
            val code = if (cached != null && cached.cipher == entry.secretCipher) {
                cached.code
            } else {
                val seed = SecretVault.decryptOrNull(entry.secretCipher)
                val fresh = seed?.let { TotpGenerator.currentCodeFromBase32(it) }
                a2fCodeCache[entry.id] = A2fCodeCacheEntry(entry.secretCipher, fresh)
                fresh
            } ?: return@forEach
            val email = entry.word.substringBefore('\n').trim()
            chips += A2fChip(
                entryId = entry.id,
                code = code,
                email = email,
                shortEmail = if (email.isEmpty()) "" else SettingsRepository.shortA2fEmail(email)
            )
        }
        return chips
    }

    private fun clearA2f() {
        if (a2fChips.isNotEmpty()) a2fChips = emptyList()
        if (isA2fActive) isA2fActive = false
    }

    /** Dipanggil ticker tiap 30 detik saat [isA2fActive]. Hitung ulang kode dari waktu sekarang. */
    fun refreshA2fCode() {
        if (!isA2fActive) return
        applySuggestionResult(lastPersonalEntries)
    }

    companion object {
        const val MAX_ACTIVE_WORD_CHARS = 30
        const val MAX_LIVE_PREVIEW_CHARS = 120
        const val CTRL_STICKY_CHAR_LIMIT = 1
        /** Satu-satunya kombinasi Ctrl+huruf yang didukung. */
        val CTRL_ALLOWED_LETTERS = setOf('a', 'c', 'x', 'v', 'z')
        private const val MAX_ACTIVE_WORD_READ_UTF16 = 60
        private val BOUNDARY_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':')

        fun extractCurrentWord(before: String): String =
            FontStyleConverter.extractCurrentWordRawAndPlain(before, MAX_ACTIVE_WORD_CHARS).second

        fun extractPreviousWord(before: String, currentWordLen: Int): String =
            FontStyleConverter.extractPreviousWordPlain(before, currentWordLen)
    }
}
