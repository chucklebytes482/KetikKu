package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.ClipboardItem
import java.security.MessageDigest
import java.util.UUID

object TextChunker {
    const val DIRECT_PASTE_MAX_CHARS = 10_000
    const val CHUNK_PASTE_SIZE = 4_000
    const val PREVIEW_MAX_CHARS = 200

    fun trimmedBounds(text: CharSequence): Pair<Int, Int> {
        var start = 0
        var end = text.length
        while (start < end && text[start].isWhitespace()) {
            start++
        }
        while (end > start && text[end - 1].isWhitespace()) {
            end--
        }
        return start to end
    }

    fun isCombiningOrJoinerCodePoint(cp: Int): Boolean {
        val type = Character.getType(cp)
        if (type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
        ) {
            return true
        }
        return cp == 0x200D || // Zero-Width Joiner
            cp in 0xFE00..0xFE0F || // Variation Selectors
            cp in 0xE0100..0xE01EF || // Variation Selectors Supplement
            cp in 0x1F3FB..0x1F3FF // Emoji Skin Tone Modifiers
    }

    fun findSafeBreakIndex(text: CharSequence, start: Int, maxLen: Int): Int {
        val len = text.length
        if (start >= len) return len
        val hardLimit = (start + maxLen).coerceAtMost(len)
        if (hardLimit >= len) return len

        var end = hardLimit
        // Never split a UTF-16 surrogate pair
        if (end > start && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
            end--
        }

        // Back off if boundary sits before a combining mark / VS / modifier / ZWJ, or right after a ZWJ
        var steps = 0
        while (end > start && steps < 64) {
            val nextCp = Character.codePointAt(text, end)
            val prevCp = Character.codePointBefore(text, end)
            if (isCombiningOrJoinerCodePoint(nextCp) || prevCp == 0x200D) {
                val prevCount = Character.charCount(prevCp)
                if (end - prevCount <= start) break
                end -= prevCount
                if (end > start && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
                    if (end - 1 <= start) break
                    end--
                }
                steps++
            } else {
                break
            }
        }

        if (end <= start) {
            // Fallback for pathological all-combining input: avoid splitting surrogate pair if possible
            end = hardLimit
            if (end > start + 1 && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
                end--
            }
        }
        return end.coerceAtLeast((start + 1).coerceAtMost(len))
    }

    fun splitForChunkedPaste(
        text: String,
        directThreshold: Int = DIRECT_PASTE_MAX_CHARS,
        chunkSize: Int = CHUNK_PASTE_SIZE
    ): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.length < directThreshold) return listOf(text)

        val safeChunkSize = chunkSize.coerceAtLeast(64)
        val chunks = ArrayList<String>((text.length / safeChunkSize) + 1)
        var pos = 0
        while (pos < text.length) {
            val nextEnd = findSafeBreakIndex(text, pos, safeChunkSize)
            chunks.add(text.substring(pos, nextEnd))
            pos = nextEnd
        }
        return chunks
    }

    fun buildPreview(
        text: CharSequence,
        startOffset: Int = 0,
        endOffset: Int = text.length,
        maxChars: Int = PREVIEW_MAX_CHARS
    ): Pair<String, Int> {
        val totalLength = (endOffset - startOffset).coerceAtLeast(0)
        if (totalLength == 0) return "" to 0

        val scanEnd = (startOffset + (maxChars + 80)).coerceAtMost(endOffset)
        val normalized = StringBuilder(minOf(totalLength, maxChars + 4))
        var prevSpace = false
        var i = startOffset
        while (i < scanEnd && normalized.length < maxChars + 32) {
            val cp = Character.codePointAt(text, i)
            val count = Character.charCount(cp)
            if (Character.isWhitespace(cp)) {
                if (!prevSpace && normalized.isNotEmpty()) {
                    normalized.append(' ')
                    prevSpace = true
                }
            } else {
                normalized.appendCodePoint(cp)
                prevSpace = false
            }
            i += count
        }

        val wasTruncated = scanEnd < endOffset || normalized.length > maxChars
        if (!wasTruncated) {
            return normalized.toString().trimEnd() to totalLength
        }

        val cutLimit = (maxChars - 3).coerceAtLeast(16)
        val safeEnd = findSafeBreakIndex(normalized, 0, cutLimit)
        val head = normalized.substring(0, safeEnd).trimEnd()
        return "$head..." to totalLength
    }

    fun sha256OfSlice(text: CharSequence, startOffset: Int, endOffset: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var pos = startOffset
        var bufIdx = 0
        while (pos < endOffset) {
            val ch = text[pos++].code
            buffer[bufIdx++] = (ch ushr 8).toByte()
            buffer[bufIdx++] = ch.toByte()
            if (bufIdx >= buffer.size - 1) {
                digest.update(buffer, 0, bufIdx)
                bufIdx = 0
            }
        }
        if (bufIdx > 0) {
            digest.update(buffer, 0, bufIdx)
        }
        val bytes = digest.digest()
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hex.append(HEX_CHARS[v ushr 4])
            hex.append(HEX_CHARS[v and 0x0F])
        }
        return hex.toString()
    }

    private const val HEX_CHARS = "0123456789abcdef"
}

