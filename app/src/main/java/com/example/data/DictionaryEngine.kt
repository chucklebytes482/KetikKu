package com.example.data

import com.example.model.PersonalDictionaryEntry
import java.util.Locale

data class SuggestionResult(
    val primaryAutoCorrect: String? = null,
    val suggestions: List<String> = emptyList()
)

object DictionaryEngine {

    private val UNMODIFIABLE_LIST_MARKER: Class<*> =
        java.util.Collections.unmodifiableList(emptyList<PersonalDictionaryEntry>()).javaClass

    private const val MAX_INDEX_PREFIX_LEN = 16

    private class IndexedDictionary(
        val sourceIdentity: List<PersonalDictionaryEntry>,
        val isUnmodifiableSnapshot: Boolean,
        val sourceSnapshot: List<PersonalDictionaryEntry>,
        val shortcutMap: Map<String, String>,
        val wordsByPrefix: Map<String, List<Pair<String, String>>>,
        val wordLowerSet: Set<String>,
        val defaultSuggestions: List<String>
    )

    @Volatile
    private var cachedIndex: IndexedDictionary? = null

    fun prewarmIndex(personalEntries: List<PersonalDictionaryEntry>) {
        getOrBuildIndex(personalEntries)
    }

    private fun getOrBuildIndex(personalEntries: List<PersonalDictionaryEntry>): IndexedDictionary {
        val current = cachedIndex
        val isUnmodifiable = UNMODIFIABLE_LIST_MARKER.isInstance(personalEntries)
        if (current != null) {
            if (isUnmodifiable && current.isUnmodifiableSnapshot && current.sourceIdentity === personalEntries) {
                return current
            }
            if (current.sourceSnapshot == personalEntries) {
                return current
            }
        }

        val snapshot = personalEntries.toList()
        val active = ArrayList<PersonalDictionaryEntry>(snapshot.size)
        for (entry in snapshot) {
            if (!entry.deleted) active.add(entry)
        }

        val shortcutMap = HashMap<String, String>(active.size)
        val seenLowerWords = HashSet<String>(active.size)
        val wordsByPrefix = HashMap<String, MutableList<Pair<String, String>>>(active.size * 4)
        val defaults = ArrayList<String>(3)

        for (entry in active) {
            if (defaults.size < 3 && entry.word.isNotBlank()) {
                defaults.add(entry.word)
            }
            if (entry.shortcut.isNotBlank()) {
                val key = entry.shortcut.lowercase(Locale.ROOT)
                if (!shortcutMap.containsKey(key)) {
                    shortcutMap[key] = entry.word
                }
            }
            if (entry.word.isNotBlank()) {
                val lowerWord = entry.word.lowercase(Locale.ROOT)
                if (seenLowerWords.add(lowerWord)) {
                    val maxLen = minOf(lowerWord.length, MAX_INDEX_PREFIX_LEN)
                    for (length in 1..maxLen) {
                        val bucket = wordsByPrefix.getOrPut(lowerWord.substring(0, length)) { ArrayList(4) }
                        // Three candidates plus one possible exact match to skip.
                        if (bucket.size < 4) bucket.add(lowerWord to entry.word)
                    }
                }
            }
        }

        val built = IndexedDictionary(
            sourceIdentity = personalEntries,
            isUnmodifiableSnapshot = isUnmodifiable,
            sourceSnapshot = snapshot,
            shortcutMap = shortcutMap,
            wordsByPrefix = wordsByPrefix,
            wordLowerSet = seenLowerWords,
            defaultSuggestions = defaults
        )
        cachedIndex = built
        return built
    }

    // Kamus kecil bawaan khusus untuk saran kata (tidak pernah masuk ke kamus pribadi / sinkronisasi):
    // 420 kata bahasa Indonesia (70%) + 180 kata bahasa Inggris (30%) = 600 kata.
    val SMALL_DICT_INDONESIAN: List<String> = listOf(
        "saya", "tidak", "yang", "dengan", "untuk", "dari", "ini", "itu", "bisa", "ada",
        "akan", "juga", "sudah", "belum", "kalau", "karena", "atau", "tapi", "lagi", "baru",
        "mau", "harus", "boleh", "terima", "kasih", "tolong", "maaf", "selamat", "pagi", "siang",
        "sore", "malam", "besok", "hari", "sekarang", "nanti", "kemarin", "minggu", "bulan", "tahun",
        "rumah", "kerja", "kantor", "jalan", "pulang", "makan", "minum", "tidur", "bagus", "baik",
        "cepat", "lama", "banyak", "sedikit", "semua", "kita", "kami", "mereka", "kamu", "anda",
        "teman", "keluarga", "anak", "ibu", "bapak", "kakak", "adik", "harga", "uang", "bayar",
        "kirim", "pesan", "balas", "tanya", "jawab", "bantu", "cari", "buat", "lihat", "dengar",
        "baca", "tulis", "simpan", "hapus", "buka", "tutup", "mulai", "selesai", "lanjut", "coba",
        "ingat", "lupa", "tunggu", "datang", "pergi", "masuk", "keluar", "naik", "turun", "kiri",
        "kanan", "atas", "bawah", "dalam", "luar", "dekat", "jauh", "benar", "salah", "mudah",
        "sulit", "penting", "siap", "aman", "jelas", "mohon", "silakan", "oke", "tentu", "pasti",
        "mungkin", "hanya", "saja", "masih", "sedang", "sangat", "lebih", "kurang", "paling", "sama",
        "beda", "lain", "setiap", "waktu", "tempat", "acara", "rapat", "berkas", "catatan", "nomor"
    ) + ExpandedSuggestionWords.indonesian

