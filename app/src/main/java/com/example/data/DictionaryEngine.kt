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
            if (index.defaultSuggestions.isEmpty()) {
                return SuggestionResult(primaryAutoCorrect = null, suggestions = emptyList())
            }
            val topIdle = index.defaultSuggestions
                .sortedByDescending { word ->
                    val score = personalityScore(word, personality, nowMillis)
                    if (score > 0.0) score else 1.0
                }
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

        // Gather matches strictly from Kamus Pribadi (across all folders and subfolders)
        val rankedPool = ArrayList<Pair<String, Double>>(16)
        val seenPoolLower = HashSet<String>(16)

        if (exactPersonalExists) {
            seenPoolLower.add(lowerWord)
            val baseScore = personalityScore(boundedWord, personality, nowMillis)
            rankedPool.add(boundedWord to (baseScore + 5.0))
        }

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

        rankedPool.sortByDescending { it.second }
        for ((word, _) in rankedPool) {
            if (!addCandidate(word)) break
        }

        return SuggestionResult(
            primaryAutoCorrect = primaryShortcut,
            suggestions = candidates.take(3)
        )
    }
}
