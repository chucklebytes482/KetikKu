package com.example

import com.example.data.DictionaryEngine
import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailShortcutTest {
    private fun e(
        id: String,
        word: String,
        folder: String,
        deleted: Boolean = false
    ) = PersonalDictionaryEntry(id = id, word = word, shortcut = "", folder = folder, deleted = deleted)

    @Test
    fun emShowsEmailsFromAutAndKredOnly_sortedAndDeduped() {
        val entries = listOf(
            e("1", "zeta@mail.com\nsite.com", SettingsRepository.FOLDER_A2F),
            e("2", "Alpha@mail.com\nsite.com", SettingsRepository.FOLDER_KREDENSIAL),
            e("3", "alpha@mail.com", SettingsRepository.FOLDER_A2F),
            e("4", "rahasia123", SettingsRepository.FOLDER_KREDENSIAL),
            e("5", "bukan@email", SettingsRepository.FOLDER_A2F),
            e("6", "tr@mail.com", SettingsRepository.FOLDER_SAMPAH),
            e("7", "del@mail.com", SettingsRepository.FOLDER_A2F, deleted = true),
            e("8", "pribadi@mail.com", SettingsRepository.FOLDER_PRIBADI)
        )
        val words = DictionaryEngine.findPagedShortcutWords("em", entries)
        assertEquals(listOf("Alpha@mail.com", "zeta@mail.com"), words)
    }

    @Test
    fun emDoesNotMatchOtherWordsStartingWithEm() {
        val entries = listOf(e("1", "a@mail.com", SettingsRepository.FOLDER_A2F))
        assertEquals(emptyList<String>(), DictionaryEngine.findPagedShortcutWords("emas", entries))
    }
}
