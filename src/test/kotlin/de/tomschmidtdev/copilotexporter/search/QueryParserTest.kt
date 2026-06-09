package de.tomschmidtdev.copilotexporter.search

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QueryParserTest {

    private fun parse(input: String) = QueryParser(input).parse()

    @Test fun `empty input returns null`() = assertNull(parse(""))
    @Test fun `blank input returns null`() = assertNull(parse("   "))

    @Test fun `single word returns Term`() =
        assertEquals(SearchNode.Term("react"), parse("react"))

    @Test fun `two words produce implicit And`() =
        assertEquals(
            SearchNode.And(SearchNode.Term("react"), SearchNode.Term("hooks")),
            parse("react hooks")
        )

    @Test fun `explicit AND`() =
        assertEquals(
            SearchNode.And(SearchNode.Term("a"), SearchNode.Term("b")),
            parse("a AND b")
        )

    @Test fun `OR`() =
        assertEquals(
            SearchNode.Or(SearchNode.Term("a"), SearchNode.Term("b")),
            parse("a OR b")
        )

    @Test fun `quoted phrase`() =
        assertEquals(SearchNode.Phrase("react hooks"), parse("\"react hooks\""))

    @Test fun `grouping with parentheses`() =
        assertEquals(
            SearchNode.And(
                SearchNode.Term("react"),
                SearchNode.Or(SearchNode.Term("hooks"), SearchNode.Term("context"))
            ),
            parse("react AND (hooks OR context)")
        )

    @Test fun `three implicit AND terms`() =
        assertEquals(
            SearchNode.And(
                SearchNode.And(SearchNode.Term("a"), SearchNode.Term("b")),
                SearchNode.Term("c")
            ),
            parse("a b c")
        )

    @Test fun `unclosed parenthesis throws`() {
        assertThrows<QueryParseException> { parse("(react OR vue") }
    }

    @Test fun `NOT operator throws`() {
        assertThrows<QueryParseException> { parse("NOT react") }
    }

    @Test fun `unclosed quote throws`() {
        assertThrows<QueryParseException> { parse("\"react hooks") }
    }

    @Test fun `trailing junk throws`() {
        assertThrows<QueryParseException> { parse("react )") }
    }
}
