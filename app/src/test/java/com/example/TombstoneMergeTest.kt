package com.example

import com.example.data.SettingsRepository
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TombstoneMergeTest {
    @Test
    fun newerTombstoneWinsOverOlderActiveLocal() {
        val local = listOf(
            PersonalDictionaryEntry(id = "d1", word = "rahasia", shortcut = "rh", folder = "prib", updatedAtMillis = 5L)
        )
        val remote = listOf(
            PersonalDictionaryEntry(id = "d1", word = "", shortcut = "", folder = "tras", deleted = true, updatedAtMillis = 9L)
        )
        val merged = SettingsRepository.mergeDictionaryLists(local, remote)
        val entry = merged.single { it.id == "d1" }
        assertTrue(entry.deleted)
        assertEquals("tras", entry.folder)
    }

    @Test
    fun olderRemoteTombstoneDoesNotDeleteNewerLocal() {
        val local = listOf(
            PersonalDictionaryEntry(id = "d2", word = "halo", shortcut = "hl", folder = "prib", updatedAtMillis = 20L)
        )
        val remote = listOf(
            PersonalDictionaryEntry(id = "d2", word = "", shortcut = "", folder = "tras", deleted = true, updatedAtMillis = 3L)
        )
        val merged = SettingsRepository.mergeDictionaryLists(local, remote)
        assertTrue(!merged.single { it.id == "d2" }.deleted)
    }
}
