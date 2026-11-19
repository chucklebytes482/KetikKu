package com.example.data

import com.example.model.PersonalDictionaryEntry
import com.example.model.WordPersonalityStat
import java.util.Locale

data class SuggestionResult(
    val primaryAutoCorrect: String? = null,
    val suggestions: List<String> = emptyList()
)

object DictionaryEngine {

    private val UNMODIFIABLE_LIST_MARKER: Class<*> =
        java.util.Collections.unmodifiableList(emptyList<PersonalDictionaryEntry>()).javaClass

    private const val MAX_INDEX_PREFIX_LEN = 16
    private const val MAX_PERSONAL_PREFIX_BUCKET = 32
    const val MIN_BUILTIN_WORD_LENGTH = 8

    private class IndexedDictionary(
        val sourceIdentity: List<PersonalDictionaryEntry>,
        val isUnmodifiableSnapshot: Boolean,
        val sourceSnapshot: List<PersonalDictionaryEntry>,
        val shortcutMap: Map<String, List<PersonalDictionaryEntry>>,
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

        val shortcutMap = HashMap<String, MutableList<PersonalDictionaryEntry>>(active.size)
        val shortcutWordsSeen = HashMap<String, MutableSet<String>>(active.size)
        val seenLowerWords = HashSet<String>(active.size)
        val wordsByPrefix = HashMap<String, MutableList<Pair<String, String>>>(active.size * 4)
        val defaults = ArrayList<String>(12)

        for (entry in active) {
            val trimmedWord = entry.word.trim()
            if (trimmedWord.isBlank()) continue
            val lowerWord = trimmedWord.lowercase(Locale.ROOT)

            if (defaults.size < 12 && !defaults.contains(trimmedWord)) {
                defaults.add(trimmedWord)
            }
            if (entry.shortcut.isNotBlank()) {
                val key = entry.shortcut.trim().lowercase(Locale.ROOT)
                val seenForKey = shortcutWordsSeen.getOrPut(key) { HashSet(4) }
                if (seenForKey.add(lowerWord)) {
                    shortcutMap.getOrPut(key) { ArrayList(2) }.add(entry.copy(word = trimmedWord))
                }
            }
            if (seenLowerWords.add(lowerWord)) {
                val maxLen = minOf(lowerWord.length, MAX_INDEX_PREFIX_LEN)
                for (length in 1..maxLen) {
                    val bucket = wordsByPrefix.getOrPut(lowerWord.substring(0, length)) { ArrayList(4) }
                    if (bucket.size < MAX_PERSONAL_PREFIX_BUCKET) {
                        bucket.add(lowerWord to trimmedWord)
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

    private val CORE_INDONESIAN: List<String> = listOf(
        "sekarang", "terutama", "informasi", "komunikasi", "pengaturan",
        "perbaikan", "perjalanan", "pembayaran", "pekerjaan", "pertemuan",
        "perhatian", "pertanyaan", "penjelasan", "pengiriman", "penerimaan",
        "penyimpanan", "pembaruan", "penelitian", "pendidikan", "kesehatan",
        "kebutuhan", "keamanan", "kebijakan", "keputusan", "kesalahan",
        "kesempatan", "kegiatan", "keluarga", "kendaraan", "keuangan",
        "kehadiran", "kepastian", "kemudahan", "kenyamanan", "kerjasama",
        "ketentuan", "komputer", "aplikasi", "perangkat", "mengetik",
        "membacanya", "menghubungi", "menyimpan", "mengetahui", "mengirimkan",
        "menunggu", "memulainya", "memastikan", "membuatnya", "mengambil"
    )

    private val CORE_ENGLISH: List<String> = listOf(
        "tomorrow", "schedule", "meeting", "password", "remember",
        "together", "question", "response", "software", "hardware",
        "keyboard", "messages", "internet", "delivery", "feedback",
        "progress", "approved", "complete", "personal", "document",
        "location", "transfer", "calendar", "reminder", "solution",
        "planning", "settings", "language", "download", "research"
    )

    // Kamus bawaan khusus untuk saran teks (tidak masuk ke kamus pribadi / sinkronisasi):
    // seluruh kata dijamin minimal 8 karakter.
    val SMALL_DICT_INDONESIAN: List<String> = (CORE_INDONESIAN + ExpandedSuggestionWords.indonesian)
        .asSequence()
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.length >= MIN_BUILTIN_WORD_LENGTH && it.all { ch -> ch.isLetter() } }
        .distinct()
        .toList()

    val SMALL_DICT_ENGLISH: List<String> = (CORE_ENGLISH + ExpandedSuggestionWords.english)
        .asSequence()
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.length >= MIN_BUILTIN_WORD_LENGTH && it.all { ch -> ch.isLetter() } }
        .filter { it !in SMALL_DICT_INDONESIAN }
        .distinct()
        .toList()

    private val SMALL_DICT_ALL: List<String> = (SMALL_DICT_INDONESIAN + SMALL_DICT_ENGLISH).distinct()
    private val SMALL_DICT_SET: Set<String> = SMALL_DICT_ALL.toHashSet()
    private val SMALL_DICT_BY_PREFIX: Map<String, List<String>> = buildMap {
        val mutable = HashMap<String, MutableList<String>>(SMALL_DICT_ALL.size * 6)
        for (word in SMALL_DICT_ALL) {
            val maxLen = minOf(word.length, MAX_INDEX_PREFIX_LEN)
            for (length in 1..maxLen) {
                val prefix = word.substring(0, length)
                val bucket = mutable.getOrPut(prefix) { ArrayList(8) }
                bucket.add(word)
            }
        }
        putAll(mutable)
    }

    private val DEFAULT_IDLE_SUGGESTIONS: List<String> = listOf("Sekarang", "Bagaimana", "Informasi")

    fun personalityScore(
        word: String,
        personality: Map<String, WordPersonalityStat>,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        if (personality.isEmpty()) return 0.0
        val stat = personality[word.trim().lowercase(Locale.ROOT)] ?: return 0.0
        if (stat.usageCount <= 0) return 0.0
        val ageDays = ((nowMillis - stat.lastUsedMillis).coerceAtLeast(0L)) / 86_400_000.0
        val recencyBonus = 50.0 / (1.0 + ageDays)
        return stat.usageCount.toDouble() * 100.0 + recencyBonus
    }

    fun findShortcutMatches(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> {
        if (currentWord.isEmpty() || personalEntries.isEmpty()) return emptyList()
        val index = getOrBuildIndex(personalEntries)
        val rawMatches = index.shortcutMap[currentWord.trim().lowercase(Locale.ROOT)] ?: return emptyList()
        if (rawMatches.size <= 1) return rawMatches.map { it.word }
        return rawMatches
            .sortedWith(
                compareByDescending<PersonalDictionaryEntry> {
                    personalityScore(it.word, personality, nowMillis)
                }.thenByDescending { it.updatedAtMillis }
            )
            .map { it.word }
    }

    fun findShortcutMatch(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val matches = findShortcutMatches(currentWord, personalEntries, personality, nowMillis)
        return if (matches.size == 1) matches.first() else null
    }

    fun getSuggestions(
        currentWord: String,
        previousWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): SuggestionResult {
        val index = getOrBuildIndex(personalEntries)
        val boundedWord = if (currentWord.length > 30) currentWord.takeLast(30) else currentWord
        if (boundedWord.isEmpty()) {
            val idleScored = ArrayList<Pair<String, Double>>(16)
            val seenIdleLower = HashSet<String>(16)

            // Prioritize learned words (>= 8 chars) and active personal entries ordered by personality
            for ((_, stat) in personality) {
                val clean = stat.word.trim()
                if (clean.isEmpty()) continue
                val lower = clean.lowercase(Locale.ROOT)
                val eligible = clean.length >= MIN_BUILTIN_WORD_LENGTH || index.wordLowerSet.contains(lower)
                if (eligible && seenIdleLower.add(lower)) {
                    idleScored.add(clean to personalityScore(clean, personality, nowMillis))
                }
            }
            for (word in index.defaultSuggestions) {
                val lower = word.lowercase(Locale.ROOT)
                if (seenIdleLower.add(lower)) {
                    val score = personalityScore(word, personality, nowMillis)
                    idleScored.add(word to if (score > 0.0) score else 1.0)
                }
            }
            for (fallback in DEFAULT_IDLE_SUGGESTIONS) {
                val lower = fallback.lowercase(Locale.ROOT)
                if (seenIdleLower.add(lower)) {
                    idleScored.add(fallback to personalityScore(fallback, personality, nowMillis))
                }
            }

            val topIdle = idleScored
                .sortedByDescending { it.second }
                .map { it.first }
                .take(3)

            return SuggestionResult(
                primaryAutoCorrect = null,
                suggestions = topIdle
            )
        }

        val lowerWord = boundedWord.lowercase(Locale.ROOT)
        val shortcutMatches = findShortcutMatches(boundedWord, personalEntries, personality, nowMillis)
        val primaryShortcut = if (shortcutMatches.size == 1) shortcutMatches.first() else null
        val exactPersonalExists = index.wordLowerSet.contains(lowerWord)
        val exactSmallDictExists = SMALL_DICT_SET.contains(lowerWord)

        val preserveCaps = boundedWord[0].isUpperCase()
        fun applyCase(w: String): String =
            if (preserveCaps && w.isNotEmpty()) w.replaceFirstChar { it.uppercaseChar() } else w

        val candidates = ArrayList<String>(3)
        val seenLowerCandidates = HashSet<String>(6)

        fun addCandidate(raw: String): Boolean {
            if (candidates.size >= 3) return false
            val lower = raw.trim().lowercase(Locale.ROOT)
            if (lower.isEmpty() || !seenLowerCandidates.add(lower)) return false
            candidates.add(raw)
            return true
        }

        // Priority 1: Personal dictionary shortcut matches (up to 3 ordered by personality).
        for (match in shortcutMatches) {
            if (!addCandidate(match)) break
        }
        if (candidates.size >= 3) {
            return SuggestionResult(
                primaryAutoCorrect = primaryShortcut,
                suggestions = candidates.take(3)
            )
        }

        val lookupPrefix = if (lowerWord.length > MAX_INDEX_PREFIX_LEN) {
            lowerWord.substring(0, MAX_INDEX_PREFIX_LEN)
        } else {
            lowerWord
        }

        // Gather prefix matches from personal dictionary, built-in (>= 8 chars), and learned personality (>= 8 chars)
        val rankedPool = ArrayList<Pair<String, Double>>(16)
        val seenPoolLower = HashSet<String>(16)

        // Include exact personal/built-in typed word with baseline tie-breaker priority
        if (exactPersonalExists || exactSmallDictExists) {
            seenPoolLower.add(lowerWord)
            val baseScore = personalityScore(boundedWord, personality, nowMillis)
            rankedPool.add(boundedWord to (baseScore + 5.0))
        }

        // Boosted personality words that start with typed prefix
        if (personality.isNotEmpty()) {
            for ((statLower, stat) in personality) {
                if (statLower != lowerWord && statLower.startsWith(lowerWord)) {
                    val clean = stat.word.trim()
                    val eligible = clean.length >= MIN_BUILTIN_WORD_LENGTH || index.wordLowerSet.contains(statLower)
                    if (eligible && seenPoolLower.add(statLower)) {
                        rankedPool.add(applyCase(clean) to personalityScore(clean, personality, nowMillis))
                    }
                }
            }
        }

        // Personal dictionary prefix bucket
        val personalBucket = index.wordsByPrefix[lookupPrefix]
        if (personalBucket != null) {
            for ((lowerDict, rawDict) in personalBucket) {
                if (lowerDict != lowerWord && lowerDict.startsWith(lowerWord) && seenPoolLower.add(lowerDict)) {
                    val styled = applyCase(rawDict)
                    val score = personalityScore(rawDict, personality, nowMillis)
                    rankedPool.add(styled to (score + 2.0))
                }
            }
        }

        // Built-in >= 8-char dictionary prefix bucket
        val smallBucket = SMALL_DICT_BY_PREFIX[lookupPrefix]
        if (smallBucket != null) {
            for (smallWord in smallBucket) {
                if (smallWord != lowerWord && smallWord.startsWith(lowerWord) && seenPoolLower.add(smallWord)) {
                    val styled = applyCase(smallWord)
                    val score = personalityScore(smallWord, personality, nowMillis)
                    rankedPool.add(styled to score)
                }
            }
        }

        rankedPool.sortByDescending { it.second }
        for ((word, _) in rankedPool) {
            if (!addCandidate(word)) break
        }

        if (candidates.size < 3 && !exactPersonalExists && !exactSmallDictExists) {
            addCandidate(currentWord)
        }

        return SuggestionResult(
            primaryAutoCorrect = primaryShortcut,
            suggestions = candidates.take(3)
        )
    }
}
