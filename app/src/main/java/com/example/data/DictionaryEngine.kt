package com.example.data

import com.example.model.PersonalDictionaryEntry
import com.example.model.WordPersonalityStat
import java.util.Locale

data class SuggestionResult(
    val primaryAutoCorrect: String? = null,
    val suggestions: List<String> = emptyList()
)

/** Satu kata folder (num, pw, kred) beserta ID entri asalnya, untuk statistik pemakaian. */
data class FolderChoice(val entryId: String, val word: String)

/** Batas habit sesi: kata yang sering diketik (hanya di memori, tidak pernah ditulis ke berkas). */
const val HABIT_MIN_USES = 2
const val HABIT_MAX_WORDS = 200

object DictionaryEngine {

    private val UNMODIFIABLE_LIST_MARKER: Class<*> =
        java.util.Collections.unmodifiableList(emptyList<PersonalDictionaryEntry>()).javaClass

    private const val MAX_INDEX_PREFIX_LEN = 16
    /** Saran kata dari kamus pribadi baru muncul setelah sekian huruf diketik. */
    const val MIN_PREFIX_SUGGEST_LEN = 3
    /** Mengetik ini (utuh) menampilkan semua entri kredensial di bilah saran. */
    const val KRED_TRIGGER = "kred"
    /** "em" + huruf a-z menyaring email dari folder aut dan kred yang diawali huruf itu. "em" saja tidak menampilkan email. */
    const val EMAIL_TRIGGER = "em"
    private val EMAIL_PREFIX_KEY_REGEX = Regex("^em[a-z]+$")
    private val EMAIL_LINE_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private const val SHORTCUT_PERSONALIZATION_BONUS = 25.0

    private class IndexedDictionary(
        val sourceIdentity: List<PersonalDictionaryEntry>,
        val isUnmodifiableSnapshot: Boolean,
        val sourceSnapshot: List<PersonalDictionaryEntry>,
        val shortcutMap: Map<String, List<PersonalDictionaryEntry>>,
        val wordsByPrefix: Map<String, List<Pair<String, String>>>,
        val wordLowerSet: Set<String>,
        val shortcutOwnedLowerWords: Set<String>,
        val defaultSuggestions: List<String>
    )

    @Volatile
    private var cachedIndex: IndexedDictionary? = null

    fun prewarmIndex(personalEntries: List<PersonalDictionaryEntry>) {
        getOrBuildIndex(personalEntries)
    }

    /** Kata yang ditampilkan untuk entri: kolom pertama. Kredensial menyimpan "kata\ndomain". */
    fun displayWord(entry: PersonalDictionaryEntry): String = entry.word.substringBefore('\n').trim()

    private fun isShortcutOnlyFolder(folder: String): Boolean {
        val norm = SettingsRepository.normalizeFolderPath(folder)
        return norm == SettingsRepository.FOLDER_KATA_SANDI ||
            norm == SettingsRepository.FOLDER_KREDENSIAL ||
            norm == SettingsRepository.FOLDER_A2F
    }

    /**
     * Entri A2F (kode 2FA) dipisah total dari indeks: tidak pernah jadi kata saran,
     * dan pintasannya hanya dibaca lewat [findA2fEntries].
     */
    fun isA2fEntry(entry: PersonalDictionaryEntry): Boolean =
        SettingsRepository.normalizeFolderPath(entry.folder) == SettingsRepository.FOLDER_A2F

    /**
     * Kata kontak yang boleh jadi saran: email dan website pada A2F (aut), website pada Kredensial (kred).
     * Kata sandi (baris pertama kred) dan pw tidak pernah jadi saran.
     */
    fun contactWords(entry: PersonalDictionaryEntry): List<String> {
        val parts = entry.word.split('\n')
        val words = when (SettingsRepository.normalizeFolderPath(entry.folder)) {
            SettingsRepository.FOLDER_A2F -> listOf(
                parts.getOrElse(0) { "" }.trim(),
                SettingsRepository.normalizeWebsiteDomain(parts.getOrElse(1) { "" })
            )
            SettingsRepository.FOLDER_KREDENSIAL -> listOf(
                SettingsRepository.normalizeWebsiteDomain(parts.getOrElse(1) { "" })
            )
            else -> emptyList()
        }
        return words.filter { it.isNotEmpty() }
    }

