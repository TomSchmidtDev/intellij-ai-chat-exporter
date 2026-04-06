package de.tomschmidtdev.copilotexporter.export

import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.ChatSession
import de.tomschmidtdev.copilotexporter.model.Role
import de.tomschmidtdev.copilotexporter.settings.ExporterSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlExporterTest {

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    private val defaultColors = ExporterSettings.State()

    private fun export(
        vararg sessions: ChatSession,
        selected: Map<String, Set<Int>>? = null,
    ) = HtmlExporter.export(sessions.toList(), selected, defaultColors)

    private fun session(
        id: String = "s1",
        title: String = "Test",
        createdAt: Long = 0L,
        vararg messages: ChatMessage,
    ) = ChatSession(id, title, createdAt, messages.toList())

    private fun msg(role: Role, content: String, index: Int = 0) =
        ChatMessage(role, content, index)

    // -------------------------------------------------------------------------
    // Grundstruktur
    // -------------------------------------------------------------------------

    @Test
    fun `output is valid HTML skeleton`() {
        val result = export(session())
        assertTrue(result.contains("<!DOCTYPE html>"))
        assertTrue(result.contains("<html"))
        assertTrue(result.contains("</html>"))
        assertTrue(result.contains("<body>"))
        assertTrue(result.contains("</body>"))
    }

    @Test
    fun `empty session list produces valid HTML without session sections`() {
        val result = HtmlExporter.export(emptyList(), colors = defaultColors)
        assertTrue(result.contains("<!DOCTYPE html>"))
        assertFalse(result.contains("""class="session""""))
    }

    // -------------------------------------------------------------------------
    // HTML-Escaping
    // -------------------------------------------------------------------------

    @Test
    fun `ampersand in session title is escaped`() {
        val result = export(session(title = "A & B"))
        assertTrue(result.contains("A &amp; B"))
        assertFalse(result.contains("A & B"))
    }

    @Test
    fun `less-than and greater-than in content are escaped`() {
        val result = export(session(messages = arrayOf(msg(Role.USER, "x < y > z"))))
        assertTrue(result.contains("x &lt; y &gt; z"))
    }

    @Test
    fun `double quotes in content are escaped`() {
        val result = export(session(messages = arrayOf(msg(Role.USER, """say "hello""""))))
        assertTrue(result.contains("&quot;"))
    }

    // -------------------------------------------------------------------------
    // Code-Block-Parsing
    // -------------------------------------------------------------------------

    @Test
    fun `fenced code block without language renders as pre code`() {
        val content = "```\nval x = 1\n```"
        val result = export(session(messages = arrayOf(msg(Role.USER, content))))
        assertTrue(result.contains("<pre><code>"))
        assertTrue(result.contains("val x = 1"))
    }

    @Test
    fun `fenced code block with language has language class`() {
        val content = "```kotlin\nval x = 1\n```"
        val result = export(session(messages = arrayOf(msg(Role.USER, content))))
        assertTrue(result.contains("""class="language-kotlin""""))
    }

    @Test
    fun `code inside fenced block is html-escaped`() {
        val content = "```xml\n<root/>\n```"
        val result = export(session(messages = arrayOf(msg(Role.USER, content))))
        assertTrue(result.contains("&lt;root/&gt;"))
        assertFalse(result.contains("<root/>"))
    }

    @Test
    fun `text before and after code block is wrapped in paragraphs`() {
        val content = "before\n```\ncode\n```\nafter"
        val result = export(session(messages = arrayOf(msg(Role.USER, content))))
        assertTrue(result.contains("<p>before</p>"))
        assertTrue(result.contains("<p>after</p>"))
    }

    // -------------------------------------------------------------------------
    // Inline-Markdown-Konvertierung
    // -------------------------------------------------------------------------

    @Test
    fun `bold markdown converted to strong`() {
        val result = export(session(messages = arrayOf(msg(Role.USER, "**bold**"))))
        assertTrue(result.contains("<strong>bold</strong>"))
    }

    @Test
    fun `italic markdown converted to em`() {
        val result = export(session(messages = arrayOf(msg(Role.USER, "*italic*"))))
        assertTrue(result.contains("<em>italic</em>"))
    }

    @Test
    fun `inline code markdown converted to code tag`() {
        val result = export(session(messages = arrayOf(msg(Role.USER, "`val x`"))))
        assertTrue(result.contains("<code>val x</code>"))
    }

    // -------------------------------------------------------------------------
    // Nachrichtenfilterung
    // -------------------------------------------------------------------------

    @Test
    fun `selectedMessages filters messages by index`() {
        val messages = arrayOf(
            msg(Role.USER, "INCLUDED_MSG", index = 0),
            msg(Role.ASSISTANT, "EXCLUDED_MSG", index = 1),
        )
        val result = export(
            session(id = "s1", messages = messages),
            selected = mapOf("s1" to setOf(0)),
        )
        assertTrue(result.contains("INCLUDED_MSG"))
        assertFalse(result.contains("EXCLUDED_MSG"))
    }

    @Test
    fun `selectedMessages null exports all messages`() {
        val messages = arrayOf(
            msg(Role.USER, "msg-a", index = 0),
            msg(Role.ASSISTANT, "msg-b", index = 1),
        )
        val result = export(session(messages = messages), selected = null)
        assertTrue(result.contains("msg-a"))
        assertTrue(result.contains("msg-b"))
    }

    // -------------------------------------------------------------------------
    // Farbwerte im CSS
    // -------------------------------------------------------------------------

    @Test
    fun `custom colors are embedded in CSS`() {
        val colors = ExporterSettings.State(background = "#abcdef", text = "#fedcba")
        val result = HtmlExporter.export(emptyList(), colors = colors)
        assertTrue(result.contains("#abcdef"))
        assertTrue(result.contains("#fedcba"))
    }

    // -------------------------------------------------------------------------
    // Rollen-CSS-Klassen
    // -------------------------------------------------------------------------

    @Test
    fun `user message has user-message css class`() {
        val result = export(session(messages = arrayOf(msg(Role.USER, "hi"))))
        assertTrue(result.contains("""class="message user-message""""))
    }

    @Test
    fun `assistant message has copilot-message css class`() {
        val result = export(session(messages = arrayOf(msg(Role.ASSISTANT, "hi"))))
        assertTrue(result.contains("""class="message copilot-message""""))
    }
}
