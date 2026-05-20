# Claude Code Session Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Claude Code" tab to the plugin that reads `~/.claude/projects/**/*.jsonl` files and exports Claude Code chat sessions to Markdown or HTML.

**Architecture:** Parallel to the existing Copilot structure — new `ClaudeCodeMessage`/`ClaudeCodeSession` models, `ClaudeJsonParser` for JSONL parsing, `ClaudeCodeReaderService` for file discovery, and `ClaudeCodePanel` for the UI tab. The existing `HtmlExporter` and `MarkdownExporter` are reused unchanged except for one compile-required addition per file (a new `Role.CLAUDE` enum value in exhaustive `when` expressions).

**Tech Stack:** Kotlin, Jackson ObjectMapper (already in classpath), standard `java.io.File`, IntelliJ Platform Swing UI (`CheckBoxList`, `JBSplitter`, `Task.Backgroundable`).

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `src/main/kotlin/.../model/ChatMessage.kt` | Add `CLAUDE("Claude")` to Role enum |
| Modify | `src/main/kotlin/.../export/HtmlExporter.kt` | Add `Role.CLAUDE` case to exhaustive `when` |
| Modify | `src/main/kotlin/.../export/MarkdownExporter.kt` | Add `Role.CLAUDE` case to exhaustive `when` |
| Create | `src/main/kotlin/.../model/ClaudeCodeMessage.kt` | `ClaudeCodeMessage` + `ClaudeCodeSession` data classes |
| Create | `src/main/kotlin/.../services/ClaudeJsonParser.kt` | Parse a single `.jsonl` file into `ClaudeCodeSession` |
| Create | `src/main/kotlin/.../services/ClaudeCodeReaderService.kt` | Discover all sessions under `~/.claude/projects/` |
| Create | `src/main/kotlin/.../ui/ClaudeCodePanel.kt` | The new "Claude Code" tab |
| Modify | `src/main/kotlin/.../ui/ExporterToolWindowFactory.kt` | Wrap both panels in `JTabbedPane` |
| Modify | `src/main/resources/META-INF/plugin.xml` | Register `ClaudeCodeReaderService` |
| Create | `src/test/kotlin/.../model/ClaudeCodeSessionTest.kt` | Unit tests for `ClaudeCodeSession.toChatSession()` |
| Create | `src/test/kotlin/.../services/ClaudeJsonParserTest.kt` | Unit tests for `ClaudeJsonParser.parse()` |
| Modify | `build.gradle.kts` | Version `1.5.6` → `1.6.0`, update `changeNotes` |
| Modify | `CHANGELOG.md` | New `## [1.6.0]` section |

All paths are rooted at `src/main/kotlin/de/tomschmidtdev/copilotexporter/` and `src/test/kotlin/de/tomschmidtdev/copilotexporter/`.

---

## Task 1: Add Role.CLAUDE and fix exhaustive when expressions

`HtmlExporter` and `MarkdownExporter` both use `when(message.role)` as an **expression** — Kotlin requires all enum values to be handled. Adding `CLAUDE` to the enum without updating those files causes a compile error.

**Files:**
- Modify: `model/ChatMessage.kt`
- Modify: `export/HtmlExporter.kt`
- Modify: `export/MarkdownExporter.kt`

- [ ] **Step 1: Add CLAUDE to the Role enum in `ChatMessage.kt`**

In `src/main/kotlin/de/tomschmidtdev/copilotexporter/model/ChatMessage.kt`, change:

```kotlin
enum class Role(val displayName: String) {
    USER("User"),
    ASSISTANT("Copilot"),
    UNKNOWN("Unknown"),
    ;
```

to:

```kotlin
enum class Role(val displayName: String) {
    USER("User"),
    ASSISTANT("Copilot"),
    CLAUDE("Claude"),
    UNKNOWN("Unknown"),
    ;
```

- [ ] **Step 2: Add Role.CLAUDE case to HtmlExporter**

In `src/main/kotlin/de/tomschmidtdev/copilotexporter/export/HtmlExporter.kt`, change `appendMessage`:

```kotlin
private fun StringBuilder.appendMessage(message: ChatMessage) {
    val cssClass = when (message.role) {
        Role.USER -> "message user-message"
        Role.ASSISTANT -> "message copilot-message"
        Role.CLAUDE -> "message assistant-message"
        Role.UNKNOWN -> "message unknown-message"
    }
    val label = message.role.displayName
    // rest unchanged
```

- [ ] **Step 3: Add Role.CLAUDE case to MarkdownExporter**

