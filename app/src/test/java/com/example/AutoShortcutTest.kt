package com.example

import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoShortcutTest {
    private fun e(id: String, shortcut: String, folder: String, deleted: Boolean = false) =
        PersonalDictionaryEntry(id = id, word = id, shortcut = shortcut, folder = folder, deleted = deleted)

    @Test
    fun gmShortcutUsesFirstTwoCharsBeforeAtPlusGm() {
        assertEquals("gmab", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdul@gmail.com", emptyList(), null))
        assertEquals("gmch", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "chucklebytes482@gmail.com", emptyList(), null))
    }

    @Test
    fun numFolderHasNoAutoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_NOMOR, "081234567890", emptyList(), null))
        assertFalse(SettingsRepository.isShortcutLockedFolder(SettingsRepository.FOLDER_NOMOR))
    }

    @Test
    fun lockedFoldersAreGmPwAutKred() {
        listOf(SettingsRepository.FOLDER_GMAIL, SettingsRepository.FOLDER_KATA_SANDI,
            SettingsRepository.FOLDER_A2F, SettingsRepository.FOLDER_KREDENSIAL).forEach {
            assertTrue(it, SettingsRepository.isShortcutLockedFolder(it))
        }
    }

    @Test
    fun trashedA2fIsNeverPermanentlyDeletable() {
        val a2fTrashed = PersonalDictionaryEntry(
            id = "a", word = "x@y.com\nsite.com", shortcut = "", folder = SettingsRepository.FOLDER_SAMPAH,
            trashedFrom = SettingsRepository.FOLDER_A2F
        )
        assertFalse(SettingsRepository.canPermanentlyDeleteFromTrash(a2fTrashed, listOf(a2fTrashed)))
        val shortWord = PersonalDictionaryEntry(
            id = "b", word = "abc", shortcut = "", folder = SettingsRepository.FOLDER_SAMPAH,
            trashedFrom = SettingsRepository.FOLDER_PRIBADI
        )
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(shortWord, listOf(shortWord)))
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
    fun autShortcutUsesWebsiteMainDomain() {
        assertEquals(
            "autgoogle.com",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\ngoogle.com", emptyList(), null)
        )
        assertEquals(
            "autgoogle.com",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\nhttps://myaccount.google.com/x", emptyList(), null)
        )
    }

    @Test
    fun autAccountsWithSameDomainShareShortcut() {
        val first = SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\ngoogle.com", emptyList(), null)
        val second = SettingsRepository.autoShortcut(
            SettingsRepository.FOLDER_A2F,
            "ani@y.com\naccounts.google.com",
            listOf(e("x", first, SettingsRepository.FOLDER_A2F)),
            null
        )
        assertEquals(first, second)
        assertEquals("autgoogle.com", second)
    }

    @Test
    fun autWithoutWebsiteGetsNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\n", emptyList(), null))
    }

    @Test
    fun shortA2fEmailTruncatesWithEllipsis() {
        assertEquals("budi@x.com", SettingsRepository.shortA2fEmail("budi@x.com"))
        val long = SettingsRepository.shortA2fEmail("penggunapanjang2026@gmail.com")
        assertEquals(16, long.length)
        assertTrue(long.endsWith("\u2026"))
    }

    @Test
    fun collisionGetsNumberSuffix() {
        val others = listOf(e("x", "gmab", SettingsRepository.FOLDER_GMAIL))
        assertEquals("gmab2", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdul@gmail.com", others, null))
    }

    @Test
    fun trashEntriesDoNotBlockShortcuts() {
        val others = listOf(e("x", "gmab", SettingsRepository.FOLDER_SAMPAH))
        assertEquals("gmab", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_GMAIL, "abdul@gmail.com", others, null))
    }

    @Test
    fun foldersWithoutAutoRuleGetNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_PRIBADI, "Halo", emptyList(), null))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_BALASAN_CEPAT, "Halo", emptyList(), null))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_SAMPAH, "Halo", emptyList(), null))
    }
}