    /** Entri di Sampah tidak pernah muncul di saran. */
    fun isTrashEntry(entry: PersonalDictionaryEntry): Boolean =
        SettingsRepository.normalizeFolderPath(entry.folder) == SettingsRepository.FOLDER_SAMPAH

    /** Kunci pemakaian entri di peta personalitas. Hanya ID, tanpa isi kredensial. */
    fun entryUsageKey(entryId: String): String = "entry:${entryId.trim()}".lowercase(Locale.ROOT)

    /** Urutan folder: paling sering dipakai dulu, lalu yang paling baru diubah. */
    private fun rankByUsage(
        entries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat>,
        nowMillis: Long
    ): List<PersonalDictionaryEntry> {
        if (personality.isEmpty()) return entries.sortedByDescending { it.updatedAtMillis }
        return entries.sortedWith(
            compareByDescending<PersonalDictionaryEntry> {
                personalityScore(entryUsageKey(it.id), personality, nowMillis)
            }.thenByDescending { it.updatedAtMillis }
        )
    }

    /**
     * Semua akun A2F aktif yang berbagi pintasan [currentWord] (akun dengan domain sama).
     * Urutan: paling sering dipakai, lalu yang paling baru diubah.
     */
    fun findA2fEntries(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<PersonalDictionaryEntry> {
        val key = currentWord.trim()
        if (key.isEmpty()) return emptyList()
        val matches = personalEntries.filter { entry ->
            !entry.deleted &&
                isA2fEntry(entry) &&
                entry.secretCipher.isNotBlank() &&
                (entry.shortcut.trim().equals(key, ignoreCase = true) ||
                    SettingsRepository.autoShortcut(entry.folder, entry.word).equals(key, ignoreCase = true))
        }
        return rankByUsage(matches, personality, nowMillis)
    }

    /** Pasangan (ID entri, kata) untuk folder `num`. Kosong untuk kata lain. */
    fun findFolderEntryChoices(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<FolderChoice> {
        if (!currentWord.trim().equals(SettingsRepository.FOLDER_NOMOR, ignoreCase = true)) return emptyList()
        val matches = personalEntries.filter { entry ->
            !entry.deleted &&
                entry.word.isNotBlank() &&
                SettingsRepository.normalizeFolderPath(entry.folder) == SettingsRepository.FOLDER_NOMOR
        }
        return rankByUsage(matches, personality, nowMillis)
            .map { FolderChoice(it.id, it.word.trim()) }
            .distinctBy { it.word }
    }

    /**
     * Mengetik persis nama folder [SettingsRepository.FOLDER_NOMOR] ("num") menampilkan semua kata di folder itu.
     * Kosong untuk kata lain.
     */
    fun findFolderEntryWords(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> = findFolderEntryChoices(currentWord, personalEntries, personality, nowMillis).map { it.word }

    /** Pasangan (ID entri, kata) untuk pintasan pw/kred. */
    fun findPagedShortcutChoices(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<FolderChoice> {
        val key = currentWord.trim().lowercase(Locale.ROOT)
        // "em" + huruf (mis. "ema") menyaring email; pintasan pribadi yang persis cocok tetap didahulukan.
        if (EMAIL_PREFIX_KEY_REGEX.matches(key) && personalEntries.none { entry ->
                !entry.deleted && (entry.shortcut.trim().equals(key, ignoreCase = true) ||
                    SettingsRepository.autoShortcut(entry.folder, entry.word).equals(key, ignoreCase = true))
            }
        ) {
            return emailChoices(personalEntries, key.removePrefix(EMAIL_TRIGGER))
        }
        val folder = when {
            key.startsWith(KRED_TRIGGER) -> SettingsRepository.FOLDER_KREDENSIAL
            key.startsWith(SettingsRepository.FOLDER_KATA_SANDI) && key.length > 2 ->
                SettingsRepository.FOLDER_KATA_SANDI
            else -> return emptyList()
        }
        val matches = personalEntries.filter { entry ->
            !entry.deleted && SettingsRepository.normalizeFolderPath(entry.folder) == folder &&
                (key == KRED_TRIGGER || SettingsRepository.autoShortcut(folder, entry.word) == key ||
                    entry.shortcut.trim().equals(key, ignoreCase = true))
        }
        return rankByUsage(matches, personality, nowMillis)
            .map { FolderChoice(it.id, displayWord(it)) }
            .filter { it.word.isNotEmpty() }
    }

    /** Email dari folder aut dan kred, unik (tanpa huruf besar/kecil), diurutkan abjad. */
    private fun emailChoices(
        personalEntries: List<PersonalDictionaryEntry>,
        emailPrefix: String = ""
    ): List<FolderChoice> {
        val prefix = emailPrefix.lowercase(Locale.ROOT)
        return personalEntries
            .asSequence()
            .filter { entry ->
                val norm = SettingsRepository.normalizeFolderPath(entry.folder)
                !entry.deleted &&
                    (norm == SettingsRepository.FOLDER_A2F || norm == SettingsRepository.FOLDER_KREDENSIAL)
            }
            .map { entry -> entry.id to displayWord(entry) }
            .filter { (_, email) -> EMAIL_LINE_REGEX.matches(email) }
            .filter { (_, email) -> email.lowercase(Locale.ROOT).startsWith(prefix) }
            .distinctBy { (_, email) -> email.lowercase(Locale.ROOT) }
            .sortedBy { (_, email) -> email.lowercase(Locale.ROOT) }
            .map { (id, email) -> FolderChoice(id, email) }
            .toList()
    }

    /** Hasil lengkap untuk pintasan pw/kred, terpisah dari tiga saran kata biasa. */
    fun findPagedShortcutWords(
        currentWord: String,
        personalEntries: List<PersonalDictionaryEntry>,
        personality: Map<String, WordPersonalityStat> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> = findPagedShortcutChoices(currentWord, personalEntries, personality, nowMillis).map { it.word }

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
            if (!entry.deleted && !isA2fEntry(entry) && !isTrashEntry(entry)) active.add(entry)
        }
        // Place shortcut-bearing entries first so they take priority in defaults and prefix buckets.
        active.sortByDescending { it.shortcut.isNotBlank() }

        val shortcutMap = HashMap<String, MutableList<PersonalDictionaryEntry>>(active.size)
        val shortcutWordsSeen = HashMap<String, MutableSet<String>>(active.size)
        val seenLowerWords = HashSet<String>(active.size)
        val shortcutOwnedLowerWords = HashSet<String>(active.size)
        val wordsByPrefix = HashMap<String, MutableList<Pair<String, String>>>(active.size * 4)
        val defaults = ArrayList<String>(24)

        for (entry in active) {
            val trimmedWord = displayWord(entry)
            if (trimmedWord.isBlank()) continue
            val lowerWord = trimmedWord.lowercase(Locale.ROOT)
            val shortcutOnly = isShortcutOnlyFolder(entry.folder)

            if (entry.shortcut.isNotBlank()) {
                if (!shortcutOnly) {
                    shortcutOwnedLowerWords.add(lowerWord)
                }
                val key = entry.shortcut.trim().lowercase(Locale.ROOT)
                val seenForKey = shortcutWordsSeen.getOrPut(key) { HashSet(4) }
                if (seenForKey.add(lowerWord)) {
                    shortcutMap.getOrPut(key) { ArrayList(2) }.add(entry.copy(word = trimmedWord))
                }
            }

            // Sensitive folders (pw & kred) must NEVER leak into idle, exact, or prefix suggestions.
            if (shortcutOnly) continue

            if (defaults.size < 24 && !defaults.contains(trimmedWord)) {
                defaults.add(trimmedWord)
            }
            if (seenLowerWords.add(lowerWord)) {
                val maxLen = minOf(lowerWord.length, MAX_INDEX_PREFIX_LEN)
                for (length in 1..maxLen) {
                    wordsByPrefix.getOrPut(lowerWord.substring(0, length)) { ArrayList(4) }.add(lowerWord to trimmedWord)
                }
            }
        }

        // Email (A2F) dan website (A2F, Kredensial) ikut jadi saran setelah MIN_PREFIX_SUGGEST_LEN huruf.
        // Hanya masuk indeks prefix; tidak masuk defaults (idle) dan tidak mengubah pintasan.
        for (entry in snapshot) {
            if (entry.deleted || isTrashEntry(entry)) continue
            for (contact in contactWords(entry)) {
                val lowerContact = contact.lowercase(Locale.ROOT)
                if (!seenLowerWords.add(lowerContact)) continue
                val maxLen = minOf(lowerContact.length, MAX_INDEX_PREFIX_LEN)
                for (length in 1..maxLen) {
                    wordsByPrefix.getOrPut(lowerContact.substring(0, length)) { ArrayList(4) }.add(lowerContact to contact)
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
            shortcutOwnedLowerWords = shortcutOwnedLowerWords,
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
        if (currentWord.trim().lowercase(Locale.ROOT) == KRED_TRIGGER) {
            return findPagedShortcutWords(currentWord, personalEntries).distinct()
        }
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
        nowMillis: Long = System.currentTimeMillis(),
        habit: Map<String, Int> = emptyMap()
    ): SuggestionResult {
        val index = getOrBuildIndex(personalEntries)
        val boundedWord = if (currentWord.length > 30) currentWord.takeLast(30) else currentWord
        // Tidak sedang mengetik (kosong, atau baru selesai spasi): bilah saran dikosongkan.
        if (boundedWord.isEmpty() || boundedWord.isBlank()) {
            return SuggestionResult(primaryAutoCorrect = null, suggestions = emptyList())
        }

        val lowerWord = boundedWord.lowercase(Locale.ROOT)
        val shortcutMatches = findShortcutMatches(boundedWord, personalEntries, personality, nowMillis)
        val primaryShortcut = if (shortcutMatches.size == 1) shortcutMatches.first() else null
        // Kamus pribadi baru menyarankan kata setelah MIN_PREFIX_SUGGEST_LEN huruf (pintasan tetap berlaku dari huruf pertama).
        val prefixSuggestAllowed = boundedWord.length >= MIN_PREFIX_SUGGEST_LEN
        val exactPersonalExists = prefixSuggestAllowed && index.wordLowerSet.contains(lowerWord)

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

        // Priority 1: Personal dictionary shortcut matches (including pw & kred).
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

        // Gather non-sensitive matches strictly from Kamus Pribadi
        val rankedPool = ArrayList<Pair<String, Double>>(16)
        val seenPoolLower = HashSet<String>(16)

        if (exactPersonalExists) {
            seenPoolLower.add(lowerWord)
            val baseScore = personalityScore(boundedWord, personality, nowMillis)
            val shortcutBonus = if (index.shortcutOwnedLowerWords.contains(lowerWord)) {
                SHORTCUT_PERSONALIZATION_BONUS
            } else {
                0.0
            }
            rankedPool.add(boundedWord to (baseScore + shortcutBonus + 5.0))
        }

        val personalBucket = if (prefixSuggestAllowed) index.wordsByPrefix[lookupPrefix] else null
        if (personalBucket != null) {
            for ((lowerDict, rawDict) in personalBucket) {
                if (lowerDict != lowerWord && lowerDict.startsWith(lowerWord) && seenPoolLower.add(lowerDict)) {
                    val styled = applyCase(rawDict)
                    val score = personalityScore(rawDict, personality, nowMillis)
                    val shortcutBonus = if (index.shortcutOwnedLowerWords.contains(lowerDict)) {
                        SHORTCUT_PERSONALIZATION_BONUS
                    } else {
                        0.0
                    }
                    // Kata yang panjangnya paling dekat dengan ketikan lebih dulu (mis. "ter" -> "terima" sebelum "terima kasih").
                    val closeness = 3.0 / (1.0 + (lowerDict.length - lowerWord.length).coerceAtLeast(0))
                    rankedPool.add(styled to (score + shortcutBonus + 2.0 + closeness))
                }
            }
        }

        // Kebiasaan sesi: kata yang sudah diketik berulang, dicocokkan lewat awalan (minimal MIN_PREFIX_SUGGEST_LEN huruf).
        if (habit.isNotEmpty() && boundedWord.length >= MIN_PREFIX_SUGGEST_LEN) {
            for ((lowerHabit, uses) in habit) {
                if (uses < HABIT_MIN_USES || lowerHabit == lowerWord) continue
                if (!lowerHabit.startsWith(lowerWord) || !seenPoolLower.add(lowerHabit)) continue
                rankedPool.add(applyCase(lowerHabit) to (uses * 100.0 + 1.0))
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
