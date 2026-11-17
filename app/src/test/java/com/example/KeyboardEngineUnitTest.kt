package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.FontStyleConverter
import com.example.data.SettingsRepository
import com.example.ime.KeyboardController
import com.example.ime.ShiftState
import com.example.ime.TextFieldImeTarget
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEngineUnitTest {

    @Test
    fun dictionaryEngine_suggestsPersonalShortcutWithoutTypoAutoCorrect() {
        val entries = listOf(
            PersonalDictionaryEntry(
                id = "1",
                word = "Hormat kami, Budi",
                shortcut = "ttd",
                language = KeyboardLanguage.INDONESIAN.code,
                updatedAtMillis = 100L
            )
        )
        val shortcutResult = DictionaryEngine.getSuggestions(
            currentWord = "ttd",
            previousWord = "",
            language = KeyboardLanguage.INDONESIAN,
            personalEntries = entries
        )
        assertEquals("Hormat kami, Budi", shortcutResult.primaryAutoCorrect)

        val typoResult = DictionaryEngine.getSuggestions(
            currentWord = "tidk",
            previousWord = "saya",
            language = KeyboardLanguage.INDONESIAN,
            personalEntries = entries
        )
        assertNull(typoResult.primaryAutoCorrect)
        assertTrue("Expected 'tidak' in suggestions: ${typoResult.suggestions}", typoResult.suggestions.contains("tidak"))
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
    fun fontStyleConverter_roundTripsAndKeyboardControllerHandlesArialItalic() {
        val italic = FontStyleConverter.toArialItalic("Halo Dunia 123!")
        assertTrue(FontStyleConverter.containsArialItalic(italic))
        assertEquals("Halo Dunia 123!", FontStyleConverter.fromArialItalic(italic))

        var tf = TextFieldValue("")
        val settings = KeyboardSettings(
            arialItalicEnabled = true
        )
        val entries = listOf(
            PersonalDictionaryEntry(
                id = "1",
                word = "Hormat kami",
                shortcut = "ttd",
                language = KeyboardLanguage.INDONESIAN.code,
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
        assertEquals(FontStyleConverter.toArialItalic("ttd"), tf.text)

        controller.onSpaceTap(settings, entries)
        assertEquals(FontStyleConverter.toArialItalic("Hormat kami") + " ", tf.text)

        // Delete trailing space + last italic letter cleanly in one backspace each
        controller.onBackspaceTap(settings, entries)
        controller.onBackspaceTap(settings, entries)
        assertEquals(FontStyleConverter.toArialItalic("Hormat kam"), tf.text)

        // Selection toggle helper converts between plain and Arial Italic
        tf = TextFieldValue("Salam", selection = TextRange(0, 5))
        controller.convertSelectionToArialItalic(enableItalic = true)
        assertEquals(FontStyleConverter.toArialItalic("Salam"), tf.text)
    }
}
