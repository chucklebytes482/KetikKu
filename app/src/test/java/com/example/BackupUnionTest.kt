package com.example

import com.example.data.unionByIdNewestWins
import com.example.model.PersonalDictionaryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupUnionTest {
    private fun e(id: String, word: String, ts: Long, deleted: Boolean = false) = PersonalDictionaryEntry(
        id = id, word = word, shortcut = id, folder = "", updatedAtMillis = ts, deleted = deleted, secretCipher = ""
    )

    @Test
    fun entryMissingFromIncomingIsKept() {
        val incoming = listOf(e("a", "A", 2))
        val existing = listOf(e("a", "A", 1), e("b", "B", 1))
        val merged = unionByIdNewestWins(incoming, existing)
        assertEquals(setOf("a", "b"), merged.map { it.id }.toSet())
    }

    @Test
    fun newerVersionWinsPerId() {
        val merged = unionByIdNewestWins(
            listOf(e("a", "old", 1)), listOf(e("a", "new", 5))
        )
        assertEquals("new", merged.single().word)
    }

    @Test
    fun newerTombstoneDeletesAndIsKept() {
        val merged = unionByIdNewestWins(
            listOf(e("a", "A", 9, deleted = true)), listOf(e("a", "A", 3))
        )
        assertEquals(true, merged.single().deleted)
    }

    @Test
    fun tieKeepsPrimary() {
        val merged = unionByIdNewestWins(
            listOf(e("a", "incoming", 4)), listOf(e("a", "file", 4))
        )
        assertEquals("incoming", merged.single().word)
    }
}
