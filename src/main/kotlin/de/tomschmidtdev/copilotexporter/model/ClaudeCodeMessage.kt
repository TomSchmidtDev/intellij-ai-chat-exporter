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
