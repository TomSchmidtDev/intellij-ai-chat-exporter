package de.tomschmidtdev.copilotexporter.services

import de.tomschmidtdev.copilotexporter.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotCliSessionParserTest {

    private fun line(vararg pairs: Pair<String, Any?>): String {
        fun render(v: Any?): String = when (v) {
            null -> "null"
            is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
            is Map<*, *> -> v.entries.joinToString(",", "{", "}") { "\"${it.key}\":${render(it.value)}" }
            else -> v.toString()
        }
        return pairs.joinToString(",", "{", "}") { "\"${it.first}\":${render(it.second)}" }
    }

    @Test
    fun `parses user and assistant messages in order`() {
        val jsonl = listOf(
            line("type" to "session.start", "data" to mapOf("x" to "y")),
            line("type" to "user.message", "data" to mapOf("content" to "Hallo"), "timestamp" to "2026-07-21T13:50:48.071Z"),
            line("type" to "assistant.message", "data" to mapOf("content" to "Guten Tag"), "timestamp" to "2026-07-21T13:50:49.000Z"),
        ).joinToString("\n")

        val messages = CopilotCliSessionParser.parse(jsonl)

        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("Hallo", messages[0].content)
        assertEquals(0, messages[0].index)
        assertEquals(Role.ASSISTANT, messages[1].role)
        assertEquals("Guten Tag", messages[1].content)
        assertEquals(1, messages[1].index)
    }

    @Test
    fun `skips injected user messages that carry a source field`() {
        val jsonl = listOf(
            line("type" to "user.message", "data" to mapOf("content" to "Echte Frage")),
            line("type" to "user.message", "data" to mapOf("content" to "<skill-context>...", "source" to "skill-using-superpowers")),
            line("type" to "assistant.message", "data" to mapOf("content" to "Antwort")),
        ).joinToString("\n")

        val messages = CopilotCliSessionParser.parse(jsonl)

        assertEquals(2, messages.size)
        assertEquals("Echte Frage", messages[0].content)
        assertEquals("Antwort", messages[1].content)
    }

    @Test
    fun `skips assistant messages with empty content (tool-only turns)`() {
        val jsonl = listOf(
            line("type" to "user.message", "data" to mapOf("content" to "Frage")),
            line("type" to "assistant.message", "data" to mapOf("content" to "")),
            line("type" to "assistant.message", "data" to mapOf("content" to "Finale Antwort")),
        ).joinToString("\n")

        val messages = CopilotCliSessionParser.parse(jsonl)

        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals(Role.ASSISTANT, messages[1].role)
        assertEquals("Finale Antwort", messages[1].content)
    }

    @Test
    fun `ignores unrelated event types`() {
        val jsonl = listOf(
            line("type" to "hook.start", "data" to mapOf("x" to "y")),
            line("type" to "tool.execution_start", "data" to mapOf("toolName" to "grep")),
            line("type" to "system.message", "data" to mapOf("content" to "You are the CLI")),
            line("type" to "permission.requested", "data" to mapOf("x" to "y")),
            line("type" to "user.message", "data" to mapOf("content" to "Nur diese")),
        ).joinToString("\n")

        val messages = CopilotCliSessionParser.parse(jsonl)

        assertEquals(1, messages.size)
        assertEquals("Nur diese", messages[0].content)
    }

    @Test
    fun `parses ISO timestamp to epoch millis`() {
        val jsonl = line(
            "type" to "user.message",
            "data" to mapOf("content" to "Frage"),
            "timestamp" to "2026-07-21T13:50:48.071Z",
        )

        val messages = CopilotCliSessionParser.parse(jsonl)

        assertEquals(1, messages.size)
        assertEquals(1784641848071L, messages[0].timestamp)
    }

    @Test
    fun `returns empty list when there are no relevant events`() {
        val jsonl = listOf(
            line("type" to "session.start", "data" to mapOf("x" to "y")),
            line("type" to "hook.end", "data" to mapOf("x" to "y")),
        ).joinToString("\n")

        assertTrue(CopilotCliSessionParser.parse(jsonl).isEmpty())
    }

    @Test
    fun `skips malformed lines gracefully`() {
        val jsonl = listOf(
            "this is not json {{{",
            "",
            line("type" to "user.message", "data" to mapOf("content" to "Trotzdem gelesen")),
        ).joinToString("\n")

        val messages = CopilotCliSessionParser.parse(jsonl)

        assertEquals(1, messages.size)
        assertEquals("Trotzdem gelesen", messages[0].content)
    }
}
