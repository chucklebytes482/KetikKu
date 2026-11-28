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
import com.example.model.WordPersonalityStat
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

        // Built-in suggestion dictionary is removed: unknown words return empty suggestions
        val unknownResult = DictionaryEngine.getSuggestions(
            currentWord = "tidk",
            previousWord = "saya",
            personalEntries = entries
        )
        assertNull(unknownResult.primaryAutoCorrect)
        assertTrue(unknownResult.suggestions.isEmpty())

        val multiEntries = entries + listOf(
            PersonalDictionaryEntry(id = "2", word = "Halo semuanya", shortcut = "hs", updatedAtMillis = 101L),
            PersonalDictionaryEntry(id = "3", word = "Hari ini cerah", shortcut = "hc", updatedAtMillis = 102L)
        )
        val defaultEmptyResult = DictionaryEngine.getSuggestions(
            currentWord = "",
            previousWord = "",
            personalEntries = multiEntries
        )
        assertTrue(defaultEmptyResult.suggestions.isEmpty())

        // Saran kata dari kamus pribadi baru muncul dari 3 huruf.
        val twoLetterResult = DictionaryEngine.getSuggestions(
            currentWord = "Ha",
            previousWord = "",
            personalEntries = multiEntries
        )
        assertFalse(twoLetterResult.suggestions.contains("Halo semuanya"))
        assertFalse(twoLetterResult.suggestions.contains("Hari ini cerah"))

        val prefixResult = DictionaryEngine.getSuggestions(
            currentWord = "Hal",
            previousWord = "",
            personalEntries = multiEntries
        )
        assertTrue(prefixResult.suggestions.contains("Halo semuanya"))
        assertFalse(prefixResult.suggestions.contains("Hari ini cerah"))
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
    fun keyboardController_commitsPlainTextEvenWithLegacyFlagAndPersonalShortcutsRequireTap() {
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
        assertTrue(controller.suggestions.contains("Hormat kami"))

        // Pressing space must NOT auto-fill personal dictionary shortcut
        controller.onSpaceTap(settings, entries)
        assertEquals("ttd ", tf.text)

        // Tapping the suggestion chip explicitly fills the entry
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("d", settings, entries)
        assertTrue(controller.suggestions.contains("Hormat kami"))
        controller.onSuggestionTap("Hormat kami", settings, entries)
        assertEquals("ttd Hormat kami", tf.text)

        controller.onBackspaceTap(settings, entries)
        assertEquals("ttd Hormat kam", tf.text)
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

        // Ctrl+Z undoes last keystroke; retype the letter to get back to "halo dunia"
        controller.onCtrlUndo(settings, emptyList())
        assertEquals("halo duni", tf.text)
        controller.onCharacterTap("a", settings, emptyList())
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

        assertEquals("halo semua apa kabar tk ", delegateTf.value.text)
        assertEquals(0, getTextBeforeCursorCalls)

        // Rapid backspaces delete space + word letters using single-codepoint path without extra reads
        repeat(3) {
            controller.onBackspaceTap(settings, entries)
        }
        assertEquals(3, singleCodePointDeletes)
        assertEquals("halo semua apa kabar ", delegateTf.value.text)
    }

    @Test
    fun settingsRepository_extractAutoSavableEntries_onlyNumbersAreAutoSaved() {
        // Alamat @gmail.com tidak lagi disimpan otomatis (folder gm dihapus).
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("pendek@gmail.com"))
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("alamatemailsuper@gmail.com"))
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("user123456789012@gmail.com"))
        // Angka di dalam alamat email tetap tidak dianggap nomor.
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("08123456789@gmail.com"))

        // Numbers: must start with +62, 62, or 08 AND have 11+ digits; other prefixes or <11 digits ignored
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("0812345678"))
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("123456789012"))
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("0215551234567"))
        assertEquals(emptyList<String>(), SettingsRepository.extractAutoSavableEntries("+14155551234"))
        assertEquals(
            listOf("08123456789"),
            SettingsRepository.extractAutoSavableEntries("08123456789")
        )
        assertEquals(
            listOf("628123456789"),
            SettingsRepository.extractAutoSavableEntries("Nomor WA: 628123456789")
        )
        assertEquals(
            listOf("+6281234567890"),
            SettingsRepository.extractAutoSavableEntries("Hubungi +6281234567890 segera")
        )
    }

    @Test
    fun keyboardController_suggestsAutoSavedNumberAndDictionaryWordOnPrefix() {
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
    fun keyboardController_ctrlPersistsForOneLetterAndAutoResetsOnNonLetterOrToggle() {
        var tf = TextFieldValue("abc", TextRange(3))
        var targetRef: TextFieldImeTarget? = null
        targetRef = TextFieldImeTarget(
            getValue = { tf },
            setValue = { tf = it }
        )
        val controller = KeyboardController(targetRef)
        val settings = KeyboardSettings()

        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(0, controller.ctrlActionsRemaining)

        // Single tap arms Ctrl for exactly 1 letter combo (e.g. Ctrl+A)
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        assertEquals(1, controller.ctrlActionsRemaining)

        // 1st letter: Ctrl+A selects all and automatically turns Ctrl OFF
        controller.onCharacterTap("a", settings, emptyList())
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(0, controller.ctrlActionsRemaining)
        assertEquals(TextRange(0, 3), tf.selection)

        // Tapping Ctrl again arms for 1 more combo: Ctrl+C copies and turns OFF immediately
        var copied = ""
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        assertEquals(1, controller.ctrlActionsRemaining)
        controller.onCharacterTap("c", settings, emptyList(), onCopyResult = { copied = it })
        assertEquals("abc", copied)
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(0, controller.ctrlActionsRemaining)

        // Next letter after Ctrl turns OFF types normally instead of triggering another shortcut
        controller.onCharacterTap("x", settings, emptyList())
        assertEquals("x", tf.text)

        // Tapping Ctrl a second time toggles it OFF immediately (no lock mode)
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)

        // While armed, tapping Backspace / Space / Enter immediately resets Ctrl to OFF
        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        controller.onBackspaceTap(settings, emptyList())
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(0, controller.ctrlActionsRemaining)

        controller.onCtrlTap()
        controller.onSpaceTap(settings, emptyList())
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)

        controller.onCtrlTap()
        controller.onEnterTap(settings, emptyList())
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)

        // Ctrl+B tidak didukung: Ctrl mati dan huruf diketik biasa; switching editor resets armed Ctrl cleanly
        controller.onCtrlTap()
        controller.onCharacterTap("b", settings, emptyList())
        assertNull(targetRef.lastSentCtrlCombo)
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(0, controller.ctrlActionsRemaining)

        controller.onCtrlTap()
        assertEquals(com.example.ime.CtrlState.ARMED, controller.ctrlState)
        controller.configureForEditor(
            isPassword = false,
            actionType = com.example.ime.EnterActionType.ENTER,
            settings = settings,
            personalEntries = emptyList()
        )
        assertEquals(com.example.ime.CtrlState.OFF, controller.ctrlState)
        assertEquals(0, controller.ctrlActionsRemaining)
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
    fun dictionaryEngine_usesOnlyPersonalDictionaryWithoutBuiltInSuggestions() {
        // When personalEntries is empty, both idle & prefix typing must yield zero suggestions
        val idleEmpty = DictionaryEngine.getSuggestions("", "", emptyList())
        assertTrue(idleEmpty.suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("sek", "", emptyList()).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("tomo", "", emptyList()).suggestions.isEmpty())

        // Non-personal personality history words must NOT be suggested
        val nonPersonalPersonality = mapOf(
            "sekarang" to WordPersonalityStat(word = "sekarang", usageCount = 99, lastUsedMillis = 1000L)
        )
        assertTrue(DictionaryEngine.getSuggestions("", "", emptyList(), nonPersonalPersonality, 1000L).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("sek", "", emptyList(), nonPersonalPersonality, 1000L).suggestions.isEmpty())

        // Personal dictionary entries (short or long) remain suggested by shortcut and prefix
        val personal = mutableListOf<PersonalDictionaryEntry>()
        assertNull(DictionaryEngine.findShortcutMatch("sekarang", personal))
        personal.add(
            PersonalDictionaryEntry(id = "p1", word = "Oke", shortcut = "ok", updatedAtMillis = 10L)
        )
        val prioritized = DictionaryEngine.getSuggestions("ok", "", personal)
        assertEquals("Oke", prioritized.primaryAutoCorrect)
        assertEquals(listOf("Oke"), prioritized.suggestions)
    }

    @Test
    fun dictionaryEngine_bucketLookupHandlesRapidPrefixTypingAcrossPersonalDictionary() {
        val personal = listOf(
            PersonalDictionaryEntry(id = "p1", word = "KetikKu Cepat", shortcut = "kc", updatedAtMillis = 1L),
            PersonalDictionaryEntry(id = "p2", word = "Kantor Pusat", shortcut = "kp", updatedAtMillis = 2L)
        )
        // Mixed-case & rapid progressive prefix lookups hit matching first-char buckets reliably
        assertEquals("KetikKu Cepat", DictionaryEngine.getSuggestions("KC", "", personal).primaryAutoCorrect)
        assertFalse(DictionaryEngine.getSuggestions("Ka", "", personal).suggestions.contains("Kantor Pusat"))
        assertTrue(DictionaryEngine.getSuggestions("Kan", "", personal).suggestions.contains("Kantor Pusat"))
        assertTrue(DictionaryEngine.getSuggestions("kan", "", personal).suggestions.contains("Kantor Pusat"))
        // Built-in words like "informasi" / "schedule" and unknown tokens "xzq" must NOT appear
        assertTrue(DictionaryEngine.getSuggestions("info", "", personal).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("sche", "", personal).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("xzq", "", personal).suggestions.isEmpty())
    }

    @Test
    fun dictionaryEngine_threeSuggestionsAndMutableListReplacementStayConsistent() {
        assertTrue(DictionaryEngine.getSuggestions("", "", emptyList()).suggestions.isEmpty())
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

    @Test
    fun multiShortcut_shows3MatchesByPersonalityAndSpacebarPreservesTypedText() {
        val entries = listOf(
            PersonalDictionaryEntry(id = "1", word = "Selamat pagi", shortcut = "sl", updatedAtMillis = 10L),
            PersonalDictionaryEntry(id = "2", word = "Selamat siang", shortcut = "sl", updatedAtMillis = 20L),
            PersonalDictionaryEntry(id = "3", word = "Selamat malam", shortcut = "sl", updatedAtMillis = 30L),
            PersonalDictionaryEntry(id = "4", word = "Salam hangat", shortcut = "sl", updatedAtMillis = 5L)
        )
        val now = 1_000_000L
        val personality = mapOf(
            "salam hangat" to WordPersonalityStat(word = "Salam hangat", usageCount = 5, lastUsedMillis = now),
            "selamat pagi" to WordPersonalityStat(word = "Selamat pagi", usageCount = 3, lastUsedMillis = now - 1000L),
            "selamat malam" to WordPersonalityStat(word = "Selamat malam", usageCount = 1, lastUsedMillis = now - 2000L)
        )

        val result = DictionaryEngine.getSuggestions(
            currentWord = "sl",
            previousWord = "",
            personalEntries = entries,
            personality = personality,
            nowMillis = now
        )
        // More than 1 shortcut match -> no forced auto-correct, top 3 ordered by personality score
        assertNull(result.primaryAutoCorrect)
        assertEquals(listOf("Salam hangat", "Selamat pagi", "Selamat malam"), result.suggestions)

        // Spacebar in KeyboardController preserves typed "sl " when >1 personal entries share the shortcut
        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )
        controller.onCharacterTap("s", settings, entries)
        controller.onCharacterTap("l", settings, entries)
        // Tanpa kebiasaan, urutan dari updatedAt terbaru: 3 teratas, tanpa auto-correct paksa
        assertEquals(3, controller.suggestions.size)
        assertTrue(controller.suggestions.all { it in setOf("Selamat pagi", "Selamat siang", "Selamat malam", "Salam hangat") })

        controller.onSpaceTap(settings, entries)
        assertEquals("sl ", tf.text)
    }

    @Test
    fun textSuggestion_selectedPersonalEntriesStillListedAfterTaps() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val personal = listOf(
            PersonalDictionaryEntry(id = "1", word = "perhatian", shortcut = "", updatedAtMillis = 10L),
            PersonalDictionaryEntry(id = "2", word = "pertanyaan", shortcut = "", updatedAtMillis = 10L),
            PersonalDictionaryEntry(id = "3", word = "perjalanan", shortcut = "", updatedAtMillis = 10L)
        )
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        // Initially no personal entries are suggested if personalEntries is empty
        "per".forEach { controller.onCharacterTap(it.toString(), settings, emptyList()) }
        assertTrue(controller.suggestions.isEmpty())

        tf = TextFieldValue("")
        controller.invalidateLocalWordCache()

        // Tap "perjalanan" berulang; kebiasaan tidak lagi mengubah urutan, hanya memastikan tidak crash
        repeat(3) {
            controller.onCharacterTap("p", settings, personal)
            controller.onCharacterTap("e", settings, personal)
            controller.onCharacterTap("r", settings, personal)
            controller.onSuggestionTap("perjalanan", settings, personal)
            controller.onSpaceTap(settings, personal)
        }
        controller.onCharacterTap("p", settings, personal)
        controller.onCharacterTap("e", settings, personal)
        controller.onCharacterTap("r", settings, personal)
        assertTrue(controller.suggestions.contains("perjalanan"))
    }

    @Test
    fun typingFolderNameNum_listsAllActiveNumEntriesNewestFirst() {
        val entries = listOf(
            PersonalDictionaryEntry(id = "n1", word = "081111111111", folder = "num", updatedAtMillis = 10L),
            PersonalDictionaryEntry(id = "n2", word = "085222222222", folder = "num", updatedAtMillis = 30L),
            PersonalDictionaryEntry(id = "n3", word = "089333333333", folder = "num", updatedAtMillis = 20L, deleted = true),
            PersonalDictionaryEntry(id = "k1", word = "081444444444", folder = "Kerja", updatedAtMillis = 40L),
            PersonalDictionaryEntry(
                id = "n4", word = "087555555555", folder = SettingsRepository.FOLDER_SAMPAH,
                updatedAtMillis = 5L, trashedFrom = "num"
            )
        )
        assertEquals(listOf("085222222222", "081111111111"), DictionaryEngine.findFolderEntryWords("num", entries))
        assertEquals(listOf("085222222222", "081111111111"), DictionaryEngine.findFolderEntryWords("NUM", entries))
        assertTrue(DictionaryEngine.findFolderEntryWords("nu", entries).isEmpty())
        assertTrue(DictionaryEngine.findFolderEntryWords("", entries).isEmpty())
    }

    @Test
    fun folderedPersonalDictionaryEntries_remainActiveForShortcutsAndPrefixSuggestions() {
        val folderedEntries = listOf(
            PersonalDictionaryEntry(id = "f1", word = "CS siap membantu kakak!", shortcut = "csok", folder = "Kerja", updatedAtMillis = 10L),
            PersonalDictionaryEntry(id = "f2", word = "csresmi2026@gmail.com", shortcut = "", folder = "Kerja", updatedAtMillis = 11L),
            PersonalDictionaryEntry(id = "f3", word = "081234567890", shortcut = "", folder = "num", updatedAtMillis = 12L)
        )
        assertEquals(
            "CS siap membantu kakak!",
            DictionaryEngine.getSuggestions("csok", "", folderedEntries).primaryAutoCorrect
        )
        assertTrue(
            DictionaryEngine.getSuggestions("csres", "", folderedEntries).suggestions.contains("csresmi2026@gmail.com")
        )
        assertTrue(
            DictionaryEngine.getSuggestions("0812", "", folderedEntries).suggestions.contains("081234567890")
        )
        // Deleted personal entries are excluded
        val deletedOnly = listOf(
            PersonalDictionaryEntry(id = "d1", word = "terhapus", shortcut = "th", deleted = true, updatedAtMillis = 20L)
        )
        assertTrue(DictionaryEngine.getSuggestions("th", "", deletedOnly).suggestions.isEmpty())
    }

    @Test
    fun keyboardController_shortcutDoesNotAutoFillOnSpaceEnterNewlineOrPunctuation() {
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

        // Space does not auto-expand
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("k", settings, entries)
        assertEquals(listOf("Terima kasih banyak"), controller.suggestions)
        controller.onSpaceTap(settings, entries)
        assertEquals("tk ", tf.text)

        // Punctuation '.' does not auto-expand
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("k", settings, entries)
        controller.onCharacterTap(".", settings, entries)
        assertEquals("tk tk.", tf.text)

        // Newline key inserts '\n' without auto-expanding shortcut
        controller.onSpaceTap(settings, entries)
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("k", settings, entries)
        controller.onNewlineTap(settings, entries)
        assertEquals("tk tk. tk\n", tf.text)

        // Enter action does not auto-expand shortcut
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("k", settings, entries)
        controller.onEnterTap(settings, entries)
        assertEquals("tk tk. tk\ntk\n", tf.text)

        // Explicit suggestion tap replaces typed shortcut
        controller.onCharacterTap("t", settings, entries)
        controller.onCharacterTap("k", settings, entries)
        controller.onSuggestionTap("Terima kasih banyak", settings, entries)
        assertEquals("tk tk. tk\ntk\nTerima kasih banyak", tf.text)
    }

    @Test
    fun dictionaryEngine_prioritizesShortcutBearingEntriesInIdleAndPrefixPersonalization() {
        val entries = listOf(
            PersonalDictionaryEntry(id = "p1", word = "Kantor biasa", shortcut = "", updatedAtMillis = 500L),
            PersonalDictionaryEntry(id = "p2", word = "Kantor pusat", shortcut = "kp", updatedAtMillis = 100L),
            PersonalDictionaryEntry(id = "p3", word = "Katalog umum", shortcut = "", updatedAtMillis = 600L),
            PersonalDictionaryEntry(id = "p4", word = "Katalog promo", shortcut = "kpr", updatedAtMillis = 100L)
        )

        val idleSuggestions = DictionaryEngine.getSuggestions(
            currentWord = "",
            previousWord = "",
            personalEntries = entries,
            personality = emptyMap(),
            nowMillis = 1_000L
        ).suggestions
        // Tidak sedang mengetik: saran kosong.
        assertTrue(idleSuggestions.isEmpty())

        // Dua huruf belum memunculkan saran kata dari kamus pribadi.
        val twoLetterSuggestions = DictionaryEngine.getSuggestions(
            currentWord = "Ka",
            previousWord = "",
            personalEntries = entries,
            personality = emptyMap(),
            nowMillis = 1_000L
        ).suggestions
        assertFalse(twoLetterSuggestions.contains("Kantor pusat"))
        assertFalse(twoLetterSuggestions.contains("Katalog promo"))

        val prefixSuggestions = DictionaryEngine.getSuggestions(
            currentWord = "Kan",
            previousWord = "",
            personalEntries = entries,
            personality = emptyMap(),
            nowMillis = 1_000L
        ).suggestions
        assertEquals("Kantor pusat", prefixSuggestions[0])
    }

    @Test
    fun keyboardSettings_defaultsDisableBackgroundAutoSync() {
        val defaults = KeyboardSettings()
        assertEquals(false, defaults.floatingModeEnabled)
    }

    @Test
    fun settingsRepository_normalizeFolderPathFlattensSubfoldersAndCanonicalizesPrimaryFolders() {
        assertEquals("Kerja CS", SettingsRepository.normalizeFolderPath("Kerja/CS"))
        assertEquals("Kerja CS", SettingsRepository.normalizeFolderPath("  Kerja \\ CS  "))
        assertEquals("gm", SettingsRepository.normalizeFolderPath("GM"))
        assertEquals("num", SettingsRepository.normalizeFolderPath("Num"))
        assertEquals("prib", SettingsRepository.normalizeFolderPath("PRIB"))
        assertEquals("pw", SettingsRepository.normalizeFolderPath("pw"))
        assertEquals("kred", SettingsRepository.normalizeFolderPath("KRED"))
        assertEquals("bc", SettingsRepository.normalizeFolderPath("bc"))
        assertEquals("balasan cepat", SettingsRepository.normalizeFolderPath("balasan cepat"))
        assertEquals("Keuangan", SettingsRepository.normalizeFolderPath("Keuangan"))
        assertEquals("", SettingsRepository.normalizeFolderPath("///"))
        assertFalse(SettingsRepository.normalizeFolderPath("A/B/C").contains('/'))

        val ordered = SettingsRepository.normalizeOrderedFolders(
            explicitFolders = listOf("Kerja", "PW", "kred"),
            entries = emptyList()
        )
        assertEquals(
            listOf("aut", "kred", "num", "pw", "prib", "bc", "tras", "Kerja"),
            ordered
        )
    }

    @Test
    fun settingsRepository_normalizeWebsiteDomainKeepsOnlyMainDomain() {
        assertEquals("google.com", SettingsRepository.normalizeWebsiteDomain("https://myaccount.google.com/"))
        assertEquals("github.com", SettingsRepository.normalizeWebsiteDomain("https://www.github.com/x?y=1"))
        assertEquals("github.com", SettingsRepository.normalizeWebsiteDomain("github.com"))
        assertEquals("bbc.co.uk", SettingsRepository.normalizeWebsiteDomain("www.bbc.co.uk/news"))
        assertEquals("example.com", SettingsRepository.normalizeWebsiteDomain("user:pw@sub.example.com:8080/a"))
        assertEquals("", SettingsRepository.normalizeWebsiteDomain("   "))
    }

    @Test
    fun dictionaryEngine_kataSandiAndKredensialNeverLeakIdleOrByPrefixOnlyByShortcut() {
        val entries = listOf(
            PersonalDictionaryEntry(id = "pub", word = "Halo dunia", shortcut = "hd", folder = "prib", updatedAtMillis = 1L),
            PersonalDictionaryEntry(id = "pw1", word = "RahasiaSandi!2026", shortcut = "pw1", folder = "pw", updatedAtMillis = 2L),
            PersonalDictionaryEntry(id = "pw_no_sc", word = "SandiTanpaPintasan", shortcut = "", folder = "pw", updatedAtMillis = 3L),
            PersonalDictionaryEntry(id = "cred1", word = "sk-live-secret-9988", shortcut = "apikey", folder = "kred", updatedAtMillis = 4L)
        )
        val highUsage = mapOf(
            "rahasiasandi!2026" to WordPersonalityStat("RahasiaSandi!2026", usageCount = 50, lastUsedMillis = 1000L),
            "sk-live-secret-9988" to WordPersonalityStat("sk-live-secret-9988", usageCount = 50, lastUsedMillis = 1000L)
        )

        // 1. Idle bar MUST NOT contain pw or kred entries
        val idle = DictionaryEngine.getSuggestions("", "", entries, highUsage, 1000L).suggestions
        assertTrue(idle.isEmpty())

        // 2. Typing prefix or exact text of password/credential MUST NOT surface them
        assertTrue(DictionaryEngine.getSuggestions("Rah", "", entries, highUsage, 1000L).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("RahasiaSandi!2026", "", entries, highUsage, 1000L).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("Sandi", "", entries, highUsage, 1000L).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("sk-live", "", entries, highUsage, 1000L).suggestions.isEmpty())

        // 3. Typing their exact shortcut DOES surface them immediately
        val pwMatch = DictionaryEngine.getSuggestions("pw1", "", entries, highUsage, 1000L)
        assertEquals("RahasiaSandi!2026", pwMatch.primaryAutoCorrect)
        assertEquals(listOf("RahasiaSandi!2026"), pwMatch.suggestions)

        val credMatch = DictionaryEngine.getSuggestions("apikey", "", entries, highUsage, 1000L)
        assertEquals("sk-live-secret-9988", credMatch.primaryAutoCorrect)
        assertEquals(listOf("sk-live-secret-9988"), credMatch.suggestions)
    }

    @Test
    fun keyboardController_liveTypedPreviewTracksCharactersIncludingSymbolsAndClearsOnBoundary() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings()
        val entries = listOf(
            PersonalDictionaryEntry(id = "s1", word = "Rahasia!2026", shortcut = "pw", folder = "pw", updatedAtMillis = 1L)
        )
        val controller = KeyboardController(
            TextFieldImeTarget(getValue = { tf }, setValue = { tf = it })
        )

        "{\"k\":\"v\"}".forEach { ch ->
            controller.onCharacterTap(ch.toString(), settings, entries)
        }
        assertEquals("{\"k\":\"v\"}", controller.currentTypedText)

        controller.onBackspaceTap(settings, entries)
        assertEquals("{\"k\":\"v\"", controller.currentTypedText)

        controller.onSpaceTap(settings, entries)
        assertEquals("", controller.currentTypedText)

        // Works in password field too and clears after selecting shortcut suggestion
        controller.configureForEditor(true, com.example.ime.EnterActionType.DONE, settings, entries)
        controller.onCharacterTap("p", settings, entries)
        controller.onCharacterTap("w", settings, entries)
        assertEquals("pw", controller.currentTypedText)
        controller.onSuggestionTap("Rahasia!2026", settings, entries)
        assertEquals("", controller.currentTypedText)
    }

    @Test
    fun keyboardController_personalShortcutsWorkInPasswordAndMaskedFieldsWithoutTrailingSpace() {
        var committedText = ""
        val maskedTarget = object : com.example.ime.ImeTarget {
            override fun getTextBeforeCursor(n: Int): String = ""
            override fun getSelectedText(): String = ""
            override fun commitText(text: String) {
                committedText += text
            }
            override fun deleteBeforeCursor(count: Int) {
                committedText = committedText.dropLast(count.coerceAtMost(committedText.length))
            }
            override fun replaceWordBeforeCursor(oldWordLength: Int, newText: String) {
                committedText = committedText.dropLast(oldWordLength.coerceAtMost(committedText.length)) + newText
            }
            override fun moveCursor(delta: Int, extendSelection: Boolean) {}
            override fun moveCursorVertical(deltaLines: Int) {}
            override fun moveCursorToEdge(toStart: Boolean) {}
            override fun selectAll() {}
            override fun performEnterAction(actionType: com.example.ime.EnterActionType) {}
        }
        val entries = listOf(
            PersonalDictionaryEntry(id = "p1", word = "KunciRahasia#99", shortcut = "kr", updatedAtMillis = 1L),
            PersonalDictionaryEntry(id = "p2", word = "admin@contoh.id", shortcut = ";em", updatedAtMillis = 2L)
        )
        val settings = KeyboardSettings()
        val controller = KeyboardController(maskedTarget)

        // 1. Password field with hidden getTextBeforeCursor()
        controller.configureForEditor(
            isPassword = true,
            actionType = com.example.ime.EnterActionType.DONE,
            settings = settings,
            personalEntries = entries
        )
        assertTrue(controller.suggestions.isEmpty())
        controller.onCharacterTap("k", settings, entries)
        controller.onCharacterTap("r", settings, entries)
        assertTrue(controller.suggestions.contains("KunciRahasia#99"))
        controller.onSuggestionTap("KunciRahasia#99", settings, entries)
        assertEquals("KunciRahasia#99", committedText)

        // 2. Non-password WebView/masked field (empty getTextBeforeCursor) + symbol-prefixed shortcut ";em"
        committedText = ""
        controller.configureForEditor(
            isPassword = false,
            actionType = com.example.ime.EnterActionType.SEARCH,
            settings = settings,
            personalEntries = entries
        )
        controller.onCharacterTap(";", settings, entries)
        controller.onCharacterTap("e", settings, entries)
        controller.onCharacterTap("m", settings, entries)
        assertTrue(controller.suggestions.contains("admin@contoh.id"))
        controller.onSuggestionTap("admin@contoh.id", settings, entries)
        assertEquals("admin@contoh.id", committedText)
    }
}


