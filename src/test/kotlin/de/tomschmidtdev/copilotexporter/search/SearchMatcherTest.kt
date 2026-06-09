package de.tomschmidtdev.copilotexporter.search

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SearchMatcherTest {

    private fun matcher(query: String) = SearchMatcher(QueryParser(query).parse()!!)

    // ── matches() ────────────────────────────────────────────────────────────

    @Test fun `term matches case-insensitively`() {
        assertTrue(matcher("react").matches("I love React"))
        assertFalse(matcher("vue").matches("I love React"))
    }

    @Test fun `exact phrase matches`() {
        assertTrue(matcher("\"react hooks\"").matches("React Hooks are great"))
        assertFalse(matcher("\"react hooks\"").matches("React and hooks are great"))
    }

    @Test fun `AND requires both terms`() {
        assertTrue(matcher("react AND hooks").matches("React hooks tutorial"))
        assertFalse(matcher("react AND hooks").matches("React tutorial"))
    }

    @Test fun `OR requires at least one term`() {
        assertTrue(matcher("react OR vue").matches("I use Vue"))
        assertTrue(matcher("react OR vue").matches("I use React"))
        assertFalse(matcher("react OR vue").matches("I use Angular"))
    }

    @Test fun `nested grouping`() {
        val m = matcher("react AND (hooks OR context)")
        assertTrue(m.matches("react hooks"))
        assertTrue(m.matches("react context"))
        assertFalse(m.matches("react only"))
    }

    // ── matchRanges() ─────────────────────────────────────────────────────────

    @Test fun `matchRanges returns correct position`() {
        val ranges = matcher("hook").matchRanges("react hooks")
        assertEquals(listOf(6 until 10), ranges)
    }

    @Test fun `matchRanges finds multiple occurrences`() {
        val ranges = matcher("a").matchRanges("banana")
        assertEquals(listOf(1 until 2, 3 until 4, 5 until 6), ranges)
    }

    @Test fun `matchRanges merges overlapping ranges from OR branches`() {
        // "react OR react" on "react" → two identical ranges, merged to one
        val ranges = matcher("react OR react").matchRanges("react")
        assertEquals(listOf(0 until 5), ranges)
    }

    @Test fun `matchRanges empty for no match`() {
        val ranges = matcher("vue").matchRanges("react hooks")
        assertEquals(emptyList<IntRange>(), ranges)
    }

    // ── buildHighlightedHtml() ────────────────────────────────────────────────

    @Test fun `buildHighlightedHtml wraps matched term`() {
        val html = "react hooks".buildHighlightedHtml(listOf(0 until 5))
        assertEquals("<mark style='background:#6b5900;color:#ffd700'>react</mark> hooks", html)
    }

    @Test fun `buildHighlightedHtml escapes HTML in non-matched segments`() {
        val html = "<b>react</b>".buildHighlightedHtml(listOf(3 until 8))
        assertTrue(html.startsWith("&lt;b&gt;"))
        assertTrue(html.contains("&lt;/b&gt;"))
    }

    @Test fun `buildHighlightedHtml escapes HTML in matched segment`() {
        val html = "<react>".buildHighlightedHtml(listOf(0 until 7))
        assertTrue(html.contains("&lt;react&gt;"))
    }

    @Test fun `buildHighlightedHtml with empty ranges escapes entire string`() {
        assertEquals("a &amp; b", "a & b".buildHighlightedHtml(emptyList()))
    }
}
