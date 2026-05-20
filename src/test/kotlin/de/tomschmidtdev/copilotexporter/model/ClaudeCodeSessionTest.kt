package de.tomschmidtdev.copilotexporter.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClaudeCodeSessionTest {

    private fun userMsg(text: String, index: Int = 0) = ClaudeCodeMessage(
        role = Role.USER,
        index = index,
        textBlocks = listOf(text),
        thinkingBlocks = emptyList(),
        toolCallBlocks = emptyList(),
    )

    private fun assistantMsg(
        text: String = "",
        thinking: String = "",
        toolCall: String = "",
        index: Int = 1,
    ) = ClaudeCodeMessage(
        role = Role.CLAUDE,
        index = index,
        textBlocks = if (text.isNotEmpty()) listOf(text) else emptyList(),
        thinkingBlocks = if (thinking.isNotEmpty()) listOf(thinking) else emptyList(),
        toolCallBlocks = if (toolCall.isNotEmpty()) listOf(toolCall) else emptyList(),
    )

    private fun session(vararg messages: ClaudeCodeMessage) = ClaudeCodeSession(
        id = "test-id",
        title = "Test Session",
        projectSlug = "-Users-test-project",
        projectPath = "/Users/test/project",
        lastModified = 1000L,
        messages = messages.toList(),
    )

    @Test
    fun `toChatSession includes text blocks by default`() {
        val s = session(userMsg("hello"), assistantMsg("world"))
        val result = s.toChatSession(s.messages, showThinking = false, showToolCalls = false)
        assertEquals(2, result.messages.size)
        assertEquals("hello", result.messages[0].content)
        assertEquals("world", result.messages[1].content)
    }

    @Test
    fun `toChatSession excludes thinking when showThinking is false`() {
        val s = session(assistantMsg(text = "answer", thinking = "my thoughts"))
        val result = s.toChatSession(s.messages, showThinking = false, showToolCalls = false)
        assertEquals("answer", result.messages[0].content)
    }

    @Test
    fun `toChatSession includes thinking when showThinking is true`() {
        val s = session(assistantMsg(text = "answer", thinking = "my thoughts"))
        val result = s.toChatSession(s.messages, showThinking = true, showToolCalls = false)
        assertEquals("my thoughts\n\nanswer", result.messages[0].content)
    }

    @Test
    fun `toChatSession excludes tool calls when showToolCalls is false`() {
        val s = session(assistantMsg(text = "done", toolCall = "[Tool: Read]\npath.kt"))
        val result = s.toChatSession(s.messages, showThinking = false, showToolCalls = false)
        assertEquals("done", result.messages[0].content)
    }

    @Test
    fun `toChatSession includes tool calls when showToolCalls is true`() {
        val s = session(assistantMsg(text = "done", toolCall = "[Tool: Read]\npath.kt"))
        val result = s.toChatSession(s.messages, showThinking = false, showToolCalls = true)
        assertEquals("[Tool: Read]\npath.kt\n\ndone", result.messages[0].content)
    }

    @Test
    fun `toChatSession drops messages that become blank after filtering`() {
        val s = session(assistantMsg(text = "", thinking = "only thoughts"))
        val result = s.toChatSession(s.messages, showThinking = false, showToolCalls = false)
        assertEquals(0, result.messages.size)
    }

    @Test
    fun `toChatSession respects selectedMessages subset`() {
        val u = userMsg("prompt", index = 0)
        val a = assistantMsg("reply", index = 1)
        val s = session(u, a)
        val result = s.toChatSession(listOf(u), showThinking = false, showToolCalls = false)
        assertEquals(1, result.messages.size)
        assertEquals("prompt", result.messages[0].content)
    }

    @Test
    fun `toChatSession copies metadata to ChatSession`() {
        val s = session(userMsg("hi"))
        val result = s.toChatSession(s.messages, showThinking = false, showToolCalls = false)
        assertEquals("test-id", result.id)
        assertEquals("Test Session", result.title)
        assertEquals(1000L, result.lastModifiedAt)
    }
}
