// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SttTextUtilsTest {
    private val defaultChars = ".,!?"

    @Test fun removesCommasAndPeriodsBeforeSpace() {
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello, world", defaultChars))
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello. world", defaultChars))
    }

    @Test fun lowercasesWordAfterRemovedPunctuation() {
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello. World", defaultChars))
        assertEquals("yes sir", SttTextUtils.stripSttPunctuation("yes, Sir", defaultChars))
    }

    @Test fun keepsApostropheInContractions() {
        assertEquals("don't stop", SttTextUtils.stripSttPunctuation("don't stop", defaultChars))
        assertEquals("It's fine", SttTextUtils.stripSttPunctuation("It's fine.", defaultChars))
    }

    @Test fun trimsTrailingPunctuationOnly() {
        assertEquals("Hello", SttTextUtils.stripSttPunctuation("Hello.", defaultChars))
    }

    @Test fun cyrillic() {
        assertEquals("Привет мир", SttTextUtils.stripSttPunctuation("Привет, мир", defaultChars))
        assertEquals("Один два", SttTextUtils.stripSttPunctuation("Один. Два", defaultChars))
    }

    @Test fun keepsDashInWords() {
        assertEquals("куда-нибудь", SttTextUtils.stripSttPunctuation("куда-нибудь", defaultChars))
        assertEquals("куда-нибудь", SttTextUtils.stripSttPunctuation("куда-нибудь.", defaultChars))
    }

    @Test fun keepsEmbeddedDotsBetweenLetters() {
        assertEquals("x.y", SttTextUtils.stripSttPunctuation("x.y", defaultChars))
        assertEquals("a.d.v", SttTextUtils.stripSttPunctuation("a.d.v", defaultChars))
        assertEquals("version 3.14", SttTextUtils.stripSttPunctuation("version 3.14", defaultChars))
    }

    @Test fun customStripList() {
        assertEquals("Hello; world", SttTextUtils.stripSttPunctuation("Hello; world", ".,!?"))
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello; world", ".,!?;"))
    }

    @Test fun ensureLeadingSpaceBetweenChunks() {
        assertEquals(" world", SttTextUtils.ensureLeadingSpaceForDictation("Hello", "world"))
        assertEquals("world", SttTextUtils.ensureLeadingSpaceForDictation("", "world"))
        assertEquals("world", SttTextUtils.ensureLeadingSpaceForDictation("Hello ", "world"))
        assertEquals(" next", SttTextUtils.ensureLeadingSpaceForDictation("Hello.", "next"))
    }
}
