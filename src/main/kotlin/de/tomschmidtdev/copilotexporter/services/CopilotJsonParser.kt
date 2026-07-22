package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Extrahiert Antworttext aus dem dreifach-verschachtelten JSON-Format von
 * Copilot Agent- und Edit-Sessions.
 *
 * Diese Klasse ist bewusst von IntelliJ-Platform-APIs getrennt, damit die
 * Parsing-Logik ohne IDE-Sandbox unit-testbar bleibt.
 *
 * Format-Übersicht:
 *
 * Variante A – AgentRound:
 *   Ebene 1: { "<uuid>": { "type": "Value", "value": "<json>" } }
 *   Ebene 2: { "type": "AgentRound", "data": "<json>" }
 *   Ebene 3: { "roundId": N, "reply": "<text>", "toolCalls": [...] }
 *   → Wir nehmen den "reply" der Round mit der höchsten roundId.
 *
 * Variante B – Markdown:
 *   Ebene 2: { "type": "Markdown", "data": "<json>" }
 *   Ebene 3: { "text": "<markdown-text>" }
 *   → Alle Markdown-Texte werden mit Doppel-Newline verbunden.
 *
 * Variante C – Subgraph (neuere Agent-Sessions):
 *   Ebene 1: { "__first__": { "type": "Subgraph", "value": "<json>" }, ... }
 *   → "value" enthält wieder ein UUID→Value-Objekt derselben Struktur → rekursiv.
 *
 * Variante D – Filter / Error:
 *   Ebene 2: { "type": "Filter"|"Error", "data": "{\"message\":\"...\"}" }
 *   → Der Server hat die Antwort gefiltert bzw. es trat ein Fehler auf. Wir
 *     zeigen die enthaltene "message" statt Roh-JSON.
 *
 * Reine Tool-Call-Runden (AgentRound mit leerem "reply" und nur toolCalls)
 * liefern KEINEN Antworttext. In diesem Fall geben wir einen leeren String
 * zurück, damit kein Roh-JSON in der UI landet — vorausgesetzt, wir haben
 * mindestens einen bekannten Strukturtyp erkannt. Nur bei komplett
 * unbekanntem Format wird der Rohstring als Fallback zurückgegeben.
 */
internal object CopilotJsonParser {

    private val objectMapper = ObjectMapper()

    fun parseAgentContents(contents: String): String {
        val outerNode = runCatching { objectMapper.readTree(contents) }.getOrNull()
            ?: return contents
        if (!outerNode.isObject) return contents
        // Leeres Objekt ("{}") bedeutet: keine Inhalte (z. B. abgebrochener Turn).
        // Leeren String zurückgeben statt "{}" in der UI anzuzeigen.
        if (outerNode.isEmpty) return ""

        val acc = Accumulator()
        processEntries(outerNode, acc)

        return when {
            acc.rounds.isNotEmpty() -> acc.rounds.maxByOrNull { it.first }!!.second
            acc.markdownTexts.isNotEmpty() -> acc.markdownTexts.joinToString("\n\n")
            acc.messages.isNotEmpty() -> acc.messages.joinToString("\n\n")
            // Bekannter Strukturtyp erkannt, aber kein Text (z. B. reine
            // Tool-Call-Runden) → leerer String statt Roh-JSON.
            acc.sawKnownStructural -> ""
            else -> contents
        }
    }

    private class Accumulator {
        val rounds = mutableListOf<Pair<Int, String>>()
        val markdownTexts = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var sawKnownStructural = false
    }

    private fun processEntries(node: JsonNode, acc: Accumulator) {
        node.fields().forEach { (_, entry) ->
            val entryType = entry.get("type")?.asText() ?: return@forEach
            val valueStr = entry.get("value")?.asText() ?: return@forEach

            when (entryType) {
                "Subgraph" -> {
                    val subNode = runCatching { objectMapper.readTree(valueStr) }.getOrNull()
                        ?: return@forEach
                    if (subNode.isObject) processEntries(subNode, acc)
                }
                "Value" -> {
                    val middle = runCatching { objectMapper.readTree(valueStr) }.getOrNull()
                        ?: return@forEach
                    val type = middle.get("type")?.asText() ?: return@forEach
                    val dataStr = middle.get("data")?.asText() ?: return@forEach
                    when (type) {
                        "AgentRound" -> {
                            acc.sawKnownStructural = true
                            val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull()
                                ?: return@forEach
                            val reply = inner.get("reply")?.asText() ?: return@forEach
                            val roundId = inner.get("roundId")?.asInt() ?: 0
                            if (reply.isNotBlank()) acc.rounds.add(roundId to reply)
                        }
                        "Markdown" -> {
                            acc.sawKnownStructural = true
                            val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull()
                                ?: return@forEach
                            val text = inner.get("text")?.asText() ?: return@forEach
                            if (text.isNotBlank()) acc.markdownTexts.add(text)
                        }
                        "Filter", "Error" -> {
                            acc.sawKnownStructural = true
                            val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull()
                                ?: return@forEach
                            val message = inner.get("message")?.asText() ?: return@forEach
                            if (message.isNotBlank()) acc.messages.add(message)
                        }
                    }
                }
            }
        }
    }
}
