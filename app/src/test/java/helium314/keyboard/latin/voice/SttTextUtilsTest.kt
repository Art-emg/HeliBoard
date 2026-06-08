// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SttTextUtilsTest {
    @Test fun removesCommasAndPeriods() {
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello, world"))
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello. world"))
    }

    @Test fun lowercasesWordAfterRemovedPunctuation() {
        assertEquals("Hello world", SttTextUtils.stripSttPunctuation("Hello. World"))
        assertEquals("yes sir", SttTextUtils.stripSttPunctuation("yes, Sir"))
    }

    @Test fun keepsApostropheInContractions() {
        assertEquals("don't stop", SttTextUtils.stripSttPunctuation("don't stop"))
        assertEquals("It's fine", SttTextUtils.stripSttPunctuation("It's fine."))
    }

    @Test fun cyrillic() {
        assertEquals("Привет мир", SttTextUtils.stripSttPunctuation("Привет, мир"))
        assertEquals("Один два", SttTextUtils.stripSttPunctuation("Один. Два"))
    }
}
