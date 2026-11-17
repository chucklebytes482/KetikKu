package com.example.data

object FontStyleConverter {

    private const val SANS_SERIF_ITALIC_UPPER_START = 0x1D608 // '𝘈'
    private const val SANS_SERIF_ITALIC_LOWER_START = 0x1D622 // '𝘢'

    fun toArialItalic(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length * 2)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            when (cp) {
                in 'A'.code..'Z'.code -> {
                    sb.appendCodePoint(SANS_SERIF_ITALIC_UPPER_START + (cp - 'A'.code))
                }
                in 'a'.code..'z'.code -> {
                    sb.appendCodePoint(SANS_SERIF_ITALIC_LOWER_START + (cp - 'a'.code))
                }
                else -> sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString()
    }

    fun fromArialItalic(input: String): String {
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

    fun decodeCodePoint(cp: Int): Int = when (cp) {
        in SANS_SERIF_ITALIC_UPPER_START..(SANS_SERIF_ITALIC_UPPER_START + 25) ->
            'A'.code + (cp - SANS_SERIF_ITALIC_UPPER_START)
        in SANS_SERIF_ITALIC_LOWER_START..(SANS_SERIF_ITALIC_LOWER_START + 25) ->
            'a'.code + (cp - SANS_SERIF_ITALIC_LOWER_START)
        else -> cp
    }

    fun isArialItalicCodePoint(cp: Int): Boolean =
        cp in SANS_SERIF_ITALIC_UPPER_START..(SANS_SERIF_ITALIC_LOWER_START + 25)

    fun containsArialItalic(input: String): Boolean {
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            if (isArialItalicCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun lastCodePointCharCount(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 1
        val cp = Character.codePointBefore(textBeforeCursor, textBeforeCursor.length)
        return Character.charCount(cp)
    }

    /**
     * Extracts the word immediately before the cursor, returning:
     * first = UTF-16 character length in [before] (for replaceWordBeforeCursor),
     * second = plain ASCII-normalized word (for dictionary matching).
     */
    fun extractCurrentWordRawAndPlain(before: String): Pair<Int, String> {
        if (before.isEmpty()) return 0 to ""
        var i = before.length
        val reversedPlain = mutableListOf<Int>()
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            val count = Character.charCount(cp)
            val plainCp = decodeCodePoint(cp)
            if (Character.isLetter(plainCp) || plainCp == '\''.code) {
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
            if (Character.isWhitespace(cp)) {
                i -= count
            } else {
                break
            }
        }
        val reversedPlain = mutableListOf<Int>()
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            val count = Character.charCount(cp)
            val plainCp = decodeCodePoint(cp)
            if (Character.isLetter(plainCp)) {
                reversedPlain.add(plainCp)
                i -= count
            } else {
                break
            }
        }
        if (reversedPlain.isEmpty()) return ""
        val sb = StringBuilder(reversedPlain.size)
        for (idx in reversedPlain.size - 1 downTo 0) {
            sb.appendCodePoint(reversedPlain[idx])
        }
        return sb.toString()
    }

    fun lastWordDeleteCharCount(before: String): Int {
        if (before.isEmpty()) return 0
        var i = before.length
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            val count = Character.charCount(cp)
            if (Character.isWhitespace(cp)) {
                i -= count
            } else {
                break
            }
        }
        while (i > 0) {
            val cp = Character.codePointBefore(before, i)
            val count = Character.charCount(cp)
            if (!Character.isWhitespace(cp)) {
                i -= count
            } else {
                break
            }
        }
        return (before.length - i).coerceAtLeast(1)
    }
}
