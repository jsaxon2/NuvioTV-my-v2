package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BadgeRequiredLiteralsTest {

    @Test fun `a plain word is required`() {
        assertEquals(setOf("atmos"), BadgeRequiredLiterals.of("(?i)\\batmos\\b"))
    }

    @Test fun `alternatives give one word each`() {
        // An optional character ends the word, so each alternative is left with "dts".
        assertEquals(setOf("dts"), BadgeRequiredLiterals.of("(?i)\\b(dts-?hd|dts:?x)\\b"))
        assertEquals(setOf("2160p", "4k", "uhd"), BadgeRequiredLiterals.of("(?i)\\b(2160p|4k|uhd)\\b"))
    }

    @Test fun `optional parts are not required`() {
        val words = BadgeRequiredLiterals.of("(?i)\\bpass[\\s._-]?through\\b")!!
        assertEquals(setOf("through"), words)
    }

    @Test fun `an empty alternative makes its group optional, not the whole rule`() {
        assertEquals(setOf("dl"), BadgeRequiredLiterals.of("(?i)(web|)dl"))
    }

    @Test fun `positive lookaheads count, negative ones do not`() {
        assertEquals(setOf("remux"), BadgeRequiredLiterals.of("(?i)(?<![\\s\\S])(?=[\\s\\S]*?(?:remux))"))
        assertEquals(setOf("hdr"), BadgeRequiredLiterals.of("(?i)\\bhdr\\b(?!10)"))
    }

    @Test fun `rules it cannot read safely get no words`() {
        assertNull(BadgeRequiredLiterals.of("(?i)\\b5[\\s._-]1\\b"))
        assertNull(BadgeRequiredLiterals.of("(?i)(\\w+)\\1"))
        assertNull(BadgeRequiredLiterals.of("(?x) a b c"))
    }

    @Test fun `a match always contains one of the words`() {
        val cases = listOf(
            "(?i)\\b(dts-?hd|dts:?x)\\b" to listOf("Movie.2020.DTS-HD.MA.mkv", "film DTS:X 7.1", "dtshd"),
            "(?i)\\bpass[\\s._-]?through\\b" to listOf("PASS THROUGH", "Passthrough", "pass-through"),
            "(?i)\\b(?:web[\\s._-]?dl|webrip)\\b" to listOf("Show.S01E01.WEB-DL", "WEBRip x264", "web dl"),
            "(?i)\\b(2160p|4k|uhd)\\b" to listOf("2160p", "UHD BluRay", "4K HDR")
        )
        cases.forEach { (pattern, texts) ->
            val words = BadgeRequiredLiterals.of(pattern)!!
            val regex = java.util.regex.Pattern.compile(pattern)
            texts.forEach { text ->
                assertTrue("$pattern should match $text", regex.matcher(text).find())
                val lower = text.lowercase(Locale.ROOT)
                assertTrue("$text should contain one of $words", words.any { lower.contains(it) })
            }
        }
    }
}
