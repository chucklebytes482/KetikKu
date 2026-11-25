package com.example

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GmailRetiredFolderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { File(tmp.root, "gm_test.preferences_pb") }
    )

    private val dictKey = stringPreferencesKey("dictionary_json")
    private val foldersKey = stringPreferencesKey("dictionary_folders_json")

    private fun entryJson(id: String, word: String, folder: String, trashedFrom: String = "") =
        """{"id":"$id","word":"$word","shortcut":"","folder":"$folder","updatedAtMillis":1,""" +
            """"deleted":false,"secretCipher":"","trashedFrom":"$trashedFrom"}"""

    @Test
    fun legacyGmFolderEntriesMoveToTrashAndFolderIsDropped() = runBlocking {
        val store = newStore()
        store.edit { prefs ->
            prefs[dictKey] = "[" +
                entryJson("g1", "alamatgmail", "gm") + "," +
                entryJson("n1", "12345", "num") +
                "]"
            prefs[foldersKey] = """["gm","num"]"""
        }
        val repo = SettingsRepository(store)

        // Migrasi berjalan di onEach; emisi pertama masih data mentah, jadi tunggu sampai gm hilang.
        val list = withTimeout(5_000) {
            repo.dictionaryFlow.first { list -> list.none { it.folder == "gm" } }
        }

        val moved = list.first { it.id == "g1" }
        assertEquals("tras", moved.folder)
        assertEquals("gm", moved.trashedFrom)
        assertEquals("num", list.first { it.id == "n1" }.folder)
        assertFalse(repo.getCurrentFolders().contains("gm"))
        assertTrue(repo.getCurrentFolders().contains("num"))
    }

    @Test
    fun restoreFromLegacyGmTrashGoesToRoot() = runBlocking {
        val store = newStore()
        store.edit { prefs ->
            prefs[dictKey] = "[" + entryJson("g1", "alamatgmail", "gm") + "]"
            prefs[foldersKey] = """["gm"]"""
        }
        val repo = SettingsRepository(store)
        withTimeout(5_000) { repo.dictionaryFlow.first { list -> list.none { it.folder == "gm" } } }

        repo.restoreFromTrash(setOf("g1"))

        val restored = repo.dictionaryFlow.first { list -> list.any { it.id == "g1" && it.folder != "tras" } }
            .first { it.id == "g1" }
        assertEquals("", restored.folder)
        assertEquals("", restored.trashedFrom)
    }

    @Test
    fun replaceDictionaryWithGmEntryPutsItInTrash() = runBlocking {
        val repo = SettingsRepository(newStore())
        val entry = PersonalDictionaryEntry(
            id = "g2",
            word = "backupgm",
            shortcut = "",
            folder = "gm",
            updatedAtMillis = 1L
        )

        val (entries, folders) = repo.replaceDictionaryAndFolders(listOf(entry), listOf("gm", "num"))

        val stored = entries.first { it.id == "g2" }
        assertEquals("tras", stored.folder)
        assertEquals("gm", stored.trashedFrom)
        assertFalse(folders.contains("gm"))
        assertTrue(folders.contains("num"))
    }

    @Test
    fun cannotCreateGmFolderAgain() = runBlocking {
        val repo = SettingsRepository(newStore())
        val before = repo.getCurrentFolders()

        repo.createFolder("gm")
        repo.createFolder("GM")

        assertEquals(before, repo.getCurrentFolders())
        assertFalse(repo.getCurrentFolders().contains("gm"))
    }
}
