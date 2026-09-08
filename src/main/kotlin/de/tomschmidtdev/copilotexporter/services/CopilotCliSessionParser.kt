package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.CopilotCliMessage
import de.tomschmidtdev.copilotexporter.model.Role
import java.time.Instant

/**
 * Liest den JSONL-Event-Log einer GitHub-Copilot-**CLI**-Session
 * (`~/.copilot/session-state/<id>/events.jsonl`).
 *
 * Zwei Sichten auf dieselben Daten:
 *   - [parse] — flache [ChatMessage]-Liste ohne Tool-Calls; genutzt vom
 *     Nitrite-Stub-Fallback in [CopilotChatReaderService].
 *   - [parseMessages] — [CopilotCliMessage]-Liste mit getrennten Text-/
 *     Tool-Call-Blöcken; genutzt vom eigenständigen "Copilot CLI"-Tab
 *     (`CopilotCliSessionReaderService`), das Tool-Calls umschaltbar macht.
 *
 * Relevante Event-Typen (eine JSON-Zeile pro Event):
 *   - `user.message`             → data.content ist der Nutzer-Prompt.
 *                                   NIE `data.transformedContent` lesen (die
 *                                   Laufzeit-augmentierte Variante mit
 *                                   Datetime-/Tool-Hinweisen/Remindern).
 *                                   Von der Laufzeit injizierte Nachrichten
 *                                   tragen ein `data.source`-Feld und werden
 *                                   übersprungen.
 *   - `assistant.message`        → data.content ist der Antworttext.
 *                                   data.toolRequests[] ({toolCallId, name,
 *                                   arguments}) wird zu Tool-Call-Blöcken.
 *                                   Nachrichten ohne Text UND ohne Tool-Calls
 *                                   werden übersprungen.
 *   - `tool.execution_complete`  → data.toolCallId + data.result.content
 *                                   werden per toolCallId dem passenden
 *                                   Tool-Request-Block als Ergebnis angehängt.
 *   - alle übrigen Typen (session.*, hook.*, model.*, permission.*,
 *     system.message, subagent.*, skill.invoked, abort, tool.execution_start)
 *     werden ignoriert.
 */
internal object CopilotCliSessionParser {

    private val objectMapper = ObjectMapper()

    fun parse(eventsJsonl: String): List<ChatMessage> =
        parseMessages(eventsJsonl).mapNotNull { msg ->
            val content = msg.toExportText(showToolCalls = false)
            if (content.isBlank()) null else ChatMessage(msg.role, content, msg.index, msg.timestamp)
        }

    fun parseMessages(eventsJsonl: String): List<CopilotCliMessage> {
        val nodes = eventsJsonl.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> runCatching { objectMapper.readTree(line) }.getOrNull() }
            .filter { it.isObject }
            .toList()

        val toolResults = mutableMapOf<String, JsonNode>()
        for (node in nodes) {
            if (node.get("type")?.asText() != "tool.execution_complete") continue
            val data = node.get("data")?.takeIf { it.isObject } ?: continue
            val toolCallId = data.get("toolCallId")?.asText() ?: continue
            toolResults[toolCallId] = data
        }

        val messages = mutableListOf<CopilotCliMessage>()
        var index = 0

        for (node in nodes) {
            val type = node.get("type")?.asText() ?: continue
            val data = node.get("data")?.takeIf { it.isObject } ?: continue
            val timestamp = parseTimestamp(node.get("timestamp")?.asText())

            when (type) {
                "user.message" -> {
                    val source = data.get("source")?.asText()
                    if (!source.isNullOrBlank()) continue
                    val content = data.get("content")?.asText()?.takeIf { it.isNotBlank() } ?: continue
                    messages.add(
                        CopilotCliMessage(
                            role = Role.USER,
                            index = index++,
                            textBlocks = listOf(content),
                            toolCallBlocks = emptyList(),
                            timestamp = timestamp,
                        )
                    )
                }
                "assistant.message" -> {
                    val content = data.get("content")?.asText()?.takeIf { it.isNotBlank() }
                    val toolCallBlocks = buildToolCallBlocks(data.get("toolRequests"), toolResults)
                    if (content == null && toolCallBlocks.isEmpty()) continue
                    messages.add(
                        CopilotCliMessage(
                            role = Role.ASSISTANT,
                            index = index++,
                            textBlocks = content?.let { listOf(it) } ?: emptyList(),
                            toolCallBlocks = toolCallBlocks,
                            timestamp = timestamp,
                        )
                    )
                }
            }
        }

        return messages
    }

    private fun buildToolCallBlocks(toolRequests: JsonNode?, toolResults: Map<String, JsonNode>): List<String> {
        if (toolRequests == null || !toolRequests.isArray) return emptyList()
        return toolRequests.mapNotNull { request ->
            val toolCallId = request.get("toolCallId")?.asText() ?: return@mapNotNull null
            val name = request.get("name")?.asText() ?: "unknown"
            val arguments = request.get("arguments")?.toString() ?: "{}"
            val block = StringBuilder("[Tool: $name]\n$arguments")
            val resultContent = toolResults[toolCallId]?.get("result")?.get("content")?.asText()
            if (!resultContent.isNullOrBlank()) {
                block.append("\n[Result]\n").append(resultContent)
            }
            block.toString()
        }
    }

    private fun parseTimestamp(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
    }
}
