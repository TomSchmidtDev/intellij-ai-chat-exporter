package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.Role
import java.time.Instant

/**
 * Liest den JSONL-Event-Log einer GitHub-Copilot-**CLI**-Session
 * (`~/.copilot/session-state/<id>/events.jsonl`) und wandelt ihn in
 * [ChatMessage]s um.
 *
 * HINTERGRUND: Aus der IDE gestartete Background-/Agent-Sessions
 * (targetType=BACKGROUND, permissionModeId=copilotcli/default) legen im
 * Nitrite-Store des IDE-Plugins nur einen Metadaten-Stub ab (`turns=[]`,
 * `NtAgentTurn` leer). Der eigentliche Gesprächsverlauf liegt im eigenen
 * Event-Log der Copilot-CLI. Diese Klasse erschließt diesen Verlauf, damit
 * solche Sessions nicht nur als leerer Platzhalter erscheinen.
 *
 * Diese Klasse ist bewusst von IntelliJ-Platform-APIs getrennt und damit
 * ohne IDE-Sandbox unit-testbar.
 *
 * Relevante Event-Typen (eine JSON-Zeile pro Event):
 *   - `user.message`      → data.content ist der Nutzer-Prompt.
 *                           Von der Laufzeit injizierte Nachrichten tragen ein
 *                           `data.source`-Feld (z. B. Skill-/Tool-Kontext) und
 *                           werden übersprungen.
 *   - `assistant.message` → data.content ist der Antworttext. Reine
 *                           Tool-Call-Turns haben leeren content und werden
 *                           übersprungen.
 *   - alle übrigen Typen (session.*, hook.*, tool.*, permission.*,
 *     system.message, skill.*, subagent.*) werden ignoriert.
 */
internal object CopilotCliSessionParser {

    private val objectMapper = ObjectMapper()

    fun parse(eventsJsonl: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        var index = 0

        eventsJsonl.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val node = runCatching { objectMapper.readTree(line) }.getOrNull() ?: return@forEach
            if (!node.isObject) return@forEach

            val type = node.get("type")?.asText() ?: return@forEach
            val data = node.get("data")?.takeIf { it.isObject } ?: return@forEach
            val timestamp = parseTimestamp(node.get("timestamp")?.asText())

            when (type) {
                "user.message" -> {
                    // Injizierte Nachrichten (Skill-/Tool-Kontext) haben ein source-Feld.
                    val source = data.get("source")?.asText()
                    if (!source.isNullOrBlank()) return@forEach
                    val content = data.get("content")?.asText()?.takeIf { it.isNotBlank() } ?: return@forEach
                    messages.add(ChatMessage(Role.USER, content, index++, timestamp))
                }
                "assistant.message" -> {
                    val content = data.get("content")?.asText()?.takeIf { it.isNotBlank() } ?: return@forEach
                    messages.add(ChatMessage(Role.ASSISTANT, content, index++, timestamp))
                }
            }
        }

        return messages
    }

    private fun parseTimestamp(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
    }
}
