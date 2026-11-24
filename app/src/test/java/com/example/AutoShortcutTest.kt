package com.example

import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoShortcutTest {
    private fun e(id: String, shortcut: String, folder: String, deleted: Boolean = false) =
        PersonalDictionaryEntry(id = id, word = id, shortcut = shortcut, folder = folder, deleted = deleted)

    @Test
    fun gmailShortcutUsesFirstCharBeforeAtPlusGm() {
        assertEquals("agm", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdulkarim94786@gmail.com", emptyList(), null))
        assertEquals("cgm", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "chucklebytes482@gmail.com", emptyList(), null))
    }

    @Test
    fun nomorShortcutUsesLastThreeDigits() {
        assertEquals("890", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_NOMOR, "081234567890", emptyList(), null))
    }

    @Test
    fun kataSandiShortcutUsesFirstCharPlusKs() {
        assertEquals("gks", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github", emptyList(), null))
    }

    @Test
    fun collisionGetsNumberSuffix() {
        val others = listOf(e("x", "agm", SettingsRepository.FOLDER_GMAIL))
        assertEquals("agm2", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdulkarim94786@gmail.com", others, null))
    }

    @Test
    fun trashEntriesDoNotBlockShortcuts() {
        val others = listOf(e("x", "agm", SettingsRepository.FOLDER_SAMPAH))
        assertEquals("agm", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdulkarim94786@gmail.com", others, null))
    }

    @Test
    fun foldersWithoutAutoRuleGetNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_PRIBADI, "Halo", emptyList(), null))
    }
}
