package de.tomschmidtdev.copilotexporter.services

import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.ChatSession
import de.tomschmidtdev.copilotexporter.model.Role

internal object CopilotSessionFallback {
    data class MetadataContext(
        val targetType: String?,
        val modeId: String?,
        val conversationId: String?,
    )

    fun metadataOnlyOrNull(
        id: String,
        title: String,
        createdAt: Long,
        modifiedAt: Long,
        metadataContext: MetadataContext? = null,
    ): ChatSession? {
        if (!hasDescriptiveTitle(title)) return null
        val ts = if (modifiedAt > 0L) modifiedAt else createdAt
        val metadataSuffix = metadataContext
            ?.asDisplaySuffix()
            ?.takeIf { it.isNotBlank() }
            ?.let { "\n\n$it" }
            ?: ""
        val placeholder = ChatMessage(
            role = Role.ASSISTANT,
            content = "[No local turns found in Copilot DB for this session. Recent Copilot versions may keep message content outside the local Nitrite storage.]$metadataSuffix",
            index = 1,
            timestamp = ts,
        )
        val messages = buildList<ChatMessage> {
            add(ChatMessage(role = Role.USER, content = title, index = 0, timestamp = ts))
            add(placeholder)
        }
        return ChatSession(
            id = id,
            title = title,
            createdAt = createdAt,
            messages = messages,
            lastModifiedAt = ts,
        )
    }

    private fun hasDescriptiveTitle(title: String): Boolean {
        val normalized = title.trim()
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase()
        return !lower.startsWith("new chat session")
    }

    private fun MetadataContext.asDisplaySuffix(): String {
        val parts = listOfNotNull(
            targetType?.takeIf { it.isNotBlank() }?.let { "targetType=$it" },
            modeId?.takeIf { it.isNotBlank() }?.let { "modeId=$it" },
            conversationId?.takeIf { it.isNotBlank() }?.let { "conversationId=$it" },
        )
        if (parts.isEmpty()) return ""
        return "Session metadata: ${parts.joinToString(", ")}"
    }
}
