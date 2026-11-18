package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.FontStyleConverter
import com.example.data.SettingsRepository
import com.example.ime.KeyboardController
import com.example.ime.ShiftState
import com.example.ime.TextFieldImeTarget
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEngineUnitTest {

    @Test
    fun dictionaryEngine_suggestsPersonalShortcut() {
        val entries = listOf(
            PersonalDictionaryEntry(
                id = "1",
                word = "Hormat kami, Budi",
                shortcut = "ttd",
                updatedAtMillis = 100L
            )
        )
        val shortcutResult = DictionaryEngine.getSuggestions(
            currentWord = "ttd",
            previousWord = "",
            personalEntries = entries
        )
        assertEquals("Hormat kami, Budi", shortcutResult.primaryAutoCorrect)

        val unknownResult = DictionaryEngine.getSuggestions(
            currentWord = "tidk",
            previousWord = "saya",
            personalEntries = entries
        )
        assertNull(unknownResult.primaryAutoCorrect)
        assertTrue(unknownResult.suggestions.contains("tidk"))
        assertTrue(unknownResult.suggestions.size <= 3)

        val multiEntries = entries + listOf(
            PersonalDictionaryEntry(id = "2", word = "Halo semuanya", shortcut = "hs", updatedAtMillis = 101L),
            PersonalDictionaryEntry(id = "3", word = "Hari ini cerah", shortcut = "hc", updatedAtMillis = 102L)
        )
        val defaultEmptyResult = DictionaryEngine.getSuggestions(
            currentWord = "",
            previousWord = "",
            personalEntries = multiEntries
        )
        assertEquals(3, defaultEmptyResult.suggestions.size)

        val prefixResult = DictionaryEngine.getSuggestions(
            currentWord = "Ha",
            previousWord = "",
            personalEntries = multiEntries
        )
        assertEquals(3, prefixResult.suggestions.size)
    }

    @Test
    fun keyboardController_startsLowercaseWithoutAutoCapAutoCorrectOrDoubleSpace() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        assertEquals(ShiftState.OFF, controller.shiftState)
        controller.onCharacterTap("t", settings, emptyList())
        controller.onCharacterTap("i", settings, emptyList())
        controller.onCharacterTap("d", settings, emptyList())
        controller.onCharacterTap("k", settings, emptyList())
        assertEquals("tidk", tf.text)
        assertEquals(ShiftState.OFF, controller.shiftState)

        // Pressing space must NOT auto-correct typos or turn double-space into a period
        controller.onSpaceTap(settings, emptyList())
        controller.onSpaceTap(settings, emptyList())
        assertEquals("tidk  ", tf.text)

        // Period + space must NOT auto-capitalize the next letter
        controller.onCharacterTap(".", settings, emptyList())
        controller.onSpaceTap(settings, emptyList())
        assertEquals(ShiftState.OFF, controller.shiftState)
        controller.onCharacterTap("a", settings, emptyList())
        assertEquals("tidk  . a", tf.text)

        controller.onSpacebarTrackpadStep(-2)
        assertEquals(TextRange(tf.text.length - 2), tf.selection)
    }

    @Test
    fun twoWayDictionaryMerge_prefersNewerTimestamp() {
        val local = listOf(
            PersonalDictionaryEntry(id = "a", word = "lama", shortcut = "lm", updatedAtMillis = 100L),
            PersonalDictionaryEntry(id = "b", word = "lokal", shortcut = "lk", updatedAtMillis = 300L)
        )
        val remote = listOf(
            PersonalDictionaryEntry(id = "a", word = "baru", shortcut = "lm", updatedAtMillis = 200L),
            PersonalDictionaryEntry(id = "b", word = "kalah", shortcut = "lk", updatedAtMillis = 150L),
            PersonalDictionaryEntry(id = "c", word = "cloud", shortcut = "cl", updatedAtMillis = 250L)
        )
        val merged = SettingsRepository.mergeDictionaryLists(local, remote).associateBy { it.id }
        assertEquals("baru", merged["a"]?.word)
        assertEquals("lokal", merged["b"]?.word)
        assertEquals("cloud", merged["c"]?.word)
        assertFalse(merged["c"]?.deleted ?: true)
    }

    @Test
    fun keyboardController_commitsPlainTextEvenWithLegacyFlagAndPersonalShortcutsWork() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings(serifMiringEnabled = true)
        val entries = listOf(
            PersonalDictionaryEntry(
                id = "1",
                word = "Hormat kami",
                shortcut = "ttd",
                updatedAtMillis = 100L
            )
        )
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("d", settings, entries)
        assertEquals("ttd", tf.text)

        controller.onSpaceTap(settings, entries)
        assertEquals("Hormat kami ", tf.text)

        controller.onBackspaceTap(settings, entries)
        controller.onBackspaceTap(settings, entries)
        assertEquals("Hormat kam", tf.text)
    }

    @Test
    fun keyboardController_ctrlUndoRedoCutCopyPasteSelectAll() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        "halo".forEach { ch -> controller.onCharacterTap(ch.toString(), settings, emptyList()) }
        controller.onSpaceTap(settings, emptyList())
        "dunia".forEach { ch -> controller.onCharacterTap(ch.toString(), settings, emptyList()) }
        assertEquals("halo dunia", tf.text)

        // Ctrl+Z undoes last keystroke, Ctrl+Y redoes it
        controller.onCtrlUndo(settings, emptyList())
        assertEquals("halo duni", tf.text)
        controller.onCtrlRedo(settings, emptyList())
        assertEquals("halo dunia", tf.text)

        // Ctrl+A selects all text, Ctrl+C copies without mutating
        controller.onCtrlSelectAll()
        assertEquals(TextRange(0, tf.text.length), tf.selection)
        val copied = controller.onCtrlCopy()
        assertEquals("halo dunia", copied)
        assertEquals("halo dunia", tf.text)

        // Ctrl+X cuts selection, Ctrl+V pastes it back
        val cut = controller.onCtrlCut(settings, emptyList())
        assertEquals("halo dunia", cut)
        assertEquals("", tf.text)

        controller.onCtrlPaste(cut, settings, emptyList())
        assertEquals("halo dunia", tf.text)
    }

    @Test
    fun keyboardController_fastTypingUsesLocalBufferWithoutPerKeystrokeTargetReads() {
        val delegateTf = object {
            var value = TextFieldValue("")
        }
        var getTextBeforeCursorCalls = 0
        var singleCodePointDeletes = 0
        val delegate = TextFieldImeTarget(
            getValue = { delegateTf.value },
            setValue = { delegateTf.value = it }
        )
        val countingTarget = object : com.example.ime.ImeTarget by delegate {
            override fun getTextBeforeCursor(n: Int): String {
                getTextBeforeCursorCalls++
                return delegate.getTextBeforeCursor(n)
            }

            override fun deleteSingleCodePointBeforeCursor() {
                singleCodePointDeletes++
                delegate.deleteSingleCodePointBeforeCursor()
            }
        }

        val entries = listOf(
            PersonalDictionaryEntry(id = "1", word = "Terima kasih", shortcut = "tk", updatedAtMillis = 1L)
        )
        val settings = KeyboardSettings()
        val controller = KeyboardController(countingTarget)
        controller.configureForEditor(
            isNumeric = false,
            isPassword = false,
            actionType = com.example.ime.EnterActionType.ENTER,
            settings = settings,
            personalEntries = entries
        )
        // Reset counter after initial configureForEditor sync
        getTextBeforeCursorCalls = 0

        for (ch in "halo semua apa kabar tk") {
            if (ch == ' ') {
                controller.onSpaceTap(settings, entries)
            } else {
                controller.onCharacterTap(ch.toString(), settings, entries)
            }
        }
        controller.onSpaceTap(settings, entries)

        assertEquals("halo semua apa kabar Terima kasih ", delegateTf.value.text)
        assertEquals(0, getTextBeforeCursorCalls)

        // Rapid backspaces delete space + word letters using single-codepoint path without extra reads
        repeat(6) {
            controller.onBackspaceTap(settings, entries)
        }
        assertEquals(6, singleCodePointDeletes)
        assertEquals("halo semua apa kabar Terima ", delegateTf.value.text)
    }

    @Test
    fun settingsRepository_extractAutoSavableEntries_validatesGmailMin20AndNumberMin11() {
        // Too short Gmail (<20 chars) and wrong domain ignored; >=20 char @gmail.com saved
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("pendek@gmail.com"))
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("alamatsuperpanjang@yahoo.com"))
        assertEquals(
            listOf("alamatemailsuper@gmail.com"),
            SettingsRepository.extractAutoSavableEntries("alamatemailsuper@gmail.com")
        )

        // Numbers: 10 digits ignored, 11+ digits saved; email containing 12 digits only saves the email
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("0812345678"))
        assertEquals(
            listOf("08123456789"),
            SettingsRepository.extractAutoSavableEntries("08123456789")
        )
        assertEquals(
            listOf("+6281234567890"),
            SettingsRepository.extractAutoSavableEntries("Hubungi +6281234567890 segera")
        )
        assertEquals(
            listOf("user123456789012@gmail.com"),
            SettingsRepository.extractAutoSavableEntries("user123456789012@gmail.com")
        )
    }

    @Test
    fun keyboardController_suggestsAutoSavedNumberAndGmailOnPrefix() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val entries = listOf(
            PersonalDictionaryEntry(id = "auto_1", word = "081234567890", shortcut = "", updatedAtMillis = 10L),
            PersonalDictionaryEntry(id = "auto_2", word = "nozailkhan22@gmail.com", shortcut = "", updatedAtMillis = 11L)
        )
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        "0812".forEach { controller.onCharacterTap(it.toString(), settings, entries) }
        assertTrue(controller.suggestions.contains("081234567890"))

        tf = TextFieldValue("")
        controller.invalidateLocalWordCache()
        "nozailkhan22@".forEach { controller.onCharacterTap(it.toString(), settings, entries) }
        assertTrue(controller.suggestions.contains("nozailkhan22@gmail.com"))
    }

    @Test
    fun keyboardController_ctrlTapArmsResetsOnLetterAndDoubleTapLocks() {
        var tf = TextFieldValue("abc", TextRange(3))
        var targetRef: TextFieldImeTarget? = null
        targetRef = TextFieldImeTarget(
            getValue = { tf },
            setValue = { tf = it }
        )
        val controller = KeyboardController(targetRef)
        val settings = KeyboardSettings()

        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)

        // Single tap arms Ctrl; typing letter 'a' runs Ctrl+A and auto-deactivates
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        controller.onCharacterTap("a", settings, emptyList())
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(TextRange(0, 3), tf.selection)
        assertEquals("abc", tf.text)

        // Rapid double-tap locks Ctrl across multiple combos; next tap releases to OFF
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.LOCKED, controller.ctrlState)

        controller.onCharacterTap("b", settings, emptyList())
        assertEquals(com.example.ime.CtrlState.LOCKED, controller.ctrlState)
        assertEquals("Ctrl+B", targetRef.lastSentCtrlCombo)

        controller.onCharacterTap("f", settings, emptyList())
        assertEquals(com.example.ime.CtrlState.LOCKED, controller.ctrlState)
        assertEquals("Ctrl+F", targetRef.lastSentCtrlCombo)

        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
    }

    @Test
    fun textChunker_chunksLargeTextSafelyWithoutBreakingSurrogatesOrCombiningMarks() {
        val shortText = "Teks biasa " + "a".repeat(9_900)
        val shortChunks = com.example.data.TextChunker.splitForChunkedPaste(shortText)
        assertEquals(1, shortChunks.size)
        assertEquals(shortText, shortChunks.first())

        // Build > 10,000 char text containing emojis (surrogate pairs & ZWJ) + combining accents near boundaries
        val unit = "Halo 👨‍👩‍👧‍👦 café e\u0301😀 "
        val sb = StringBuilder(26_000)
        while (sb.length < 24_000) {
            sb.append(unit)
        }
        val longText = sb.toString()
        val chunks = com.example.data.TextChunker.splitForChunkedPaste(longText)
        assertTrue("Expected multiple chunks for ${longText.length} chars", chunks.size >= 6)
        chunks.forEach { chunk ->
            assertTrue("Chunk must remain non-empty", chunk.isNotEmpty())
            assertFalse("Chunk must not start with trailing low surrogate", Character.isLowSurrogate(chunk.first()))
            assertFalse("Chunk must not end with dangling high surrogate", Character.isHighSurrogate(chunk.last()))
            assertTrue("Chunk size reasonable", chunk.length <= com.example.data.TextChunker.CHUNK_PASTE_SIZE + 32)
        }
        assertEquals(longText, chunks.joinToString(""))

        val (preview, totalChars) = com.example.data.TextChunker.buildPreview(longText)
        assertEquals(longText.length, totalChars)
        assertTrue(preview.length <= com.example.data.TextChunker.PREVIEW_MAX_CHARS)
        assertTrue(preview.endsWith("..."))
    }

    @Test
    fun typingVsPasteIsolation_capsActiveWordAt30AndIgnoresPastedText() {
        val longWord = "abcdefghijklmnopqrstuvwxyz1234567890"
        val extracted = FontStyleConverter.extractCurrentWordRawAndPlain(longWord, maxCodePoints = 30)
        assertEquals(30, extracted.second.length)
        assertEquals(longWord.takeLast(30), extracted.second)

        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val entries = listOf(
            PersonalDictionaryEntry(id = "s1", word = "Terima kasih banyak", shortcut = "tk", updatedAtMillis = 10L)
        )
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        // Pasting shortcut text "tk" or a 15,000-char string must NEVER populate suggestions or trigger expansion
        val hugePaste = "tk " + "B".repeat(15_000)
        controller.pasteText(hugePaste, settings, entries)
        assertEquals(hugePaste, tf.text)
        assertTrue(controller.suggestions.isEmpty())
        assertNull(controller.primaryAutoCorrect)
    }

    @Test
    fun pasteCrashProtection_handlesFailingTargetWithoutThrowing() {
        val throwingTarget = object : com.example.ime.ImeTarget {
            override fun getTextBeforeCursor(n: Int) = ""
            override fun getSelectedText() = ""
            override fun commitText(text: String) {
                throw IllegalStateException("Simulated IPC transaction overflow")
            }
            override fun deleteBeforeCursor(count: Int) {}
            override fun replaceWordBeforeCursor(oldWordLength: Int, newText: String) {}
            override fun moveCursor(delta: Int, extendSelection: Boolean) {}
            override fun moveCursorVertical(deltaLines: Int) {}
            override fun moveCursorToEdge(toStart: Boolean) {}
            override fun selectAll() {}
            override fun performEnterAction(actionType: com.example.ime.EnterActionType) {}
        }
        val controller = KeyboardController(throwingTarget)
        controller.pasteText("x".repeat(25_000), KeyboardSettings(), emptyList())
        assertEquals("Gagal menempel teks", controller.clipboardStatusMessage)
    }

    @Test
    fun smallSuggestionDictionary_has70PercentIndonesian30PercentEnglishAndDoesNotModifyPersonalDict() {
        val idCount = DictionaryEngine.SMALL_DICT_INDONESIAN.size
        val enCount = DictionaryEngine.SMALL_DICT_ENGLISH.size
        assertEquals(420, idCount)
        assertEquals(180, enCount)
        assertEquals(600, (DictionaryEngine.SMALL_DICT_INDONESIAN + DictionaryEngine.SMALL_DICT_ENGLISH).distinct().size)

        // Indonesian & English prefix suggestions work even when personalEntries is completely empty
        val personal = mutableListOf<PersonalDictionaryEntry>()
        val idSugg = DictionaryEngine.getSuggestions("sek", "", personal)
        assertTrue(idSugg.suggestions.contains("sekarang"))

        val enSugg = DictionaryEngine.getSuggestions("plea", "", personal)
        assertTrue(enSugg.suggestions.contains("please"))

        // Personal entries stay untouched and personal shortcuts still take top priority
        assertTrue(personal.isEmpty())
        assertNull(DictionaryEngine.findShortcutMatch("saya", personal))
        personal.add(
            PersonalDictionaryEntry(id = "p1", word = "Selesai proyek", shortcut = "sel", updatedAtMillis = 10L)
        )
        val prioritized = DictionaryEngine.getSuggestions("sel", "", personal)
        assertEquals("Selesai proyek", prioritized.primaryAutoCorrect)
        assertEquals("Selesai proyek", prioritized.suggestions.first())
    }

    @Test
    fun dictionaryEngine_bucketLookupHandlesRapidPrefixTypingAcrossBothDictionaries() {
        val personal = listOf(
            PersonalDictionaryEntry(id = "p1", word = "KetikKu Cepat", shortcut = "kc", updatedAtMillis = 1L),
            PersonalDictionaryEntry(id = "p2", word = "Kantor Pusat", shortcut = "kp", updatedAtMillis = 2L)
        )
        // Mixed-case & rapid progressive prefix lookups hit matching first-char buckets reliably
        assertEquals("KetikKu Cepat", DictionaryEngine.getSuggestions("KC", "", personal).primaryAutoCorrect)
        assertTrue(DictionaryEngine.getSuggestions("Ka", "", personal).suggestions.contains("Kantor Pusat"))
        assertTrue(DictionaryEngine.getSuggestions("ban", "", personal).suggestions.contains("banyak"))
        assertTrue(DictionaryEngine.getSuggestions("Ban", "", personal).suggestions.contains("Banyak"))
        assertTrue(DictionaryEngine.getSuggestions("th", "", personal).suggestions.contains("thank"))
        assertTrue(DictionaryEngine.getSuggestions("xzq", "", personal).suggestions.contains("xzq"))
    }

    @Test
    fun dictionaryEngine_threeSuggestionsAndMutableListReplacementStayConsistent() {
        assertEquals(3, DictionaryEngine.getSuggestions("", "", emptyList()).suggestions.size)
        val personal = mutableListOf(
            PersonalDictionaryEntry(id = "p", word = "Selesai proyek", shortcut = "sel", updatedAtMillis = 1L)
        )
        assertEquals("Selesai proyek", DictionaryEngine.getSuggestions("sel", "", personal).primaryAutoCorrect)
        personal[0] = personal[0].copy(word = "Selamat bertugas", updatedAtMillis = 2L)
        assertEquals("Selamat bertugas", DictionaryEngine.getSuggestions("sel", "", personal).primaryAutoCorrect)
        val large = (1..10_000).map {
            PersonalDictionaryEntry(id = "$it", word = "kata$it", updatedAtMillis = it.toLong())
        }
        for (prefix in listOf("k", "ka", "kat", "kata", "kata9", "xyz")) {
            val suggestions = DictionaryEngine.getSuggestions(prefix, "", large).suggestions
            assertTrue(suggestions.size <= 3)
            assertEquals(suggestions.distinct(), suggestions)
        }
    }

    @Test
    fun dictionaryEngine_unmodifiableSnapshotAndLongPrefixOver16CharsWorkCleanly() {
        val snapshot1 = java.util.Collections.unmodifiableList(
            listOf(
                PersonalDictionaryEntry(
                    id = "email1",
                    word = "penggunapanjang2026@gmail.com",
                    shortcut = "",
                    updatedAtMillis = 10L
                )
            )
        )
        DictionaryEngine.prewarmIndex(snapshot1)

        // 18-character prefix (> MAX_INDEX_PREFIX_LEN = 16) must still suggest the 29-char Gmail entry
        val longPrefixRes = DictionaryEngine.getSuggestions("penggunapanjang202", "", snapshot1)
        assertTrue(longPrefixRes.suggestions.contains("penggunapanjang2026@gmail.com"))

        // Replacing with a new unmodifiableList instance invalidates cleanly in O(1)
        val snapshot2 = java.util.Collections.unmodifiableList(
            listOf(
                PersonalDictionaryEntry(
                    id = "p2",
                    word = "Rapat Mingguan",
                    shortcut = "rm",
                    updatedAtMillis = 20L
                )
            )
        )
        assertEquals("Rapat Mingguan", DictionaryEngine.getSuggestions("rm", "", snapshot2).primaryAutoCorrect)
    }
}

