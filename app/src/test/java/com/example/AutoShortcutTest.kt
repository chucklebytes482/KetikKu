package com.example

import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoShortcutTest {
    private fun e(id: String, shortcut: String, folder: String, deleted: Boolean = false) =
        PersonalDictionaryEntry(id = id, word = id, shortcut = shortcut, folder = folder, deleted = deleted)

    @Test
    fun gmShortcutUsesFirstCharBeforeAtPlusGm() {
        assertEquals("gma", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdul@gmail.com", emptyList(), null))
        assertEquals("gmc", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "chucklebytes482@gmail.com", emptyList(), null))
    }

    @Test
    fun numShortcutUsesFirstFourDigits() {
        assertEquals("num0812", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_NOMOR, "081234567890", emptyList(), null))
    }

    @Test
    fun pwShortcutUsesPwPlusFirstChar() {
        assertEquals("pwg", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github", emptyList(), null))
    }

    @Test
    fun kredShortcutUsesFirstTwoChars() {
        assertEquals("kredbc", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "BCA", emptyList(), null))
    }

    @Test
    fun autShortcutUsesEmailAndWebsiteParts() {
        assertEquals(
            "autbugi",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\ngithub.com", emptyList(), null)
        )
        assertEquals(
            "autbugi",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\nhttps://www.github.com/x", emptyList(), null)
        )
    }

    @Test
    fun autWithoutWebsiteGetsNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\n", emptyList(), null))
    }

    @Test
    fun collisionGetsNumberSuffix() {
        val others = listOf(e("x", "gma", SettingsRepository.FOLDER_GMAIL))
        assertEquals("gma2", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdul@gmail.com", others, null))
    }

    @Test
    fun trashEntriesDoNotBlockShortcuts() {
        val others = listOf(e("x", "gma", SettingsRepository.FOLDER_SAMPAH))
        assertEquals("gma", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdul@gmail.com", others, null))
    }

    @Test
    fun foldersWithoutAutoRuleGetNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_PRIBADI, "Halo", emptyList(), null))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_BALASAN_CEPAT, "Halo", emptyList(), null))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_SAMPAH, "Halo", emptyList(), null))
    }
}
