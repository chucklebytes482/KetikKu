package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.EntryUsageStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryUsageStoreTest {

    private lateinit var store: EntryUsageStore

    @Before
    fun setUp() {
        store = EntryUsageStore.get(ApplicationProvider.getApplicationContext())
        store.writableDatabase.execSQL("DELETE FROM entry_usage")
    }

    @Test
    fun usesOf_countsEachRecordedTapPerEntry() {
        assertEquals(0, store.usesOf("a2f-1"))
        store.record("a2f-1")
        store.record("a2f-1")
        store.record("a2f-2")
        assertEquals(2, store.usesOf("a2f-1"))
        assertEquals(1, store.usesOf("a2f-2"))
        assertEquals(0, store.usesOf("  "))
    }

    @Test
    fun upsert_persistsCountInSqlite() {
        store.upsert("a2f-9", 1L)
        store.upsert("a2f-9", 2L)
        store.readableDatabase.rawQuery(
            "SELECT uses, last_used FROM entry_usage WHERE entry_id = ?", arrayOf("a2f-9")
        ).use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertEquals(2L, c.getLong(1))
        }
    }
}
