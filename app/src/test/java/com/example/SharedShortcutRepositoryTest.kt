package com.example

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedShortcutRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun editingA2fWebsiteKeepsAccountAndOldPwEntriesShareShortcut() = runTest {
        val dir = temporaryFolder.newFolder()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            dir.resolve("dictionary.preferences_pb")
        }
        dataStore.edit {
            it[stringPreferencesKey("dictionary_json")] = """
                [
                    {"id":"aut-1","word":"user@example.test\nold.example","folder":"aut","shortcut":"autold","secretCipher":"opaque-test-value","updatedAtMillis":1},
                    {"id":"pw-1","word":"Github","folder":"pw","shortcut":"pwg","updatedAtMillis":1},
                    {"id":"pw-2","word":"Google","folder":"pw","shortcut":"pwg2","updatedAtMillis":2}
                ]
            """.trimIndent()
        }
        val repository = SettingsRepository(dataStore)
        repository.upsertDictionaryEntry("", "", "id-ID", "aut-1", "aut", email = "ignored@example.test",
            website = "https://login.new.example/path")
        val entries = repository.dictionaryFlow.first { list -> list.any { it.id == "pw-2" && it.shortcut == "pwg" } }
        val account = entries.single { it.id == "aut-1" }
        assertEquals("user@example.test\nnew.example", account.word)
        assertEquals("autnew", account.shortcut)
        assertEquals("opaque-test-value", account.secretCipher)
        assertTrue(account.updatedAtMillis > 1)
        assertEquals(listOf("pwg", "pwg"), entries.filter { it.folder == "pw" }.map { it.shortcut })
    }
}
