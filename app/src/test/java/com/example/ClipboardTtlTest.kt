package com.example

import com.example.data.ClipboardMemory
import com.example.data.InMemoryClipboardBackingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardTtlTest {
    @Test
    fun itemOlderThanOneHourIsDroppedFromPreviewsAndLookups() {
        val store = InMemoryClipboardBackingStore()
        val now = System.currentTimeMillis()
        val old = store.saveClipboardText("teks lama", now - ClipboardMemory.TTL_MILLIS - 1_000L)!!
        store.saveClipboardText("teks baru", now)!!

        val previews = store.getPreviews()
        assertEquals(1, previews.size)
        assertEquals("teks baru", store.getLatestFullText())
        assertNull(store.getFullText(old.id))
    }

    @Test
    fun itemWithinOneHourIsKept() {
        val store = InMemoryClipboardBackingStore()
        val now = System.currentTimeMillis()
        val item = store.saveClipboardText("masih segar", now - ClipboardMemory.TTL_MILLIS + 60_000L)!!
        assertEquals("masih segar", store.getFullText(item.id))
    }
}