In `src/main/kotlin/de/tomschmidtdev/copilotexporter/export/MarkdownExporter.kt`, change `appendMessage`:

```kotlin
private fun StringBuilder.appendMessage(message: ChatMessage) {
    val prefix = when (message.role) {
        Role.USER -> "**User:**"
        Role.ASSISTANT -> "**Copilot:**"
        Role.CLAUDE -> "**Claude:**"
        Role.UNKNOWN -> "**Unknown:**"
    }
    // rest unchanged
```

- [ ] **Step 4: Run all existing tests**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test
```

Expected: all tests pass (no compile errors, no behavior change for Copilot exports).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/tomschmidtdev/copilotexporter/model/ChatMessage.kt \
        src/main/kotlin/de/tomschmidtdev/copilotexporter/export/HtmlExporter.kt \
        src/main/kotlin/de/tomschmidtdev/copilotexporter/export/MarkdownExporter.kt
git commit -m "feat: add Role.CLAUDE enum value for Claude Code export"
```

---

## Task 2: ClaudeCodeMessage and ClaudeCodeSession data classes

`ClaudeCodeMessage` stores typed content blocks separately so the panel can filter by type (text / thinking / tool calls) without losing data. `ClaudeCodeSession` knows how to convert itself to the `ChatSession` the exporters expect.

**Files:**
- Create: `model/ClaudeCodeMessage.kt`
- Create: `src/test/kotlin/.../model/ClaudeCodeSessionTest.kt`

- [ ] **Step 1: Write failing tests for ClaudeCodeSession.toChatSession()**

Create `src/test/kotlin/de/tomschmidtdev/copilotexporter/model/ClaudeCodeSessionTest.kt`:

```kotlin
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
        text: String,
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
        // Assistant message with only thinking, shown with showThinking=false → no text → dropped
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test --tests "*.ClaudeCodeSessionTest"
```

Expected: compile error (ClaudeCodeMessage and ClaudeCodeSession do not exist yet).

- [ ] **Step 3: Create `model/ClaudeCodeMessage.kt`**

Create `src/main/kotlin/de/tomschmidtdev/copilotexporter/model/ClaudeCodeMessage.kt`:

```kotlin
package de.tomschmidtdev.copilotexporter.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One user or assistant turn in a Claude Code session.
 *
 * Content is split by block type so the UI can show/hide thinking and tool
 * calls independently without re-parsing.
 *
 * Block ordering on export: toolCallBlocks (if shown) → thinkingBlocks (if shown) → textBlocks.
 * Tool calls come first because they are the context for the text that follows.
 */
data class ClaudeCodeMessage(
    val role: Role,
    val index: Int,
    val textBlocks: List<String>,
    val thinkingBlocks: List<String>,
    val toolCallBlocks: List<String>,
) {
    fun toExportText(showThinking: Boolean, showToolCalls: Boolean): String {
        val parts = mutableListOf<String>()
        if (showToolCalls) parts.addAll(toolCallBlocks)
        if (showThinking) parts.addAll(thinkingBlocks)
        parts.addAll(textBlocks)
        return parts.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    val previewText: String
        get() = textBlocks.firstOrNull()?.take(80) ?: thinkingBlocks.firstOrNull()?.take(80) ?: ""
}

/**
 * One Claude Code chat session, read from a single .jsonl file.
 *
 * @param projectSlug  Raw directory name under ~/.claude/projects/ (e.g. "-Users-alice-my-app")
 * @param projectPath  Human-readable best-guess path (e.g. "/Users/alice/my-app")
 */
data class ClaudeCodeSession(
    val id: String,
    val title: String,
    val projectSlug: String,
    val projectPath: String,
    val lastModified: Long,
    val messages: List<ClaudeCodeMessage>,
) {
    val formattedDate: String
        get() {
            if (lastModified == 0L) return "Unknown date"
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            return formatter.format(Instant.ofEpochMilli(lastModified))
        }

    val messageCount: String
        get() = "${messages.size} message${if (messages.size != 1) "s" else ""}"

    /**
     * Converts selected messages to a ChatSession suitable for HtmlExporter / MarkdownExporter.
     *
     * @param selectedMessages  Subset of this session's messages to include (in order).
     * @param showThinking      Whether to include thinking blocks in the export text.
     * @param showToolCalls     Whether to include tool-call and tool-result blocks.
     */
    fun toChatSession(
        selectedMessages: List<ClaudeCodeMessage>,
        showThinking: Boolean,
        showToolCalls: Boolean,
    ): ChatSession {
        val chatMessages = selectedMessages.mapIndexed { i, msg ->
            ChatMessage(
                role = msg.role,
                content = msg.toExportText(showThinking, showToolCalls),
                index = i,
            )
        }.filter { it.content.isNotBlank() }
        return ChatSession(
            id = id,
            title = title,
            createdAt = lastModified,
            messages = chatMessages,
            lastModifiedAt = lastModified,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test --tests "*.ClaudeCodeSessionTest"
```

Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/tomschmidtdev/copilotexporter/model/ClaudeCodeMessage.kt \
        src/test/kotlin/de/tomschmidtdev/copilotexporter/model/ClaudeCodeSessionTest.kt
