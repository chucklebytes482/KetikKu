package com.example.data

object FontStyleConverter {

    // Mathematical Monospace Unicode code points: 𝙰-𝚉, 𝚊-𝚣, 𝟶-𝟿
    private const val MONO_UPPER_START = 0x1D670 // '𝙰'
    private const val MONO_LOWER_START = 0x1D68A // '𝚊'
    private const val MONO_DIGIT_START = 0x1D7F6 // '𝟶'

    // Legacy Serif Italic range retained for graceful decoding of previously typed text
    private const val LEGACY_SERIF_ITALIC_UPPER_START = 0x1D434
    private const val LEGACY_SERIF_ITALIC_LOWER_START = 0x1D44E

    fun toMonospace(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length * 2)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            when (cp) {
                in 'A'.code..'Z'.code -> {
                    sb.appendCodePoint(MONO_UPPER_START + (cp - 'A'.code))
                }
                in 'a'.code..'z'.code -> {
                    sb.appendCodePoint(MONO_LOWER_START + (cp - 'a'.code))
                }
                in '0'.code..'9'.code -> {
                    sb.appendCodePoint(MONO_DIGIT_START + (cp - '0'.code))
                }
                else -> sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString()
    }

    fun toSerifItalic(input: String): String = toMonospace(input)

    fun toArialItalic(input: String): String = toMonospace(input)

    fun fromMonospace(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            sb.appendCodePoint(decodeCodePoint(cp))
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    fun fromSerifItalic(input: String): String = fromMonospace(input)

    fun fromArialItalic(input: String): String = fromMonospace(input)

    fun decodeCodePoint(cp: Int): Int = when (cp) {
        in MONO_UPPER_START..(MONO_UPPER_START + 25) ->
            'A'.code + (cp - MONO_UPPER_START)
        in MONO_LOWER_START..(MONO_LOWER_START + 25) ->
            'a'.code + (cp - MONO_LOWER_START)
        in MONO_DIGIT_START..(MONO_DIGIT_START + 9) ->
            '0'.code + (cp - MONO_DIGIT_START)
        in LEGACY_SERIF_ITALIC_UPPER_START..(LEGACY_SERIF_ITALIC_UPPER_START + 25) ->
            'A'.code + (cp - LEGACY_SERIF_ITALIC_UPPER_START)
        in LEGACY_SERIF_ITALIC_LOWER_START..(LEGACY_SERIF_ITALIC_LOWER_START + 25) ->
            'a'.code + (cp - LEGACY_SERIF_ITALIC_LOWER_START)
        else -> cp
    }

    fun isMonospaceCodePoint(cp: Int): Boolean =
        cp in MONO_UPPER_START..(MONO_LOWER_START + 25) ||
            cp in MONO_DIGIT_START..(MONO_DIGIT_START + 9)

    fun isSerifItalicCodePoint(cp: Int): Boolean = isMonospaceCodePoint(cp)

    fun isArialItalicCodePoint(cp: Int): Boolean = isMonospaceCodePoint(cp)

    fun containsMonospace(input: String): Boolean {
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            if (isMonospaceCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun containsSerifItalic(input: String): Boolean = containsMonospace(input)

    fun containsArialItalic(input: String): Boolean = containsMonospace(input)

    fun lastCodePointCharCount(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 1
        val cp = Character.codePointBefore(textBeforeCursor, textBeforeCursor.length)
        return Character.charCount(cp)
    }

    private fun isWordTokenCodePoint(plainCp: Int): Boolean =
        Character.isLetterOrDigit(plainCp) ||
            plainCp == '\''.code ||
            plainCp == '@'.code ||
            plainCp == '_'.code ||
            plainCp == '+'.code

    fun extractCurrentWordRawAndPlain(before: String, maxCodePoints: Int = 30): Pair<Int, String> {
        if (before.isEmpty() || maxCodePoints <= 0) return 0 to ""
        var i = before.length
        val reversedPlain = mutableListOf<Int>()
        while (i > 0 && reversedPlain.size < maxCodePoints) {
            val cp = Character.codePointBefore(before, i)
            val count = Character.charCount(cp)
            val plainCp = decodeCodePoint(cp)
            if (isWordTokenCodePoint(plainCp)) {
                reversedPlain.add(plainCp)
                i -= count
            } else {
                break
            }
        }
        val rawLen = before.length - i
        if (rawLen == 0) return 0 to ""
        val sb = StringBuilder(reversedPlain.size)
        for (idx in reversedPlain.size - 1 downTo 0) {
            sb.appendCodePoint(reversedPlain[idx])
        }
        return rawLen to sb.toString()
    }

    fun extractPreviousWordPlain(before: String, currentWordRawLen: Int): String {
        var i = (before.length - currentWordRawLen).coerceAtLeast(0)
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            val count = Character.charCount(cp)
            val plainCp = decodeCodePoint(cp)
            if (isWordTokenCodePoint(plainCp)) {
                return decodeCodePoint(cp).toChar().toString()
            }
            i -= count
        }
        return ""
    }
}
