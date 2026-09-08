package de.tomschmidtdev.copilotexporter.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One user or assistant turn in a standalone Copilot CLI session
 * (`~/.copilot/session-state/<id>/events.jsonl`).
 *
 * Unlike [ClaudeCodeMessage], there is no thinking-block equivalent in this
 * format, so only text and tool-call blocks are split out.
 */
data class CopilotCliMessage(
    val role: Role,
    val index: Int,
    val textBlocks: List<String>,
    val toolCallBlocks: List<String>,
    val timestamp: Long = 0L,
) {
    fun toExportText(showToolCalls: Boolean): String {
        val parts = mutableListOf<String>()
        if (showToolCalls) parts.addAll(toolCallBlocks)
        parts.addAll(textBlocks)
        return parts.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    val previewText: String
        get() = textBlocks.firstOrNull()?.take(80)
            ?: toolCallBlocks.firstOrNull()?.take(80)
            ?: ""
}

/**
 * One standalone Copilot CLI session, read from
 * `~/.copilot/session-state/<id>/` (`workspace.yaml` + `events.jsonl`).
 *
 * @param cwd          Working directory the session was started in; always present.
 * @param repository   `owner/repo`, when the cwd is inside a known git remote.
 * @param branch       Git branch at session start, when available.
 * @param clientName   Origin of the session, e.g. `github/cli` (terminal) or
 *                     `copilot-intellij` (IDE-launched background session).
 */
data class CopilotCliSession(
    val id: String,
    val title: String,
    val cwd: String?,
    val repository: String?,
    val branch: String?,
    val clientName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<CopilotCliMessage>,
) {
    val formattedDate: String
        get() {
            val ts = if (updatedAt > 0L) updatedAt else createdAt
            if (ts == 0L) return "Unknown date"
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            return formatter.format(Instant.ofEpochMilli(ts))
        }

    val messageCount: String
        get() = "${messages.size} message${if (messages.size != 1) "s" else ""}"

    /**
     * Converts selected messages to a ChatSession suitable for HtmlExporter / MarkdownExporter.
     *
     * @param selectedMessages  Subset of this session's messages to include (in order).
     * @param showToolCalls     Whether to include tool-call and tool-result blocks.
     */
    fun toChatSession(
        selectedMessages: List<CopilotCliMessage>,
        showToolCalls: Boolean,
    ): ChatSession {
        val chatMessages = selectedMessages.mapIndexed { i, msg ->
            ChatMessage(
                role = msg.role,
                content = msg.toExportText(showToolCalls),
                index = i,
                timestamp = msg.timestamp,
            )
        }.filter { it.content.isNotBlank() }
        return ChatSession(
            id = id,
            title = title,
            createdAt = createdAt,
            messages = chatMessages,
            lastModifiedAt = updatedAt,
        )
    }
}
