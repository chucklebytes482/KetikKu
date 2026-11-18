package com.example.data

import com.example.model.PersonalDictionaryEntry

data class SuggestionResult(
    val primaryAutoCorrect: String? = null,
    val suggestions: List<String> = emptyList()
)

object DictionaryEngine {

    fun getSuggestions(
        currentWord: String,
        previousWord: String,
        personalEntries: List<PersonalDictionaryEntry>
    ): SuggestionResult {
        val activePersonal = personalEntries.filter { !it.deleted }
        val lowerWord = currentWord.lowercase()

        if (lowerWord.isEmpty()) {
            val defaults = activePersonal.map { it.word }.take(3)
            return SuggestionResult(
                primaryAutoCorrect = null,
                suggestions = defaults
            )
        }

        // Exact shortcut match from personal dictionary
        val shortcutMatch = activePersonal.firstOrNull {
            it.shortcut.isNotBlank() && it.shortcut.equals(lowerWord, ignoreCase = true)
        }

        val dictionaryWords = activePersonal
            .filter { it.word.isNotBlank() }
            .map { it.word }
            .distinct()

        val exactExists = dictionaryWords.any { it.equals(lowerWord, ignoreCase = true) }

        val prefixMatches = dictionaryWords
            .filter { it.lowercase().startsWith(lowerWord) && !it.equals(lowerWord, ignoreCase = true) }
            .take(3)

        val preserveCaps = currentWord.firstOrNull()?.isUpperCase() == true
        fun applyCase(w: String): String =
            if (preserveCaps && w.isNotEmpty()) w.replaceFirstChar { it.uppercaseChar() } else w

        val candidates = linkedSetOf<String>()
        if (shortcutMatch != null) {
            candidates.add(shortcutMatch.word)
        }
        val shortcutTarget = shortcutMatch?.word

        if (exactExists) {
            candidates.add(currentWord)
        } else if (shortcutTarget != null) {
            candidates.add(shortcutTarget)
        }

        prefixMatches.forEach { candidates.add(applyCase(it)) }

        if (candidates.size < 3 && !exactExists) {
            candidates.add(currentWord)
        }

        return SuggestionResult(
            primaryAutoCorrect = shortcutTarget,
            suggestions = candidates.take(3)
        )
    }
}
