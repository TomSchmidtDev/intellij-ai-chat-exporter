package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.ObjectMapper
import de.tomschmidtdev.copilotexporter.model.Role
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ClaudeJsonParserTest {

    @TempDir
    lateinit var tmp: Path

    private val mapper = ObjectMapper()

    // -------------------------------------------------------------------------
    // Helpers to build JSONL lines
    // -------------------------------------------------------------------------

    private fun aiTitle(title: String, sessionId: String = "s1") =
        mapper.writeValueAsString(mapOf("type" to "ai-title", "aiTitle" to title, "sessionId" to sessionId))

    private fun customTitle(title: String, sessionId: String = "s1") =
        mapper.writeValueAsString(mapOf("type" to "custom-title", "customTitle" to title, "sessionId" to sessionId))

    private fun userMsg(content: Any, sidechain: Boolean = false) =
        mapper.writeValueAsString(mapOf(
            "type" to "user",
            "isSidechain" to sidechain,
            "uuid" to "u1",
            "parentUuid" to null,
            "message" to mapOf("role" to "user", "content" to content),
        ))

    private fun assistantMsg(blocks: List<Map<String, Any>>, sidechain: Boolean = false) =
        mapper.writeValueAsString(mapOf(
            "type" to "assistant",
            "isSidechain" to sidechain,
            "uuid" to "a1",
            "parentUuid" to "u1",
            "message" to mapOf("role" to "assistant", "content" to blocks),
        ))

    private fun textBlock(text: String) = mapOf("type" to "text", "text" to text)
    private fun thinkingBlock(t: String) = mapOf("type" to "thinking", "thinking" to t)
    private fun toolUseBlock(name: String, input: Map<String, Any> = mapOf("path" to "file.kt")) =
        mapOf("type" to "tool_use", "name" to name, "input" to input)
    private fun toolResultBlock(text: String) = mapOf(
        "type" to "tool_result",
        "content" to listOf(mapOf("type" to "text", "text" to text)),
    )

    private fun writeSession(vararg lines: String): File {
        val file = tmp.resolve("abc123.jsonl").toFile()
        file.writeText(lines.joinToString("\n"))
        return file
    }

    // -------------------------------------------------------------------------
    // Basic parsing
    // -------------------------------------------------------------------------

    @Test
    fun `returns null for empty file`() {
        val file = tmp.resolve("empty.jsonl").toFile().also { it.writeText("") }
        assertNull(ClaudeJsonParser.parse(file))
    }

    @Test
    fun `uses filename as session id`() {
        val file = writeSession(userMsg("hello"), assistantMsg(listOf(textBlock("hi"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals("abc123", session.id)
    }

    @Test
    fun `uses ai-title when present`() {
        val file = writeSession(aiTitle("My Session"), userMsg("hi"), assistantMsg(listOf(textBlock("hey"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals("My Session", session.title)
    }

    @Test
    fun `uses custom-title when present`() {
        val file = writeSession(customTitle("My Session"), userMsg("hi"), assistantMsg(listOf(textBlock("hey"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals("My Session", session.title)
    }

    @Test
    fun `falls back to session id when no ai-title`() {
        val file = writeSession(userMsg("hi"), assistantMsg(listOf(textBlock("hey"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals("abc123", session.title)
    }

    @Test
    fun `parses single turn with string user content`() {
        val file = writeSession(userMsg("tell me about Kotlin"), assistantMsg(listOf(textBlock("Kotlin is great"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals(2, session.messages.size)
        assertEquals(Role.USER, session.messages[0].role)
        assertEquals(listOf("tell me about Kotlin"), session.messages[0].textBlocks)
        assertEquals(Role.CLAUDE, session.messages[1].role)
        assertEquals(listOf("Kotlin is great"), session.messages[1].textBlocks)
    }

    @Test
    fun `parses user content as array of text blocks`() {
        val contentArray = listOf(mapOf("type" to "text", "text" to "array content"))
        val file = writeSession(userMsg(contentArray), assistantMsg(listOf(textBlock("ok"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals(listOf("array content"), session.messages[0].textBlocks)
    }

    @Test
    fun `parses thinking blocks into thinkingBlocks`() {
        val blocks = listOf(thinkingBlock("deep thought"), textBlock("answer"))
        val file = writeSession(userMsg("?"), assistantMsg(blocks))
        val session = ClaudeJsonParser.parse(file)!!
        val assistant = session.messages[1]
        assertEquals(listOf("deep thought"), assistant.thinkingBlocks)
        assertEquals(listOf("answer"), assistant.textBlocks)
    }

    @Test
    fun `parses tool_use blocks into toolCallBlocks`() {
        val blocks = listOf(toolUseBlock("Read"), textBlock("done"))
        val file = writeSession(userMsg("fix it"), assistantMsg(blocks))
        val session = ClaudeJsonParser.parse(file)!!
        val assistant = session.messages[1]
        assertEquals(1, assistant.toolCallBlocks.size)
        assertTrue(assistant.toolCallBlocks[0].startsWith("[Tool: Read]"))
        assertEquals(listOf("done"), assistant.textBlocks)
    }

    @Test
    fun `parses tool_result in user message into toolCallBlocks`() {
        val contentWithResult = listOf(toolResultBlock("file contents here"))
        val file = writeSession(
            userMsg("hi"), assistantMsg(listOf(textBlock("ok"))),
            userMsg(contentWithResult), assistantMsg(listOf(textBlock("done"))),
        )
        val session = ClaudeJsonParser.parse(file)!!
        val secondUser = session.messages[2]
        assertEquals(listOf("file contents here"), secondUser.toolCallBlocks)
    }

    @Test
    fun `skips sidechain user messages`() {
        val file = writeSession(
            userMsg("main prompt"),
            userMsg("abandoned prompt", sidechain = true),
            assistantMsg(listOf(textBlock("reply"))),
        )
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals(2, session.messages.size)
        assertEquals("main prompt", session.messages[0].textBlocks[0])
    }

    @Test
    fun `skips sidechain assistant messages`() {
        val file = writeSession(
            userMsg("prompt"),
            assistantMsg(listOf(textBlock("abandoned")), sidechain = true),
            assistantMsg(listOf(textBlock("real reply"))),
        )
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals(2, session.messages.size)
        assertEquals("real reply", session.messages[1].textBlocks[0])
    }

    @Test
    fun `ignores blank text blocks`() {
        val blocks = listOf(textBlock("   "), textBlock("real text"))
        val file = writeSession(userMsg("hi"), assistantMsg(blocks))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals(listOf("real text"), session.messages[1].textBlocks)
    }

    @Test
    fun `returns null when no messages can be extracted`() {
        val line = mapper.writeValueAsString(mapOf("type" to "attachment"))
        val file = tmp.resolve("nomsgs.jsonl").toFile().also { it.writeText(line) }
        assertNull(ClaudeJsonParser.parse(file))
    }

    @Test
    fun `tolerates malformed JSON lines`() {
        val file = tmp.resolve("bad.jsonl").toFile()
        file.writeText("{ broken\n" + userMsg("hi") + "\n" + assistantMsg(listOf(textBlock("ok"))))
        val session = ClaudeJsonParser.parse(file)!!
        assertEquals(2, session.messages.size)
    }

    // -------------------------------------------------------------------------
    // slugToPath
    // -------------------------------------------------------------------------

    @Test
    fun `slugToPath decodes simple slug`() {
        // Dashes in dir names (e.g. "my-app") are indistinguishable from path separators —
        // slugToPath is best-effort and replaces all dashes with slashes.
        assertEquals("/Users/alice/my/app", ClaudeJsonParser.slugToPath("-Users-alice-my-app"))
    }

    @Test
    fun `slugToPath returns empty string for empty input`() {
        assertEquals("", ClaudeJsonParser.slugToPath(""))
    }
}
