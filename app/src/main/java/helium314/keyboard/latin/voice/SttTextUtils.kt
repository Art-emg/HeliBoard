// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/** Post-processing for speech-to-text output. */
object SttTextUtils {
    /**
     * Removes punctuation from STT text. When punctuation is removed before an uppercase letter,
     * that letter is lowercased so a new sentence does not start mid-flow.
     */
    @JvmStatic
    fun stripSttPunctuation(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isLetterOrDigit() -> {
                    sb.append(c)
                    i++
                }
                c == '\'' || c == '\u2019' -> {
                    val prev = sb.lastOrNull()
                    val next = text.getOrNull(i + 1)
                    if (prev?.isLetter() == true && next?.isLetter() == true) {
                        sb.append(c)
                    }
                    i++
                }
                c.isWhitespace() -> {
                    if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    i++
                }
                else -> {
                    i++
                    var j = i
                    while (j < text.length && text[j].isWhitespace()) j++
                    if (j < text.length && text[j].isUpperCase() && sb.isNotEmpty()) {
                        if (sb.last() != ' ') sb.append(' ')
                        sb.append(text[j].lowercaseChar())
                        i = j + 1
                    }
                }
            }
        }
        // Keep leading spaces Soniox may send after sentence-ending punctuation.
        return sb.toString().trimEnd()
    }

    /**
     * Inserts a leading space when dictation chunks would otherwise run together after
     * trailing punctuation was stripped from the previous chunk.
     */
    @JvmStatic
    fun ensureLeadingSpaceForDictation(textBeforeCursor: CharSequence?, incoming: String): String {
        if (incoming.isEmpty()) return incoming
        if (incoming.first().isWhitespace()) return incoming
        if (textBeforeCursor.isNullOrEmpty()) return incoming
        if (!incoming.first().isLetterOrDigit()) return incoming
        val last = textBeforeCursor.last()
        if (last.isWhitespace()) return incoming
        if (last.isLetterOrDigit() || last in ".,!?;:" || last == '\u2026') {
            return " $incoming"
        }
        return incoming
    }
}
