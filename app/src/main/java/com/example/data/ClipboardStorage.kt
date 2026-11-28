package com.example.data

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
    fun getContentHash(id: String): String?
    fun getLatestFullText(): String?
    fun isLatestSame(rawText: String): Boolean
    fun clearAll()
}

/**
 * Papan klip hanya data sementara di memori proses. Tidak pernah ditulis ke berkas.
 * Satu instans dipakai bersama oleh aplikasi dan IME (proses yang sama).
 */
class InMemoryClipboardBackingStore : ClipboardBackingStore {
    private data class StoredEntry(
        val item: ClipboardItem,
        val hash: String,
        val fullText: String
    )

    private val lock = Any()
    private val entries = ArrayList<StoredEntry>()

    /** Buang item yang sudah lewat masa berlaku, dihitung dari waktu copy. Dipanggil di dalam lock. */
    private fun pruneExpiredLocked(nowMillis: Long = System.currentTimeMillis()) {
        entries.removeAll { nowMillis - it.item.timestampMillis > ClipboardMemory.TTL_MILLIS }
    }

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
            timestampMillis = nowMillis,
            contentHash = hash
        )
        synchronized(lock) {
            pruneExpiredLocked()
            entries.removeAll { it.hash == hash && it.item.charCount == charCount }
            entries.add(0, StoredEntry(item = item, hash = hash, fullText = cleaned))
            if (entries.size > ClipboardMemory.HISTORY_LIMIT) {
                entries.subList(ClipboardMemory.HISTORY_LIMIT, entries.size).clear()
            }
        }
        return item
    }

    override fun getPreviews(limit: Int): List<ClipboardItem> = synchronized(lock) {
        pruneExpiredLocked()
        entries.take(limit).map { it.item }
    }

    override fun getFullText(id: String): String? = synchronized(lock) {
        pruneExpiredLocked()
        entries.firstOrNull { it.item.id == id }?.fullText
    }

    override fun getContentHash(id: String): String? = synchronized(lock) {
        pruneExpiredLocked()
        entries.firstOrNull { it.item.id == id }?.let { "${it.item.charCount}:${it.hash}" }
    }

    override fun getLatestFullText(): String? = synchronized(lock) {
        pruneExpiredLocked()
        entries.firstOrNull()?.fullText
    }

    override fun isLatestSame(rawText: String): Boolean {
        val (start, end) = TextChunker.trimmedBounds(rawText)
        if (start >= end) return true
        val charCount = end - start
        val hash = TextChunker.sha256OfSlice(rawText, start, end)
        return synchronized(lock) {
            pruneExpiredLocked()
            val latest = entries.firstOrNull() ?: return false
            latest.item.charCount == charCount && latest.hash == hash
        }
    }

    override fun clearAll() {
        synchronized(lock) { entries.clear() }
    }
}

object ClipboardMemory {
    const val HISTORY_LIMIT = 25
    /** Item papan klip hilang 1 jam setelah dicopy. */
    const val TTL_MILLIS = 60L * 60L * 1000L
    val store: ClipboardBackingStore by lazy { InMemoryClipboardBackingStore() }
}
