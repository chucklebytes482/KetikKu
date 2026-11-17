package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.DictionaryEngine
import com.example.data.KeyPoint
import com.example.data.SettingsRepository
import com.example.ime.KeyboardController
import com.example.ime.ShiftState
import com.example.ime.TextFieldImeTarget
import com.example.model.KeyboardLanguage
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardEngineUnitTest {

    @Test
    fun dictionaryEngine_suggestsAndAutoCorrectsIndonesian() {
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
        assertEquals("tidak", typoResult.primaryAutoCorrect)
    }

    @Test
    fun dictionaryEngine_decodesGlidePathToExpectedWord() {
        // Approximate path across d -> a -> n on QWERTY
        val path = listOf(
            KeyPoint(0.26f, 0.50f), // d
            KeyPoint(0.16f, 0.50f), // s/a
            KeyPoint(0.10f, 0.50f), // a
            KeyPoint(0.35f, 0.68f), // c/v/b
            KeyPoint(0.55f, 0.83f)  // n
        )
        val matches = DictionaryEngine.decodeGlidePath(
            pathPoints = path,
            language = KeyboardLanguage.INDONESIAN,
            personalEntries = emptyList()
        )
        assertTrue("Expected 'dan' in glide matches: $matches", matches.contains("dan"))
    }

    @Test
    fun keyboardController_autoCapitalizesAutoCorrectsAndMovesCursor() {
        var tf = TextFieldValue("")
        val settings = KeyboardSettings(
            autoCapitalizeEnabled = true,
            autoCorrectEnabled = true,
            doubleSpacePeriodEnabled = true
        )
        val controller = KeyboardController(
            TextFieldImeTarget(
                getValue = { tf },
                setValue = { tf = it }
            )
        )

        assertEquals(ShiftState.SHIFTED, controller.shiftState)
        controller.onCharacterTap("s", settings, emptyList())
        controller.onCharacterTap("a", settings, emptyList())
        controller.onCharacterTap("y", settings, emptyList())
        controller.onCharacterTap("a", settings, emptyList())
        assertEquals("Saya", tf.text)
        assertEquals(ShiftState.OFF, controller.shiftState)

        // Space + swipe delete + cursor trackpad
        controller.onSpaceTap(settings, emptyList())
        assertEquals("Saya ", tf.text)

        controller.onSpacebarTrackpadStep(-2)
        assertEquals(TextRange(3), tf.selection)
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
}