    val SMALL_DICT_ENGLISH: List<String> = listOf(
        "please", "thank", "thanks", "sorry", "hello", "good", "morning", "night", "today", "tomorrow",
        "meeting", "schedule", "update", "check", "confirm", "cancel", "send", "sent", "reply", "share",
        "save", "delete", "open", "close", "done", "ready", "wait", "need", "want", "help",
        "work", "office", "home", "team", "project", "report", "file", "link", "email", "number",
        "address", "name", "password", "login", "account", "payment", "order", "delivery", "status", "problem",
        "issue", "review", "approve", "feedback", "notes", "next", "start", "finish", "great", "welcome"
    ) + ExpandedSuggestionWords.english

    private val SMALL_DICT_ALL: List<String> = SMALL_DICT_INDONESIAN + SMALL_DICT_ENGLISH
    private val SMALL_DICT_SET: Set<String> = SMALL_DICT_ALL.toHashSet()
    private val SMALL_DICT_BY_PREFIX: Map<String, List<String>> = buildMap {
        val mutable = HashMap<String, MutableList<String>>(SMALL_DICT_ALL.size * 4)
        for (word in SMALL_DICT_ALL) {
            val maxLen = minOf(word.length, MAX_INDEX_PREFIX_LEN)
            for (length in 1..maxLen) {
                val prefix = word.substring(0, length)
                val bucket = mutable.getOrPut(prefix) { ArrayList(4) }
                if (bucket.size < 4) bucket.add(word)
            }
        }
        putAll(mutable)
    }
    private val DEFAULT_IDLE_SUGGESTIONS: List<String> = listOf("Saya", "Tidak", "Yang")

    fun findShortcutMatch(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>
    ): String? {
        if (currentWord.isEmpty() || personalEntries.isEmpty()) return null
        val index = getOrBuildIndex(personalEntries)
        return index.shortcutMap[currentWord.lowercase(Locale.ROOT)]
    }

    fun getSuggestions(
        currentWord: String,
        previousWord: String,
        personalEntries: List<PersonalDictionaryEntry>
    ): SuggestionResult {
        val index = getOrBuildIndex(personalEntries)
        val boundedWord = if (currentWord.length > 30) currentWord.takeLast(30) else currentWord
        if (boundedWord.isEmpty()) {
            val idle = ArrayList<String>(3)
            for (word in index.defaultSuggestions) {
                if (idle.size >= 3) break
                if (!idle.contains(word)) idle.add(word)
            }
            for (fallback in DEFAULT_IDLE_SUGGESTIONS) {
                if (idle.size >= 3) break
                if (!idle.contains(fallback)) idle.add(fallback)
            }
            return SuggestionResult(
                primaryAutoCorrect = null,
                suggestions = idle.take(3)
            )
        }

        val lowerWord = boundedWord.lowercase(Locale.ROOT)
        val shortcutTarget = index.shortcutMap[lowerWord]
        val exactPersonalExists = index.wordLowerSet.contains(lowerWord)
        val exactSmallDictExists = SMALL_DICT_SET.contains(lowerWord)

        val preserveCaps = boundedWord[0].isUpperCase()
        fun applyCase(w: String): String =
            if (preserveCaps && w.isNotEmpty()) w.replaceFirstChar { it.uppercaseChar() } else w

        val candidates = ArrayList<String>(3)
        if (shortcutTarget != null) {
            candidates.add(shortcutTarget)
        }
        if ((exactPersonalExists || exactSmallDictExists) &&
            !candidates.contains(boundedWord) &&
            candidates.size < 3
        ) {
            candidates.add(boundedWord)
        }

        val lookupPrefix = if (lowerWord.length > MAX_INDEX_PREFIX_LEN) {
            lowerWord.substring(0, MAX_INDEX_PREFIX_LEN)
        } else {
            lowerWord
        }

        // Priority 1: bounded prefix lookup, no scan through a first-letter bucket.
        val personalBucket = index.wordsByPrefix[lookupPrefix]
        if (personalBucket != null) {
            for ((lowerDict, rawDict) in personalBucket) {
                if (candidates.size >= 3) break
                if (lowerDict != lowerWord && lowerDict.startsWith(lowerWord)) {
                    val styled = applyCase(rawDict)
                    if (!candidates.contains(styled)) {
                        candidates.add(styled)
                    }
                }
            }
        }

        // Priority 2: bounded built-in prefix lookup.
        if (candidates.size < 3) {
            val smallBucket = SMALL_DICT_BY_PREFIX[lookupPrefix]
            if (smallBucket != null) {
                for (smallWord in smallBucket) {
                    if (candidates.size >= 3) break
                    if (smallWord != lowerWord && smallWord.startsWith(lowerWord)) {
                        val styled = applyCase(smallWord)
                        if (!candidates.contains(styled)) {
                            candidates.add(styled)
                        }
                    }
                }
            }
        }

        if (candidates.size < 3 && !exactPersonalExists && !exactSmallDictExists && !candidates.contains(currentWord)) {
            candidates.add(currentWord)
        }

        return SuggestionResult(
            primaryAutoCorrect = shortcutTarget,
            suggestions = candidates.take(3)
        )
    }
}
