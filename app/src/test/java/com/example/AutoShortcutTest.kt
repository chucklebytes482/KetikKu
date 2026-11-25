package com.example

import com.example.data.DictionaryEngine
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
    fun pwShortcutUsesFirstCharPlusPw() {
        assertEquals("pwg", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github", emptyList(), null))
    }

    @Test
    fun numFolderHasNoAutoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_NOMOR, "081234567890", emptyList(), null))
        assertFalse(SettingsRepository.isShortcutLockedFolder(SettingsRepository.FOLDER_NOMOR))
    }

    @Test
    fun lockedFoldersArePwAutKred() {
        listOf(SettingsRepository.FOLDER_KATA_SANDI,
            SettingsRepository.FOLDER_A2F, SettingsRepository.FOLDER_KREDENSIAL).forEach {
            assertTrue(it, SettingsRepository.isShortcutLockedFolder(it))
        }
    }

    @Test
    fun trashRuleAllowsAllExceptLockedOriginsWithoutDuplicate() {
        fun trashed(id: String, word: String, from: String) = PersonalDictionaryEntry(
            id = id, word = word, shortcut = "", folder = SettingsRepository.FOLDER_SAMPAH, trashedFrom = from
        )
        val a2fNoDup = trashed("a", "x@y.com\nsite.com", SettingsRepository.FOLDER_A2F)
        assertFalse(SettingsRepository.canPermanentlyDeleteFromTrash(a2fNoDup, listOf(a2fNoDup)))
        val pwNoDup = trashed("p", "Github", SettingsRepository.FOLDER_KATA_SANDI)
        assertFalse(SettingsRepository.canPermanentlyDeleteFromTrash(pwNoDup, listOf(pwNoDup)))
        val kredNoDup = trashed("k", "BCA", SettingsRepository.FOLDER_KREDENSIAL)
        assertFalse(SettingsRepository.canPermanentlyDeleteFromTrash(kredNoDup, listOf(kredNoDup)))
        val a2fActive = PersonalDictionaryEntry(
            id = "a2", word = "x@y.com\nsite.com", shortcut = "autsite.com", folder = SettingsRepository.FOLDER_A2F
        )
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(a2fNoDup, listOf(a2fNoDup, a2fActive)))
        val longWord = trashed("b", "kalimat panjang sekali", SettingsRepository.FOLDER_PRIBADI)
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(longWord, listOf(longWord)))
        val shortWord = trashed("c", "abc", SettingsRepository.FOLDER_PRIBADI)
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(shortWord, listOf(shortWord)))
    }

    @Test
    fun pwShortcutUsesPwPlusFirstChar() {
        assertEquals("pwg", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github", emptyList(), null))
    }

    @Test
    fun kredShortcutUsesWebsiteMainDomain() {
        assertEquals(
            "kredgithub.com",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "budi\ngithub.com", emptyList(), null)
        )
        assertEquals(
            "kredgithub.com",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "budi\nhttps://www.github.com/login", emptyList(), null)
        )
    }

    @Test
    fun kredWithoutWebsiteGetsNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "BCA", emptyList(), null))
    }

    @Test
    fun kredKeywordShowsActiveCredentialsOnly() {
        val kred1 = PersonalDictionaryEntry(id = "k1", word = "budi\ngithub.com", shortcut = "kredgithub.com",
            folder = SettingsRepository.FOLDER_KREDENSIAL, updatedAtMillis = 1L)
        val kred2 = PersonalDictionaryEntry(id = "k2", word = "ani\nbca.co.id", shortcut = "kredbca.co.id",
            folder = SettingsRepository.FOLDER_KREDENSIAL, updatedAtMillis = 2L)
        val trashedKred = PersonalDictionaryEntry(id = "k3", word = "old\nold.com", shortcut = "",
            folder = SettingsRepository.FOLDER_KREDENSIAL, deleted = true, updatedAtMillis = 3L)
        val result = DictionaryEngine.findShortcutMatches("kred", listOf(kred1, kred2, trashedKred))
        assertEquals(listOf("ani", "budi"), result)
    }

    @Test
    fun threeLettersOfPersonalWordDoNotShowKredential() {
        val pribadi = PersonalDictionaryEntry(id = "p1", word = "kredit", shortcut = "",
            folder = SettingsRepository.FOLDER_PRIBADI)
        assertEquals(emptyList<String>(), DictionaryEngine.findShortcutMatches("kre", listOf(pribadi)))
    }

    @Test
    fun suggestionsNeedThreeCharsBeforePrefixMatch() {
        val pribadi = PersonalDictionaryEntry(id = "w1", word = "selamat", shortcut = "",
            folder = SettingsRepository.FOLDER_PRIBADI)
        assertTrue(DictionaryEngine.getSuggestions("se", "", listOf(pribadi)).suggestions.isEmpty())
        assertTrue(DictionaryEngine.getSuggestions("sel", "", listOf(pribadi)).suggestions.contains("selamat"))
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
        val others = listOf(e("x", "pwg", SettingsRepository.FOLDER_KATA_SANDI))
        assertEquals("pwg2", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github", others, null))
    }

    @Test
    fun trashEntriesDoNotBlockShortcuts() {
        val others = listOf(e("x", "pwg", SettingsRepository.FOLDER_SAMPAH))
        assertEquals("pwg", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github", others, null))
    }

    @Test
    fun foldersWithoutAutoRuleGetNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_PRIBADI, "Halo", emptyList(), null))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_BALASAN_CEPAT, "Halo", emptyList(), null))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_SAMPAH, "Halo", emptyList(), null))
    }

    private fun site(folder: String, website: String) =
        PersonalDictionaryEntry(id = website, word = "kata\n$website", folder = folder)

    @Test
    fun mainDomainSortKeyUsesLabelWithoutTld() {
        assertEquals("google", SettingsRepository.mainDomainSortKey(site(SettingsRepository.FOLDER_KREDENSIAL, "google.com")))
        assertEquals("google", SettingsRepository.mainDomainSortKey(site(SettingsRepository.FOLDER_A2F, "https://myaccount.google.com/")))
        assertEquals("bbc", SettingsRepository.mainDomainSortKey(site(SettingsRepository.FOLDER_KREDENSIAL, "www.bbc.co.uk/news")))
        assertEquals("", SettingsRepository.mainDomainSortKey(site(SettingsRepository.FOLDER_PRIBADI, "google.com")))
        assertEquals("", SettingsRepository.mainDomainSortKey(site(SettingsRepository.FOLDER_KREDENSIAL, "")))
    }
}
