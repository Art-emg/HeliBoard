// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import helium314.keyboard.latin.settings.Defaults

/** Post-processing for speech-to-text output. */
object SttTextUtils {
    /**
     * Parses the user setting: each non-whitespace character is stripped when rules allow.
     */
    @JvmStatic
    fun parseStripChars(spec: String?): Set<Char> =
        spec?.filterNot { it.isWhitespace() }?.toSet() ?: emptySet()

    /**
     * Removes configured punctuation from STT text.
     *
     * A listed character is removed only when it is the last character in the chunk or is
     * followed by whitespace. Characters embedded between letters or digits (e.g. `x.y`, `a.d.v`,
     * `куда-нибудь` when `-` is not listed) are kept.
     *
     * When punctuation is removed before an uppercase letter, that letter is lowercased so a new
     * sentence does not start mid-flow.
     */
    @JvmStatic
    fun stripSttPunctuation(text: String): String =
        stripSttPunctuation(text, parseStripChars(Defaults.PREF_SONIOX_STRIP_PUNCTUATION_CHARS))

    @JvmStatic
    fun stripSttPunctuation(text: String, charsToStripSpec: String?): String =
        stripSttPunctuation(text, parseStripChars(charsToStripSpec))

    @JvmStatic
    fun stripSttPunctuation(text: String, charsToStrip: Set<Char>): String {
        if (text.isEmpty() || charsToStrip.isEmpty()) return text
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
                c in charsToStrip -> {
                    val prev = text.getOrNull(i - 1)
                    val next = text.getOrNull(i + 1)
                    val embeddedInWord = prev?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true
                    val atEnd = i == text.length - 1
                    val followedBySpace = next?.isWhitespace() == true
                    if (!embeddedInWord && (atEnd || followedBySpace)) {
                        i++
                        var j = i
                        while (j < text.length && text[j].isWhitespace()) j++
                        if (j < text.length && text[j].isUpperCase() && sb.isNotEmpty()) {
                            if (sb.last() != ' ') sb.append(' ')
                            sb.append(text[j].lowercaseChar())
                            i = j + 1
                        }
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
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

    /** Composing preview for live dictation in the editor (underlined until finalized). */
    @JvmStatic
    fun asVoicePartialComposingText(text: String): CharSequence {
        if (text.isEmpty()) return text
        val spannable = SpannableString(text)
        spannable.setSpan(
            UnderlineSpan(),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING,
        )
        return spannable
    }
}
