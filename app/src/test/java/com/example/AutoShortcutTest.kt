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
        assertEquals("pwg", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Github"))
    }

    @Test
    fun numFolderHasNoAutoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_NOMOR, "081234567890"))
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
        // aut tidak pernah bisa dihapus permanen, walaupun ada entri aktif yang sama.
        val a2fActive = PersonalDictionaryEntry(
            id = "a2", word = "x@y.com\nsite.com", shortcut = "autsite", folder = SettingsRepository.FOLDER_A2F
        )
        assertFalse(SettingsRepository.canPermanentlyDeleteFromTrash(a2fNoDup, listOf(a2fNoDup, a2fActive)))
        // pw cocok lewat kata/frasa pada entri aktif.
        val pwActive = PersonalDictionaryEntry(id = "pa", word = "Github", shortcut = "pwg", folder = SettingsRepository.FOLDER_KATA_SANDI)
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(pwNoDup, listOf(pwNoDup, pwActive)))
        val longWord = trashed("b", "kalimat panjang sekali", SettingsRepository.FOLDER_PRIBADI)
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(longWord, listOf(longWord)))
        val shortWord = trashed("c", "abc", SettingsRepository.FOLDER_PRIBADI)
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(shortWord, listOf(shortWord)))
    }

    @Test
    fun plainWordDuplicateIsCheckedPerFolder() {
        fun trashed(id: String, word: String, from: String) = PersonalDictionaryEntry(
            id = id, word = word, shortcut = "", folder = SettingsRepository.FOLDER_SAMPAH, trashedFrom = from
        )
        val pwTrash = trashed("p", "Github", SettingsRepository.FOLDER_KATA_SANDI)
        val otherFolder = PersonalDictionaryEntry(id = "x", word = "Github", shortcut = "", folder = SettingsRepository.FOLDER_PRIBADI)
        // Kata sama di folder lain bukan duplikat untuk folder pw.
        assertFalse(SettingsRepository.canPermanentlyDeleteFromTrash(pwTrash, listOf(pwTrash, otherFolder)))
        val sameFolder = PersonalDictionaryEntry(id = "y", word = "Github", shortcut = "pwg", folder = SettingsRepository.FOLDER_KATA_SANDI)
        assertTrue(SettingsRepository.canPermanentlyDeleteFromTrash(pwTrash, listOf(pwTrash, sameFolder)))
    }

    @Test
    fun kredShortcutUsesWebsiteMainDomain() {
        assertEquals(
            "kredgithub",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "budi\ngithub.com")
        )
        assertEquals(
            "kredgithub",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "budi\nhttps://www.github.com/login")
        )
    }

    @Test
    fun kredAndAutSharedMainDomainUsesSameShortcutPrefix() {
        assertEquals("kredbca", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "ani\nbca.co.id"))
        assertEquals("autbbc", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "x@y.com\nwww.bbc.co.uk"))
    }

    @Test
    fun kredWithoutWebsiteGetsNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KREDENSIAL, "BCA"))
    }

    @Test
    fun kredKeywordShowsActiveCredentialsOnly() {
        val kred1 = PersonalDictionaryEntry(id = "k1", word = "budi\ngithub.com", shortcut = "kredgithub",
            folder = SettingsRepository.FOLDER_KREDENSIAL, updatedAtMillis = 1L)
        val kred2 = PersonalDictionaryEntry(id = "k2", word = "ani\nbca.co.id", shortcut = "kredbca",
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
            "autgoogle",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\ngoogle.com")
        )
        assertEquals(
            "autgoogle",
            SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\nhttps://myaccount.google.com/x")
        )
    }

    @Test
    fun autAccountsWithSameDomainShareShortcut() {
        val first = SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\ngoogle.com")
        val second = SettingsRepository.autoShortcut(
            SettingsRepository.FOLDER_A2F,
            "ani@y.com\naccounts.google.com"
        )
        assertEquals(first, second)
        assertEquals("autgoogle", second)
    }

    @Test
    fun autWithoutWebsiteGetsNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_A2F, "budi@x.com\n"))
    }

    @Test
    fun shortA2fEmailTruncatesWithEllipsis() {
        assertEquals("budi@x.com", SettingsRepository.shortA2fEmail("budi@x.com"))
        val long = SettingsRepository.shortA2fEmail("penggunapanjang2026@gmail.com")
        assertEquals(16, long.length)
        assertTrue(long.endsWith("\u2026"))
    }

    private fun suggest(word: String, vararg entries: PersonalDictionaryEntry) =
        DictionaryEngine.getSuggestions(word, "", entries.toList()).suggestions

    @Test
    fun emailAndWebsiteFromAutAppearAsSuggestionsAfterThreeChars() {
        val aut = PersonalDictionaryEntry(id = "a1", word = "budi@gmail.com\ngoogle.com", shortcut = "autgoogle",
            folder = SettingsRepository.FOLDER_A2F, secretCipher = "x")
        assertTrue(suggest("goo", aut).contains("google.com"))
        assertTrue(suggest("bud", aut).contains("budi@gmail.com"))
        assertTrue(suggest("go", aut).isEmpty())
    }

    @Test
    fun websiteFromKredAppearsButPasswordDoesNot() {
        val kred = PersonalDictionaryEntry(id = "k1", word = "rahasia123\ngithub.com", shortcut = "kredgithub",
            folder = SettingsRepository.FOLDER_KREDENSIAL)
        assertTrue(suggest("git", kred).contains("github.com"))
        assertTrue(suggest("rah", kred).isEmpty())
    }

    @Test
    fun pwAndTrashContactsNeverSuggested() {
        val pw = PersonalDictionaryEntry(id = "p1", word = "Github", shortcut = "pwg", folder = SettingsRepository.FOLDER_KATA_SANDI)
        val trashedAut = PersonalDictionaryEntry(id = "a2", word = "zed@x.com\nzedsite.com", shortcut = "",
            folder = SettingsRepository.FOLDER_SAMPAH, trashedFrom = SettingsRepository.FOLDER_A2F)
        assertTrue(suggest("git", pw).isEmpty())
        assertTrue(suggest("zed", trashedAut).isEmpty())
    }

    @Test
    fun pwShortcutShowsAllMatchingInitialsIncludingLegacyNumberedEntries() {
        val entries = (1..7).map { index ->
            e("pw$index", if (index == 1) "pwg" else "pwg$index", SettingsRepository.FOLDER_KATA_SANDI)
                .copy(word = "Gambar $index", updatedAtMillis = index.toLong())
        }
        val excluded = listOf(
            entries.first().copy(id = "deleted", word = "Gugur", deleted = true),
            entries.first().copy(id = "trash", word = "Gugur", folder = SettingsRepository.FOLDER_SAMPAH),
            entries.first().copy(id = "other", word = "Admin", shortcut = "pwa")
        )
        assertEquals((7 downTo 1).map { "Gambar $it" }, DictionaryEngine.findPagedShortcutWords("PWG", entries + excluded))
        assertEquals("pwg", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_KATA_SANDI, "Gambar 2"))
        assertTrue(DictionaryEngine.findPagedShortcutWords("pw", entries).isEmpty())
    }

    @Test
    fun domainShortcutsReturnEveryMatchingKredAndA2fAccount() {
        val kred = (1..7).map { index ->
            e("k$index", "kredgithub$index", SettingsRepository.FOLDER_KREDENSIAL)
                .copy(word = "akun $index\nlogin.github.com", updatedAtMillis = index.toLong())
        }
        val a2f = (1..7).map { index ->
            e("a$index", "autgithub", SettingsRepository.FOLDER_A2F)
                .copy(word = "user$index@example.test\ngithub.com", secretCipher = "synthetic", updatedAtMillis = index.toLong())
        }
        val words = (7 downTo 1).map { "akun $it" }
        assertEquals(words, DictionaryEngine.findPagedShortcutWords("kredgithub", kred + a2f))
        assertEquals(words, DictionaryEngine.findPagedShortcutWords("kred", kred + a2f))
        assertEquals((7 downTo 1).map { "a$it" }, DictionaryEngine.findA2fEntries("autgithub", kred + a2f).map { it.id })
        assertTrue(DictionaryEngine.findPagedShortcutWords("kredgit", kred).isEmpty())
    }

    @Test
    fun foldersWithoutAutoRuleGetNoShortcut() {
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_PRIBADI, "Halo"))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_BALASAN_CEPAT, "Halo"))
        assertEquals("", SettingsRepository.autoShortcut(SettingsRepository.FOLDER_SAMPAH, "Halo"))
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
