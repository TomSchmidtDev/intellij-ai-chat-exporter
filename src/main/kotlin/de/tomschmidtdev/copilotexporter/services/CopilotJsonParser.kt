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
 * Bei Parse-Fehlern oder unbekanntem Format wird der Rohstring zurückgegeben.
 */
internal object CopilotJsonParser {

    private val objectMapper = ObjectMapper()

    fun parseAgentContents(contents: String): String {
        val outerNode = runCatching { objectMapper.readTree(contents) }.getOrNull()
            ?: return contents
        if (!outerNode.isObject) return contents

        val rounds = mutableListOf<Pair<Int, String>>()
        val markdownTexts = mutableListOf<String>()

        processEntries(outerNode, rounds, markdownTexts)

        return when {
            rounds.isNotEmpty() -> rounds.maxByOrNull { it.first }!!.second
            markdownTexts.isNotEmpty() -> markdownTexts.joinToString("\n\n")
            else -> contents
        }
    }

    private fun processEntries(
        node: JsonNode,
        rounds: MutableList<Pair<Int, String>>,
        markdownTexts: MutableList<String>,
    ) {
        node.fields().forEach { (_, entry) ->
            val entryType = entry.get("type")?.asText() ?: return@forEach
            val valueStr = entry.get("value")?.asText() ?: return@forEach

            when (entryType) {
                "Subgraph" -> {
                    val subNode = runCatching { objectMapper.readTree(valueStr) }.getOrNull()
                        ?: return@forEach
                    if (subNode.isObject) processEntries(subNode, rounds, markdownTexts)
                }
                "Value" -> {
                    val middle = runCatching { objectMapper.readTree(valueStr) }.getOrNull()
                        ?: return@forEach
                    val type = middle.get("type")?.asText() ?: return@forEach
                    val dataStr = middle.get("data")?.asText() ?: return@forEach
                    when (type) {
                        "AgentRound" -> {
                            val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull()
                                ?: return@forEach
                            val reply = inner.get("reply")?.asText() ?: return@forEach
                            val roundId = inner.get("roundId")?.asInt() ?: 0
                            if (reply.isNotBlank()) rounds.add(roundId to reply)
                        }
                        "Markdown" -> {
                            val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull()
                                ?: return@forEach
                            val text = inner.get("text")?.asText() ?: return@forEach
                            if (text.isNotBlank()) markdownTexts.add(text)
                        }
                    }
                }
            }
        }
    }
}