interface ClipboardBackingStore {
    fun saveClipboardText(rawText: String, nowMillis: Long): ClipboardItem?
    fun getPreviews(limit: Int = 25): List<ClipboardItem>
    fun getFullText(id: String): String?
    fun getLatestFullText(): String?
    fun isLatestSame(rawText: String): Boolean
    fun clearAll()
}

class InMemoryClipboardBackingStore : ClipboardBackingStore {
    private data class StoredEntry(
        val item: ClipboardItem,
        val hash: String,
        val fullText: String
    )

    private val lock = Any()
    private val entries = ArrayList<StoredEntry>()

    override fun saveClipboardText(rawText: String, nowMillis: Long): ClipboardItem? {
        val (start, end) = TextChunker.trimmedBounds(rawText)
        if (start >= end) return null
        val cleaned = if (start == 0 && end == rawText.length) rawText else rawText.substring(start, end)
        val (preview, charCount) = TextChunker.buildPreview(cleaned, 0, cleaned.length)
        val hash = TextChunker.sha256OfSlice(cleaned, 0, cleaned.length)
        val item = ClipboardItem(
            id = UUID.randomUUID().toString().take(10),
            preview = preview,
            charCount = charCount,
            timestampMillis = nowMillis
        )
        synchronized(lock) {
            entries.removeAll { it.hash == hash && it.item.charCount == charCount }
            entries.add(0, StoredEntry(item = item, hash = hash, fullText = cleaned))
            if (entries.size > 25) {
                entries.subList(25, entries.size).clear()
            }
        }
        return item
    }

    override fun getPreviews(limit: Int): List<ClipboardItem> = synchronized(lock) {
        entries.take(limit).map { it.item }
    }

    override fun getFullText(id: String): String? = synchronized(lock) {
        entries.firstOrNull { it.item.id == id }?.fullText
    }

    override fun getLatestFullText(): String? = synchronized(lock) {
        entries.firstOrNull()?.fullText
    }

    override fun isLatestSame(rawText: String): Boolean {
        val (start, end) = TextChunker.trimmedBounds(rawText)
        if (start >= end) return true
        val charCount = end - start
        val hash = TextChunker.sha256OfSlice(rawText, start, end)
        return synchronized(lock) {
            val latest = entries.firstOrNull() ?: return false
            latest.item.charCount == charCount && latest.hash == hash
        }
    }

    override fun clearAll() {
        synchronized(lock) { entries.clear() }
    }
}

class ClipboardDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION),
    ClipboardBackingStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ITEMS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_PREVIEW TEXT NOT NULL,
                $COL_CHAR_COUNT INTEGER NOT NULL,
                $COL_HASH TEXT NOT NULL,
                $COL_IS_CHUNKED INTEGER NOT NULL DEFAULT 0,
                $COL_FULL_TEXT TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_clipboard_timestamp
            ON $TABLE_ITEMS ($COL_TIMESTAMP DESC)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CHUNKS (
                $COL_CHUNK_ITEM_ID TEXT NOT NULL,
                $COL_CHUNK_INDEX INTEGER NOT NULL,
                $COL_CHUNK_TEXT TEXT NOT NULL,
                PRIMARY KEY ($COL_CHUNK_ITEM_ID, $COL_CHUNK_INDEX)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHUNKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ITEMS")
        onCreate(db)
    }

    override fun saveClipboardText(rawText: String, nowMillis: Long): ClipboardItem? {
        val (start, end) = TextChunker.trimmedBounds(rawText)
        if (start >= end) return null
        val cleaned = if (start == 0 && end == rawText.length) rawText else rawText.substring(start, end)
        val (preview, charCount) = TextChunker.buildPreview(cleaned, 0, cleaned.length)
        val hash = TextChunker.sha256OfSlice(cleaned, 0, cleaned.length)
        val newId = UUID.randomUUID().toString().take(10)
        val isChunked = cleaned.length > INLINE_MAX_CHARS

        val db = writableDatabase
        db.beginTransaction()
        try {
            // Remove duplicate entries by hash & char_count
            db.rawQuery(
                "SELECT $COL_ID FROM $TABLE_ITEMS WHERE $COL_HASH = ? AND $COL_CHAR_COUNT = ?",
                arrayOf(hash, charCount.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val dupId = cursor.getString(0)
                    db.delete(TABLE_CHUNKS, "$COL_CHUNK_ITEM_ID = ?", arrayOf(dupId))
                }
            }
            db.delete(
                TABLE_ITEMS,
                "$COL_HASH = ? AND $COL_CHAR_COUNT = ?",
                arrayOf(hash, charCount.toString())
            )

            val itemValues = ContentValues().apply {
                put(COL_ID, newId)
                put(COL_PREVIEW, preview)
                put(COL_CHAR_COUNT, charCount)
                put(COL_HASH, hash)
                put(COL_IS_CHUNKED, if (isChunked) 1 else 0)
                put(COL_FULL_TEXT, if (isChunked) "" else cleaned)
                put(COL_TIMESTAMP, nowMillis)
            }
            db.insertOrThrow(TABLE_ITEMS, null, itemValues)

            if (isChunked) {
                var pos = 0
                var chunkIndex = 0
                while (pos < cleaned.length) {
                    val nextEnd = TextChunker.findSafeBreakIndex(cleaned, pos, DB_CHUNK_CHARS)
                    val chunkValues = ContentValues().apply {
                        put(COL_CHUNK_ITEM_ID, newId)
                        put(COL_CHUNK_INDEX, chunkIndex++)
                        put(COL_CHUNK_TEXT, cleaned.substring(pos, nextEnd))
                    }
                    db.insertOrThrow(TABLE_CHUNKS, null, chunkValues)
                    pos = nextEnd
                }
            }

            // Trim old entries beyond HISTORY_LIMIT
            val idsToDelete = mutableListOf<String>()
            db.rawQuery(
                "SELECT $COL_ID FROM $TABLE_ITEMS ORDER BY $COL_TIMESTAMP DESC LIMIT -1 OFFSET ?",
                arrayOf(HISTORY_LIMIT.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    idsToDelete.add(cursor.getString(0))
                }
            }
            for (oldId in idsToDelete) {
                db.delete(TABLE_CHUNKS, "$COL_CHUNK_ITEM_ID = ?", arrayOf(oldId))
                db.delete(TABLE_ITEMS, "$COL_ID = ?", arrayOf(oldId))
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return ClipboardItem(
            id = newId,
            preview = preview,
            charCount = charCount,
            timestampMillis = nowMillis
        )
    }

    override fun getPreviews(limit: Int): List<ClipboardItem> {
        val db = readableDatabase
        val result = ArrayList<ClipboardItem>(limit.coerceAtMost(HISTORY_LIMIT))
        db.rawQuery(
            "SELECT $COL_ID, $COL_PREVIEW, $COL_CHAR_COUNT, $COL_TIMESTAMP FROM $TABLE_ITEMS ORDER BY $COL_TIMESTAMP DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ClipboardItem(
                        id = cursor.getString(0),
                        preview = cursor.getString(1),
                        charCount = cursor.getInt(2),
                        timestampMillis = cursor.getLong(3)
                    )
                )
            }
        }
        return result
    }

    override fun getFullText(id: String): String? {
        if (id.isBlank()) return null
        val db = readableDatabase
        var isChunked = 0
        var inlineText = ""
        var charCount = 0
        var found = false

        db.rawQuery(
            "SELECT $COL_IS_CHUNKED, $COL_FULL_TEXT, $COL_CHAR_COUNT FROM $TABLE_ITEMS WHERE $COL_ID = ? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                found = true
                isChunked = cursor.getInt(0)
                inlineText = cursor.getString(1)
                charCount = cursor.getInt(2)
            }
        }

        if (!found) return null
        if (isChunked == 0) return inlineText

        val sb = StringBuilder(charCount.coerceAtLeast(64))
        db.rawQuery(
            "SELECT $COL_CHUNK_TEXT FROM $TABLE_CHUNKS WHERE $COL_CHUNK_ITEM_ID = ? ORDER BY $COL_CHUNK_INDEX ASC",
            arrayOf(id)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sb.append(cursor.getString(0))
            }
        }
        return sb.toString()
    }

    override fun getLatestFullText(): String? {
        val db = readableDatabase
        val latestId = db.rawQuery(
            "SELECT $COL_ID FROM $TABLE_ITEMS ORDER BY $COL_TIMESTAMP DESC LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null
        return getFullText(latestId)
    }

    override fun isLatestSame(rawText: String): Boolean {
        val (start, end) = TextChunker.trimmedBounds(rawText)
        if (start >= end) return true
        val charCount = end - start
        val db = readableDatabase
        var latestHash: String? = null
        var latestCount = -1
        db.rawQuery(
            "SELECT $COL_HASH, $COL_CHAR_COUNT FROM $TABLE_ITEMS ORDER BY $COL_TIMESTAMP DESC LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                latestHash = cursor.getString(0)
                latestCount = cursor.getInt(1)
            }
        }
        if (latestHash == null || latestCount != charCount) return false
        val hash = TextChunker.sha256OfSlice(rawText, start, end)
        return hash == latestHash
    }

    override fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_CHUNKS, null, null)
            db.delete(TABLE_ITEMS, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val DB_NAME = "ketikku_clipboard.db"
        private const val DB_VERSION = 1
        private const val TABLE_ITEMS = "clipboard_items"
        private const val TABLE_CHUNKS = "clipboard_chunks"

        private const val COL_ID = "id"
        private const val COL_PREVIEW = "preview"
        private const val COL_CHAR_COUNT = "char_count"
        private const val COL_HASH = "content_hash"
        private const val COL_IS_CHUNKED = "is_chunked"
        private const val COL_FULL_TEXT = "full_text"
        private const val COL_TIMESTAMP = "timestamp_millis"

        private const val COL_CHUNK_ITEM_ID = "item_id"
        private const val COL_CHUNK_INDEX = "chunk_index"
        private const val COL_CHUNK_TEXT = "chunk_text"

        const val HISTORY_LIMIT = 25
        private const val INLINE_MAX_CHARS = 250_000
        private const val DB_CHUNK_CHARS = 100_000
    }
}
