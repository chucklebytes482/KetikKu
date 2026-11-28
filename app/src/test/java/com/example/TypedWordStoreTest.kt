package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.TypedWordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TypedWordStoreTest {

    private lateinit var store: TypedWordStore

    @Before
    fun setUp() {
        store = TypedWordStore.get(ApplicationProvider.getApplicationContext())
        store.writableDatabase.execSQL("DELETE FROM typed_words")
    }

    private fun rowCount(): Int = store.readableDatabase.rawQuery("SELECT COUNT(*) FROM typed_words", null).use { c ->
        c.moveToFirst()
        c.getInt(0)
    }

    private fun containsWord(word: String): Boolean =
        store.readableDatabase.rawQuery("SELECT 1 FROM typed_words WHERE word = ?", arrayOf(word)).use { it.moveToFirst() }

    /** Kata 8 huruf unik dari indeks, hanya huruf a-z. */
    private fun wordFor(index: Int): String {
        var n = index
        val sb = StringBuilder("kata")
        repeat(4) {
            sb.append('a' + (n % 26))
            n /= 26
        }
        return sb.toString()
    }

    @Test
    fun normalize_rejectsShortNonLetterAndLongWords() {
        assertNull(TypedWordStore.normalize("pagi"))
        assertNull(TypedWordStore.normalize("selamat"))           // 7 huruf
        assertNull(TypedWordStore.normalize("abcdefg1"))          // ada angka
        assertNull(TypedWordStore.normalize("sel amat!"))         // spasi dan tanda baca
        assertNull(TypedWordStore.normalize("a".repeat(31)))
        assertEquals("abcdefgh", TypedWordStore.normalize("AbcdefGH"))   // 8 huruf, dinormalisasi huruf kecil
        assertEquals("sejahtera", TypedWordStore.normalize("  Sejahtera "))
    }

    @Test
    fun suggestPrefix_requiresThreeLettersAndOrdersByUses() {
        store.upsert("selamatpagi", 1L)
        store.upsert("selamatpagi", 2L)
        store.upsert("selamatpagi", 3L)
        store.upsert("selamatsiang", 4L)
        store.upsert("sejahterakan", 5L)

        assertTrue(store.suggestPrefix("se").isEmpty())
        assertTrue(store.suggestPrefix("sel1").isEmpty())

        val result = store.suggestPrefix("sel")
        assertEquals(listOf("selamatpagi", "selamatsiang"), result.keys.toList())
        assertEquals(3, result["selamatpagi"])
        assertEquals(1, result["selamatsiang"])
        assertFalse(result.containsKey("sejahterakan"))
    }

    @Test
    fun evictionKeepsAtMost500RowsAndDropsLeastUsedFirst() {
        for (i in 0 until TypedWordStore.MAX_ROWS) {
            store.upsert(wordFor(i), nowMillis = i.toLong())
        }
        assertEquals(TypedWordStore.MAX_ROWS, rowCount())

        // Kata indeks 1 dipakai lebih sering, harus bertahan.
        repeat(5) { store.upsert(wordFor(1), nowMillis = 1_000L) }

        store.upsert("pembandingbaru", nowMillis = 2_000L)

        assertEquals(TypedWordStore.MAX_ROWS, rowCount())
        assertFalse(containsWord(wordFor(0)))          // uses 1, paling lama tidak dipakai
        assertTrue(containsWord(wordFor(1)))           // uses 6
        assertTrue(containsWord("pembandingbaru"))
    }
}
