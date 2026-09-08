package de.tomschmidtdev.copilotexporter.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CopilotCliSessionTest {

    private fun userMsg(text: String, index: Int = 0) = CopilotCliMessage(
        role = Role.USER,
        index = index,
        textBlocks = listOf(text),
        toolCallBlocks = emptyList(),
    )

    private fun assistantMsg(
        text: String = "",
        toolCall: String = "",
        index: Int = 1,
    ) = CopilotCliMessage(
        role = Role.ASSISTANT,
        index = index,
        textBlocks = if (text.isNotEmpty()) listOf(text) else emptyList(),
        toolCallBlocks = if (toolCall.isNotEmpty()) listOf(toolCall) else emptyList(),
    )

    private fun session(vararg messages: CopilotCliMessage) = CopilotCliSession(
        id = "test-id",
        title = "Test Session",
        cwd = "/Users/test/project",
        repository = "TomSchmidtDev/test",
        branch = "main",
        clientName = "github/cli",
        createdAt = 1000L,
        updatedAt = 2000L,
        messages = messages.toList(),
    )

    @Test
    fun `toChatSession includes text blocks by default`() {
        val s = session(userMsg("hello"), assistantMsg("world"))
        val result = s.toChatSession(s.messages, showToolCalls = false)
        assertEquals(2, result.messages.size)
        assertEquals("hello", result.messages[0].content)
        assertEquals("world", result.messages[1].content)
    }

    @Test
    fun `toChatSession excludes tool calls when showToolCalls is false`() {
        val s = session(assistantMsg(text = "done", toolCall = "[Tool: bash]\n{\"command\":\"ls\"}"))
        val result = s.toChatSession(s.messages, showToolCalls = false)
        assertEquals("done", result.messages[0].content)
    }

    @Test
    fun `toChatSession includes tool calls when showToolCalls is true`() {
        val s = session(assistantMsg(text = "done", toolCall = "[Tool: bash]\n{\"command\":\"ls\"}"))
        val result = s.toChatSession(s.messages, showToolCalls = true)
        assertEquals("[Tool: bash]\n{\"command\":\"ls\"}\n\ndone", result.messages[0].content)
    }

    @Test
    fun `toChatSession drops messages that become blank after filtering`() {
        val s = session(assistantMsg(text = "", toolCall = "[Tool: bash]\n{}"))
        val result = s.toChatSession(s.messages, showToolCalls = false)
        assertEquals(0, result.messages.size)
    }

    @Test
    fun `toChatSession respects selectedMessages subset`() {
        val u = userMsg("prompt", index = 0)
        val a = assistantMsg("reply", index = 1)
        val s = session(u, a)
        val result = s.toChatSession(listOf(u), showToolCalls = false)
        assertEquals(1, result.messages.size)
        assertEquals("prompt", result.messages[0].content)
    }

    @Test
    fun `toChatSession copies metadata to ChatSession`() {
        val s = session(userMsg("hi"))
        val result = s.toChatSession(s.messages, showToolCalls = false)
        assertEquals("test-id", result.id)
        assertEquals("Test Session", result.title)
        assertEquals(1000L, result.createdAt)
        assertEquals(2000L, result.lastModifiedAt)
    }

    @Test
    fun `formattedDate falls back to createdAt when updatedAt is zero`() {
        val s = session(userMsg("hi")).copy(updatedAt = 0L, createdAt = 0L)
        assertEquals("Unknown date", s.formattedDate)
    }

    @Test
    fun `messageCount pluralizes correctly`() {
        val single = session(userMsg("hi"))
        val multi = session(userMsg("hi"), assistantMsg("there"))
        assertEquals("1 message", single.messageCount)
        assertEquals("2 messages", multi.messageCount)
    }
}