git commit -m "feat: add ClaudeCodeMessage and ClaudeCodeSession models"
```

---

## Task 3: ClaudeJsonParser — parse a single .jsonl file

Each `.jsonl` file is one session. The parser reads all lines, collects `ai-title`, `user`, and `assistant` entries (skipping `isSidechain=true` lines), and assembles a `ClaudeCodeSession`.

**JSONL entry shapes (reference):**
```
{"type":"ai-title","aiTitle":"...","sessionId":"..."}
{"type":"user","isSidechain":false,"uuid":"...","message":{"role":"user","content":"text or array"}}
{"type":"assistant","isSidechain":false,"uuid":"...","message":{"role":"assistant","content":[blocks]}}
```

**Content block shapes:**
- User string content: `"content": "plain text"`
- User array content: `[{"type":"text","text":"..."},{"type":"tool_result","content":[{"type":"text","text":"..."}]}]`
- Assistant blocks: `[{"type":"text","text":"..."},{"type":"thinking","thinking":"..."},{"type":"tool_use","name":"Read","input":{"file_path":"..."}}]`

**Files:**
- Create: `services/ClaudeJsonParser.kt`
- Create: `src/test/kotlin/.../services/ClaudeJsonParserTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/de/tomschmidtdev/copilotexporter/services/ClaudeJsonParserTest.kt`:

```kotlin
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
        val file = writeSession(userMsg("hi"), assistantMsg(listOf(textBlock("ok"))),
            userMsg(contentWithResult), assistantMsg(listOf(textBlock("done"))))
        val session = ClaudeJsonParser.parse(file)!!
        val secondUser = session.messages[2]
        assertEquals(listOf("file contents here"), secondUser.toolCallBlocks)
    }

    @Test
    fun `skips sidechain user messages`() {
        val file = writeSession(
            userMsg("main prompt"),
            userMsg("abandoned prompt", sidechain = true),
            assistantMsg(listOf(textBlock("reply")))
        )
        val session = ClaudeJsonParser.parse(file)!!
        // sidechain user message must not appear
        assertEquals(2, session.messages.size)
        assertEquals("main prompt", session.messages[0].textBlocks[0])
    }

    @Test
    fun `skips sidechain assistant messages`() {
        val file = writeSession(
            userMsg("prompt"),
            assistantMsg(listOf(textBlock("abandoned")), sidechain = true),
            assistantMsg(listOf(textBlock("real reply")))
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
        // attachment entry only, no user/assistant
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
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test --tests "*.ClaudeJsonParserTest"
```

Expected: compile error (ClaudeJsonParser does not exist yet).

- [ ] **Step 3: Create `services/ClaudeJsonParser.kt`**

Create `src/main/kotlin/de/tomschmidtdev/copilotexporter/services/ClaudeJsonParser.kt`:

```kotlin
package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeMessage
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import de.tomschmidtdev.copilotexporter.model.Role
import java.io.File

object ClaudeJsonParser {

    private val mapper = ObjectMapper()

    /**
     * Parses a single Claude Code session .jsonl file.
     *
     * Returns null if the file contains no extractable user/assistant messages.
     *
     * @param file  The .jsonl file (filename without extension = session UUID).
     */
    fun parse(file: File): ClaudeCodeSession? {
        val lines = try { file.readLines() } catch (e: Exception) { return null }
        if (lines.isEmpty()) return null

        val sessionId = file.nameWithoutExtension
        var title = sessionId
        val messages = mutableListOf<ClaudeCodeMessage>()
        var index = 0

        for (line in lines) {
            val entry = try { mapper.readTree(line) } catch (e: Exception) { continue }
            if (entry == null || !entry.isObject) continue

            when (entry["type"]?.asText()) {
                "ai-title" -> {
                    entry["aiTitle"]?.asText()?.takeIf { it.isNotBlank() }?.let { title = it }
                }
                "user" -> {
                    if (entry["isSidechain"]?.asBoolean() == true) continue
                    val content = entry["message"]?.get("content") ?: continue
                    parseUserMessage(content, index)?.let { messages.add(it); index++ }
                }
                "assistant" -> {
                    if (entry["isSidechain"]?.asBoolean() == true) continue
                    val content = entry["message"]?.get("content") ?: continue
                    parseAssistantMessage(content, index)?.let { messages.add(it); index++ }
                }
            }
        }

        if (messages.isEmpty()) return null

        return ClaudeCodeSession(
            id = sessionId,
            title = title,
            projectSlug = file.parentFile?.name ?: "",
            projectPath = slugToPath(file.parentFile?.name ?: ""),
            lastModified = file.lastModified(),
            messages = messages,
        )
    }

    private fun parseUserMessage(content: JsonNode, index: Int): ClaudeCodeMessage? {
        val textBlocks = mutableListOf<String>()
        val toolResultBlocks = mutableListOf<String>()

        if (content.isTextual) {
            content.asText().trim().takeIf { it.isNotBlank() }?.let { textBlocks.add(it) }
        } else if (content.isArray) {
            for (block in content) {
                when (block["type"]?.asText()) {
                    "text" -> block["text"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { textBlocks.add(it) }
                    "tool_result" -> {
                        block["content"]?.takeIf { it.isArray }?.forEach { rb ->
                            rb["text"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { toolResultBlocks.add(it) }
                        }
                    }
                }
            }
        }

        if (textBlocks.isEmpty() && toolResultBlocks.isEmpty()) return null
        return ClaudeCodeMessage(
            role = Role.USER,
            index = index,
            textBlocks = textBlocks,
            thinkingBlocks = emptyList(),
            toolCallBlocks = toolResultBlocks,
        )
    }

    private fun parseAssistantMessage(content: JsonNode, index: Int): ClaudeCodeMessage? {
        if (!content.isArray) return null
        val textBlocks = mutableListOf<String>()
        val thinkingBlocks = mutableListOf<String>()
        val toolCallBlocks = mutableListOf<String>()

        for (block in content) {
            when (block["type"]?.asText()) {
                "text" -> block["text"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { textBlocks.add(it) }
                "thinking" -> block["thinking"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { thinkingBlocks.add(it) }
                "tool_use" -> {
                    val name = block["name"]?.asText() ?: continue
                    val input = block["input"]?.toString() ?: "{}"
                    toolCallBlocks.add("[Tool: $name]\n$input")
                }
            }
        }

        if (textBlocks.isEmpty() && thinkingBlocks.isEmpty() && toolCallBlocks.isEmpty()) return null
        return ClaudeCodeMessage(
            role = Role.CLAUDE,
            index = index,
            textBlocks = textBlocks,
            thinkingBlocks = thinkingBlocks,
            toolCallBlocks = toolCallBlocks,
        )
    }

    /**
     * Best-effort decode of a project slug back to a file path.
     *
     * Slug format: path separators replaced with `-` (e.g. `-Users-alice-my-app`).
     * We strip the leading dash and replace remaining dashes with `/` for display.
     * This is ambiguous (directory names with `-` can't be distinguished from path
     * separators), but is good enough for the project filter dropdown.
     */
    fun slugToPath(slug: String): String {
        if (slug.isEmpty()) return slug
        val stripped = slug.trimStart('-')
        return "/$stripped".replace("/-", "/")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test --tests "*.ClaudeJsonParserTest"
```

Expected: all 14 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/de/tomschmidtdev/copilotexporter/services/ClaudeJsonParser.kt \
        src/test/kotlin/de/tomschmidtdev/copilotexporter/services/ClaudeJsonParserTest.kt
git commit -m "feat: add ClaudeJsonParser for reading .jsonl session files"
```

---

## Task 4: ClaudeCodeReaderService — discover all sessions

Reads `~/.claude/projects/` on all platforms, lists all project directories, parses every `.jsonl` file, and returns the sessions optionally filtered by project slug.

**Files:**
- Create: `services/ClaudeCodeReaderService.kt`

(No unit tests — file-system discovery depends on the local machine state.)

- [ ] **Step 1: Create `services/ClaudeCodeReaderService.kt`**

Create `src/main/kotlin/de/tomschmidtdev/copilotexporter/services/ClaudeCodeReaderService.kt`:

```kotlin
package de.tomschmidtdev.copilotexporter.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import java.io.File

@Service(Service.Level.APPLICATION)
class ClaudeCodeReaderService {

    private val log = logger<ClaudeCodeReaderService>()

    /**
     * Reads all Claude Code sessions from ~/.claude/projects/.
     *
     * @param projectSlugFilter  When non-null, only sessions in this project directory are returned.
     */
    fun readSessions(projectSlugFilter: String? = null): List<ClaudeCodeSession> {
        val projectsDir = claudeProjectsDir() ?: return emptyList()
        if (!projectsDir.exists()) {
            log.info("Claude Code projects directory not found: ${projectsDir.absolutePath}")
            return emptyList()
        }

        val projectDirs = projectsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.let { dirs ->
                if (projectSlugFilter != null) dirs.filter { it.name == projectSlugFilter } else dirs
            }
            ?: return emptyList()

        return projectDirs.flatMap { projectDir ->
            projectDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".jsonl") }
                ?.mapNotNull { file ->
                    try {
                        ClaudeJsonParser.parse(file)
                    } catch (e: Exception) {
                        log.warn("Failed to parse ${file.absolutePath}: ${e.message}")
                        null
                    }
                }
                ?: emptyList()
        }.sortedByDescending { it.lastModified }
    }

    /** Returns all distinct project slugs that have at least one session file. */
    fun listProjectSlugs(): List<String> {
        val projectsDir = claudeProjectsDir() ?: return emptyList()
        if (!projectsDir.exists()) return emptyList()
        return projectsDir.listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.any { f -> f.name.endsWith(".jsonl") } == true }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    private fun claudeProjectsDir(): File? {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()
        val claudeDir = when {
            os.contains("win") -> System.getenv("USERPROFILE")?.let { File(it, ".claude") }
                ?: File(home, ".claude")
            else -> File(home, ".claude")
        }
        return File(claudeDir, "projects")
    }
}
```

- [ ] **Step 2: Register the service in plugin.xml**

In `src/main/resources/META-INF/plugin.xml`, add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<applicationService
        serviceImplementation="de.tomschmidtdev.copilotexporter.services.ClaudeCodeReaderService"/>
```

- [ ] **Step 3: Verify the project compiles**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/de/tomschmidtdev/copilotexporter/services/ClaudeCodeReaderService.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add ClaudeCodeReaderService to discover ~/.claude/projects/ sessions"
```

---

## Task 5: ClaudeCodePanel — the UI tab

Analogous to `ExporterPanel`. Key differences: project filter dropdown instead of All-IDEs checkbox, four message-type toggles instead of two, and export builds a `ChatSession` on the fly from `ClaudeCodeMessage` objects.

**Files:**
- Create: `ui/ClaudeCodePanel.kt`

- [ ] **Step 1: Create `ui/ClaudeCodePanel.kt`**

Create `src/main/kotlin/de/tomschmidtdev/copilotexporter/ui/ClaudeCodePanel.kt`:

```kotlin
package de.tomschmidtdev.copilotexporter.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import de.tomschmidtdev.copilotexporter.export.HtmlExporter
import de.tomschmidtdev.copilotexporter.export.MarkdownExporter
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeMessage
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import de.tomschmidtdev.copilotexporter.model.Role
import de.tomschmidtdev.copilotexporter.services.ClaudeCodeReaderService
import de.tomschmidtdev.copilotexporter.services.ClaudeJsonParser
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class ClaudeCodePanel(private val project: Project) : JPanel(BorderLayout()) {

    private var sessions: List<ClaudeCodeSession> = emptyList()
    private var currentMessages: List<ClaudeCodeMessage> = emptyList()

    private val sessionList = CheckBoxList<ClaudeCodeSession>()
    private val messageList = CheckBoxList<ClaudeCodeMessage>()
    private val previewLabel = JBLabel("Preview").apply {
        border = JBUI.Borders.empty(6, 8, 4, 8)
        font = font.deriveFont(java.awt.Font.BOLD)
    }
    private val statusLabel = JBLabel("Loading…").apply {
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.empty(4, 8)
    }
    private val projectCombo = JComboBox<String>()

    // Toggle state — tool calls and thinking hidden by default
    private var showUser = true
    private var showAssistant = true
    private var showToolCalls = false
    private var showThinking = false

    init {
        buildLayout()
        loadSessionsInBackground()
        sessionList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) updateMessagePreview()
        }
    }

    private fun buildLayout() {
        border = JBUI.Borders.empty(4)

        val separatorBorder = javax.swing.BorderFactory.createMatteBorder(
            0, 0, 1, 0,
            JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        )

        projectCombo.addItem("All Projects")
        projectCombo.addActionListener { loadSessionsInBackground() }

        val leftButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel("Project:"))
            add(projectCombo)
            add(JButton("Refresh", AllIcons.Actions.Refresh).apply {
                toolTipText = "Reload Claude Code sessions"
                addActionListener { loadSessionsInBackground() }
            })
            add(JButton("Export MD", AllIcons.FileTypes.Text).apply {
                toolTipText = "Export selected sessions to Markdown"
                addActionListener { doExport(ExportFormat.MARKDOWN) }
            })
            add(JButton("Export HTML", AllIcons.FileTypes.Html).apply {
                toolTipText = "Export selected sessions to HTML"
                addActionListener { doExport(ExportFormat.HTML) }
            })
        }

        val toolbar = JPanel(BorderLayout()).apply {
            border = separatorBorder
            add(leftButtons, BorderLayout.WEST)
        }

        val sessionPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Sessions").apply {
                border = JBUI.Borders.empty(6, 8, 4, 8)
                font = font.deriveFont(java.awt.Font.BOLD)
            }, BorderLayout.NORTH)
            add(JBScrollPane(sessionList), BorderLayout.CENTER)
        }

        val togglePanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(makeToggleButton("User", AllIcons.General.User, showUser) { v ->
                showUser = v; rebuildMessageListPreservingChecks()
            })
            add(makeToggleButton("Assistant", AllIcons.Actions.Preview, showAssistant) { v ->
                showAssistant = v; rebuildMessageListPreservingChecks()
            })
            add(makeToggleButton("Tool Calls", AllIcons.Debugger.Console, showToolCalls) { v ->
                showToolCalls = v; rebuildMessageListPreservingChecks()
            })
            add(makeToggleButton("Thinking", AllIcons.Actions.Lightning, showThinking) { v ->
                showThinking = v; rebuildMessageListPreservingChecks()
            })
        }

        val previewHeader = JPanel(BorderLayout()).apply {
            border = javax.swing.BorderFactory.createMatteBorder(
                0, 0, 1, 0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            )
            add(previewLabel, BorderLayout.NORTH)
            add(togglePanel, BorderLayout.SOUTH)
        }

        val messagePanel = JPanel(BorderLayout()).apply {
            add(previewHeader, BorderLayout.NORTH)
            add(JBScrollPane(messageList), BorderLayout.CENTER)
        }

        val splitter = JBSplitter(false, 0.3f).apply {
            firstComponent = sessionPanel
            secondComponent = messagePanel
        }

        add(toolbar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun makeToggleButton(
        label: String,
        icon: javax.swing.Icon,
        initialState: Boolean,
        onToggle: (Boolean) -> Unit,
    ): JToggleButton = JToggleButton(label, icon, initialState).apply {
        toolTipText = if (initialState) "Click to hide $label messages" else "Click to show $label messages"
        addActionListener { onToggle(isSelected) }
    }

    private fun loadSessionsInBackground() {
        setStatus("Loading…")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reading Claude Code sessions", false) {
            private var result: List<ClaudeCodeSession> = emptyList()
            private var slugs: List<String> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val reader = ApplicationManager.getApplication().service<ClaudeCodeReaderService>()
                slugs = reader.listProjectSlugs()
                val selectedSlug = (projectCombo.selectedItem as? String)
                    ?.takeIf { it != "All Projects" }
                result = reader.readSessions(projectSlugFilter = selectedSlug)
            }

            override fun onSuccess() {
                refreshProjectCombo(slugs)
                sessions = result
                populateSessionList()
            }

            override fun onThrowable(error: Throwable) {
                setStatus("Error: ${error.message}")
            }
        })
    }

    private fun refreshProjectCombo(slugs: List<String>) {
        val current = projectCombo.selectedItem as? String
        projectCombo.removeAllItems()
        projectCombo.addItem("All Projects")
        slugs.forEach { slug ->
            val display = ClaudeJsonParser.slugToPath(slug).let { path ->
                // Show only last path component as display name, full path as tooltip via JComboBox renderer
                path.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: slug
            }
            projectCombo.addItem(display)
        }
        // Restore selection if still present
        if (current != null) {
            for (i in 0 until projectCombo.itemCount) {
                if (projectCombo.getItemAt(i) == current) { projectCombo.selectedIndex = i; break }
            }
        }
    }

    private fun populateSessionList() {
        sessionList.clear()
        if (sessions.isEmpty()) {
            setStatus("No Claude Code sessions found.")
            return
        }
        sessions.forEachIndexed { index, session ->
            val displayText = "<html><b>${truncate(session.title, 30).escapeHtml()}</b>" +
                    "<br><small>${session.formattedDate} · ${session.messageCount}</small></html>"
            sessionList.addItem(session, displayText, index == 0)
        }
        setStatus("${sessions.size} session${if (sessions.size != 1) "s" else ""} found.")
        if (sessions.isNotEmpty()) {
            sessionList.selectedIndex = 0
            updateMessagePreview()
        }
    }

    private fun updateMessagePreview() {
        val session = selectedSession() ?: return
        val title = truncate(session.title, 50).escapeHtml()
        previewLabel.text = "<html><b>Preview</b> <span style='color:gray; font-weight:normal'>&mdash; $title</span></html>"
        currentMessages = session.messages
        rebuildMessageList(BooleanArray(currentMessages.size) { true })
    }

    private fun rebuildMessageListPreservingChecks() {
        val checks = BooleanArray(currentMessages.size) { messageList.isItemSelected(it) }
        rebuildMessageList(checks)
    }

    private fun rebuildMessageList(checkedStates: BooleanArray) {
        messageList.clear()
        currentMessages.forEachIndexed { i, msg ->
            val visible = when (msg.role) {
                Role.USER -> showUser
                Role.CLAUDE -> showAssistant
                else -> true
            }
            if (!visible) return@forEachIndexed
            val roleColor = if (msg.role == Role.USER) "#6ea8fe" else "#75b798"
            val roleName = msg.role.displayName
            val preview = truncate(msg.previewText.replace("\n", " "), 55).escapeHtml()
            val displayText = "<html><span style='color:$roleColor'><b>$roleName:</b></span> $preview</html>"
            messageList.addItem(msg, displayText, checkedStates.getOrElse(i) { true })
        }
    }

    private fun selectedSession(): ClaudeCodeSession? {
        val idx = sessionList.selectedIndex
        return if (idx >= 0) sessionList.getItemAt(idx) else null
    }

    private enum class ExportFormat(val extension: String) { MARKDOWN("md"), HTML("html") }

    private fun doExport(format: ExportFormat) {
        val checkedSessions = buildCheckedSessionExports()
        if (checkedSessions.isEmpty()) {
            setStatus("No sessions selected for export.")
            return
        }

        val descriptor = FileSaverDescriptor(
            "Export Claude Code Chat",
            "Save as ${format.name.lowercase()} file",
            format.extension,
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val baseDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val defaultName = checkedSessions.firstOrNull()?.first?.title
            ?.take(60)?.replace(Regex("[\\\\/:*?\"<>|]"), "-")?.trim('-', ' ')?.ifBlank { "claude-code-export" }
            ?: "claude-code-export"
        val wrapper = dialog.save(baseDir, "$defaultName.${format.extension}") ?: return
        val outputFile = wrapper.file

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting chat…", false) {
            override fun run(indicator: ProgressIndicator) {
                val chatSessions = checkedSessions.map { (claudeSession, selected) ->
                    claudeSession.toChatSession(selected, showThinking, showToolCalls)
                }
                val content = when (format) {
                    ExportFormat.MARKDOWN -> MarkdownExporter.export(chatSessions)
                    ExportFormat.HTML -> HtmlExporter.export(chatSessions)
                }
                outputFile.writeText(content, Charsets.UTF_8)
            }

            override fun onSuccess() {
                setStatus("Exported to: ${outputFile.name}")
                ApplicationManager.getApplication().invokeLater {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile)?.let { vf ->
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vf, true)
                    }
                }
            }

            override fun onThrowable(error: Throwable) { setStatus("Export failed: ${error.message}") }
        })
    }

    /** Returns (ClaudeCodeSession, selectedMessages) for all checked sessions. */
    private fun buildCheckedSessionExports(): List<Pair<ClaudeCodeSession, List<ClaudeCodeMessage>>> {
        val result = mutableListOf<Pair<ClaudeCodeSession, List<ClaudeCodeMessage>>>()
        for (i in 0 until sessionList.model.size) {
            if (!sessionList.isItemSelected(i)) continue
            val session = sessionList.getItemAt(i) ?: continue
            // Collect checked messages from messageList for the currently previewed session
            val selectedMessages = if (session == selectedSession()) {
                val checked = mutableListOf<ClaudeCodeMessage>()
                for (j in 0 until messageList.model.size) {
                    if (messageList.isItemSelected(j)) messageList.getItemAt(j)?.let { checked.add(it) }
                }
                checked
            } else {
                session.messages // all messages for non-previewed sessions
            }
            result.add(session to selectedMessages)
        }
        return result
    }

    private fun setStatus(text: String) {
        SwingUtilities.invokeLater { statusLabel.text = text }
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "…" else text

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
```

- [ ] **Step 2: Verify the project compiles**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/de/tomschmidtdev/copilotexporter/ui/ClaudeCodePanel.kt
git commit -m "feat: add ClaudeCodePanel UI tab for Claude Code session export"
```

---

## Task 6: ExporterToolWindowFactory — add JTabbedPane

Replace the single `ExporterPanel` content with a `JTabbedPane` containing both "Copilot" and "Claude Code" tabs.

**Files:**
- Modify: `ui/ExporterToolWindowFactory.kt`

- [ ] **Step 1: Update ExporterToolWindowFactory**

Replace the body of `createToolWindowContent` in `src/main/kotlin/de/tomschmidtdev/copilotexporter/ui/ExporterToolWindowFactory.kt`:

```kotlin
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val version = PluginManager
        .getPluginByClass(ExporterToolWindowFactory::class.java)
        ?.version

    if (version != null) {
        toolWindow.stripeTitle = "AI Chat Exporter $version"
    }

    val tabbedPane = javax.swing.JTabbedPane().apply {
        addTab("Copilot", ExporterPanel(project))
        addTab("Claude Code", ClaudeCodePanel(project))
    }

    val content = ContentFactory.getInstance()
        .createContent(tabbedPane, null, false)

    toolWindow.contentManager.addContent(content)
}
```

Note: The stripe title changes from "Copilot Chat Exporter" to "AI Chat Exporter" to reflect that both tools are now supported.

- [ ] **Step 2: Run all tests**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/de/tomschmidtdev/copilotexporter/ui/ExporterToolWindowFactory.kt
git commit -m "feat: add JTabbedPane with Copilot and Claude Code tabs"
```

---

## Task 7: Version bump to 1.6.0 and CHANGELOG

**Files:**
- Modify: `build.gradle.kts`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update version in build.gradle.kts**

Change line 7:
```kotlin
version = "1.6.0"
```

Update the `changeNotes` block — prepend a new entry before the existing `<b>1.5.6</b>` block:

```
<b>1.6.0</b>
<ul>
    <li>New: Claude Code tab — browse and export Claude Code chat sessions from ~/.claude/projects/</li>
    <li>New: Filter Claude Code sessions by project</li>
    <li>New: Toggle tool calls and thinking blocks on/off (hidden by default)</li>
</ul>
```

- [ ] **Step 2: Update CHANGELOG.md**

Add a new section at the top (after the `# Changelog` header):

```markdown
## [1.6.0] - 2026-05-20
### Added
- Claude Code tab: browse and export Claude Code chat sessions stored in `~/.claude/projects/`
- Project filter dropdown to show sessions from a specific project only
- Toggle buttons for Tool Calls and Thinking blocks (hidden by default; User and Assistant shown by default)
- Sessions from all entrypoints (CLI, Claude Desktop, JetBrains IDE plugin) are included
```

- [ ] **Step 3: Run all tests and build**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew test buildPlugin
```

Expected: all tests pass, `build/distributions/` contains a new `*.zip`.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts CHANGELOG.md
git commit -m "Bump version to 1.6.0: Claude Code session export"
```

---

## Self-Review Notes

**Spec coverage check:**
- ✅ All sessions (CLI, Desktop, IDE) → `ClaudeCodeReaderService` reads all slugs regardless of entrypoint
- ✅ Flat list, filterable by project → `sessionList` is flat, `projectCombo` filters
- ✅ Separate "Claude Code" tab → `JTabbedPane` with two tabs
- ✅ Tool Calls + Thinking toggleable, hidden by default → `showToolCalls=false`, `showThinking=false` initial state
- ✅ Export reuses `HtmlExporter` / `MarkdownExporter` → `toChatSession()` bridges the models
- ✅ Plugin-split ready → all new code is isolated in new files; only `ExporterToolWindowFactory` and `plugin.xml` bridge the two halves

**Known limitation:** The `HtmlExporter` still writes `<h1 class="page-title">Copilot Chat Export</h1>` for Claude Code exports. This is cosmetic and can be addressed in a follow-up by adding an optional `exportTitle` parameter to `HtmlExporter.export()`.

**Type consistency:**
- `ClaudeCodeMessage.toExportText(showThinking, showToolCalls)` — matches signature used in `ClaudeCodeSession.toChatSession()`
- `ClaudeJsonParser.slugToPath()` — public, called from both `ClaudeCodeReaderService` and `ClaudeCodePanel`
- `ApplicationManager.getApplication().service<ClaudeCodeReaderService>()` — consistent with `@Service(Service.Level.APPLICATION)`
