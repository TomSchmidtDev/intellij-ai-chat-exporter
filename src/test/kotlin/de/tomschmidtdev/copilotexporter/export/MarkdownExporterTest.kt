package de.tomschmidtdev.copilotexporter.export

import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.ChatSession
import de.tomschmidtdev.copilotexporter.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownExporterTest {

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    private fun session(
        id: String = "s1",
        title: String = "Test Session",
        createdAt: Long = 0L,
        vararg messages: ChatMessage,
    ) = ChatSession(id, title, createdAt, messages.toList())

    private fun msg(role: Role, content: String, index: Int = 0) =
        ChatMessage(role, content, index)

    // -------------------------------------------------------------------------
    // Leere und randständige Fälle
    // -------------------------------------------------------------------------

    @Test
    fun `empty session list returns empty string`() {
        val result = MarkdownExporter.export(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `session without messages produces only header`() {
        val result = MarkdownExporter.export(listOf(session(title = "Empty")))
        assertTrue(result.contains("## Empty"))
        assertNoMessageBlocks(result)
    }

    @Test
    fun `session with zero timestamp has no date line`() {
        val result = MarkdownExporter.export(listOf(session(createdAt = 0L)))
        assertTrue(!result.contains("·"), "Expected no date/count line for createdAt=0")
    }

    @Test
    fun `session with non-zero timestamp includes date line`() {
        val result = MarkdownExporter.export(listOf(session(createdAt = 1_700_000_000_000L)))
        assertTrue(result.contains("·"))
    }

    // -------------------------------------------------------------------------
    // Nachrichteninhalt und Rollen
    // -------------------------------------------------------------------------

    @Test
    fun `user message has correct prefix`() {
        val result = MarkdownExporter.export(
            listOf(session(messages = arrayOf(msg(Role.USER, "hello"))))
        )
        assertTrue(result.contains("**User:**"))
        assertTrue(result.contains("hello"))
    }

    @Test
    fun `assistant message has correct prefix`() {
        val result = MarkdownExporter.export(
            listOf(session(messages = arrayOf(msg(Role.ASSISTANT, "world"))))
        )
        assertTrue(result.contains("**Copilot:**"))
        assertTrue(result.contains("world"))
    }

    @Test
    fun `unknown role has correct prefix`() {
        val result = MarkdownExporter.export(
            listOf(session(messages = arrayOf(msg(Role.UNKNOWN, "?"))))
        )
        assertTrue(result.contains("**Unknown:**"))
    }

    // -------------------------------------------------------------------------
    // Reihenfolge und Filterung
    // -------------------------------------------------------------------------

    @Test
    fun `messages are exported in index order regardless of list order`() {
        val messages = arrayOf(
            msg(Role.ASSISTANT, "second", index = 1),
            msg(Role.USER, "first", index = 0),
        )
        val result = MarkdownExporter.export(listOf(session(messages = messages)))
        val userPos = result.indexOf("first")
        val assistantPos = result.indexOf("second")
        assertTrue(userPos < assistantPos, "USER (index 0) should appear before ASSISTANT (index 1)")
    }

    @Test
    fun `selectedMessages null exports all messages`() {
        val messages = arrayOf(
            msg(Role.USER, "msg-a", index = 0),
            msg(Role.ASSISTANT, "msg-b", index = 1),
        )
        val result = MarkdownExporter.export(listOf(session(messages = messages)), selectedMessages = null)
        assertTrue(result.contains("msg-a"))
        assertTrue(result.contains("msg-b"))
    }

    @Test
    fun `selectedMessages filters to specified indices only`() {
        val messages = arrayOf(
            msg(Role.USER, "msg-a", index = 0),
            msg(Role.ASSISTANT, "msg-b", index = 1),
            msg(Role.USER, "msg-c", index = 2),
        )
        val result = MarkdownExporter.export(
            listOf(session(id = "s1", messages = messages)),
            selectedMessages = mapOf("s1" to setOf(0, 2)),
        )
        assertTrue(result.contains("msg-a"))
        assertTrue(!result.contains("msg-b"), "Index 1 should be filtered out")
        assertTrue(result.contains("msg-c"))
    }

    @Test
    fun `selectedMessages with no entry for session exports all`() {
        val messages = arrayOf(msg(Role.USER, "hello", index = 0))
        val result = MarkdownExporter.export(
            listOf(session(id = "s1", messages = messages)),
            selectedMessages = mapOf("other-session" to setOf(0)),
        )
        assertTrue(result.contains("hello"))
    }

    // -------------------------------------------------------------------------
    // Escaping
    // -------------------------------------------------------------------------

    @Test
    fun `square brackets in title are escaped`() {
        val result = MarkdownExporter.export(listOf(session(title = "Fix [bug]")))
        assertTrue(result.contains("\\[bug\\]"), "Brackets in title should be escaped")
    }

    @Test
    fun `square brackets in message content are not escaped`() {
        val result = MarkdownExporter.export(
            listOf(session(messages = arrayOf(msg(Role.USER, "see [docs]"))))
        )
        assertTrue(result.contains("[docs]"), "Brackets in content should be preserved")
    }

    // -------------------------------------------------------------------------
    // Mehrere Sessions
    // -------------------------------------------------------------------------

    @Test
    fun `multiple sessions are separated by horizontal rule`() {
        val s1 = session(id = "s1", title = "First")
        val s2 = session(id = "s2", title = "Second")
        val result = MarkdownExporter.export(listOf(s1, s2))
        assertTrue(result.contains("---"))
        val firstPos = result.indexOf("## First")
        val hrPos = result.indexOf("---")
        val secondPos = result.indexOf("## Second")
        assertTrue(firstPos < hrPos && hrPos < secondPos)
    }

    @Test
    fun `single session has no leading horizontal rule`() {
        val result = MarkdownExporter.export(listOf(session(title = "Solo")))
        assertTrue(!result.trimStart().startsWith("---"))
    }

    // -------------------------------------------------------------------------
    // Hilfsmethode
    // -------------------------------------------------------------------------

    private fun assertNoMessageBlocks(result: String) {
        assertTrue(!result.contains("**User:**") && !result.contains("**Copilot:**"))
    }
}
