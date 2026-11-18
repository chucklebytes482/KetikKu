package com.example.data

object FontStyleConverter {

    private const val SERIF_ITALIC_UPPER_START = 0x1D434 // '𝐴'
    private const val SERIF_ITALIC_LOWER_START = 0x1D44E // '𝑎'

    fun toSerifItalic(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length * 2)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            when (cp) {
                in 'A'.code..'Z'.code -> {
                    sb.appendCodePoint(SERIF_ITALIC_UPPER_START + (cp - 'A'.code))
                }
                in 'a'.code..'z'.code -> {
                    sb.appendCodePoint(SERIF_ITALIC_LOWER_START + (cp - 'a'.code))
                }
                else -> sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString()
    }

    fun toArialItalic(input: String): String = toSerifItalic(input)

    fun fromSerifItalic(input: String): String {
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

    fun fromArialItalic(input: String): String = fromSerifItalic(input)

    fun decodeCodePoint(cp: Int): Int = when (cp) {
        in SERIF_ITALIC_UPPER_START..(SERIF_ITALIC_UPPER_START + 25) ->
            'A'.code + (cp - SERIF_ITALIC_UPPER_START)
        in SERIF_ITALIC_LOWER_START..(SERIF_ITALIC_LOWER_START + 25) ->
            'a'.code + (cp - SERIF_ITALIC_LOWER_START)
        else -> cp
    }

    fun isSerifItalicCodePoint(cp: Int): Boolean =
        cp in SERIF_ITALIC_UPPER_START..(SERIF_ITALIC_LOWER_START + 25)

    fun isArialItalicCodePoint(cp: Int): Boolean = isSerifItalicCodePoint(cp)

    fun containsSerifItalic(input: String): Boolean {
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            if (isSerifItalicCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun containsArialItalic(input: String): Boolean = containsSerifItalic(input)

    fun lastCodePointCharCount(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 1
        val cp = Character.codePointBefore(textBeforeCursor, textBeforeCursor.length)
        return Character.charCount(cp)
    }

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
            val plainCp = decodeCodePoint(cp)
            if (Character.isLetter(plainCp) || plainCp == '\''.code) {
                return decodeCodePoint(cp).toChar().toString()
            }
            i -= count
        }
        return ""
    }
}
