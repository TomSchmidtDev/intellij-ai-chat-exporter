package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotJsonParserTest {

    private val mapper = ObjectMapper()

    // -------------------------------------------------------------------------
    // Hilfsmethoden zum Erzeugen von Test-JSON
    // -------------------------------------------------------------------------

    /** Baut Ebene 3 für AgentRound: { "roundId": N, "reply": "...", "toolCalls": [] } */
    private fun agentRoundData(roundId: Int, reply: String): String =
        mapper.writeValueAsString(mapOf("roundId" to roundId, "reply" to reply, "toolCalls" to emptyList<Any>()))

    /** Baut Ebene 2 für AgentRound: { "type": "AgentRound", "data": "<escaped json>" } */
    private fun agentRoundMiddle(roundId: Int, reply: String): String =
        mapper.writeValueAsString(mapOf("type" to "AgentRound", "data" to agentRoundData(roundId, reply)))

    /** Baut Ebene 3 für Markdown: { "text": "..." } */
    private fun markdownData(text: String): String =
        mapper.writeValueAsString(mapOf("text" to text))

    /** Baut Ebene 2 für Markdown: { "type": "Markdown", "data": "<escaped json>" } */
    private fun markdownMiddle(text: String): String =
        mapper.writeValueAsString(mapOf("type" to "Markdown", "data" to markdownData(text)))

    /** Baut Ebene 1 mit einem Value-Eintrag: { "<key>": { "type": "Value", "value": "<middle>" } } */
    private fun valueOuter(key: String, middle: String): String =
        mapper.writeValueAsString(mapOf(key to mapOf("type" to "Value", "value" to middle)))

    /** Baut Ebene 1 mit einem Subgraph-Eintrag: { "<key>": { "type": "Subgraph", "value": "<inner json>" } } */
    private fun subgraphOuter(key: String, inner: String): String =
        mapper.writeValueAsString(mapOf(key to mapOf("type" to "Subgraph", "value" to inner)))

    // -------------------------------------------------------------------------
    // Variante A: AgentRound
    // -------------------------------------------------------------------------

    @Test
    fun `single AgentRound returns reply text`() {
        val json = valueOuter("uuid-1", agentRoundMiddle(roundId = 1, reply = "Hello from agent"))
        assertEquals("Hello from agent", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `multiple AgentRounds returns reply with highest roundId`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Value", "value" to agentRoundMiddle(1, "first reply")),
            "uuid-2" to mapOf("type" to "Value", "value" to agentRoundMiddle(3, "third reply")),
            "uuid-3" to mapOf("type" to "Value", "value" to agentRoundMiddle(2, "second reply")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("third reply", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `AgentRound with blank reply is ignored`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Value", "value" to agentRoundMiddle(1, "   ")),
            "uuid-2" to mapOf("type" to "Value", "value" to agentRoundMiddle(2, "real reply")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("real reply", CopilotJsonParser.parseAgentContents(json))
    }

    // -------------------------------------------------------------------------
    // Variante B: Markdown
    // -------------------------------------------------------------------------

    @Test
    fun `single Markdown entry returns text`() {
        val json = valueOuter("uuid-1", markdownMiddle("# Heading\nSome text"))
        assertEquals("# Heading\nSome text", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `multiple Markdown entries joined with double newline`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Value", "value" to markdownMiddle("first")),
            "uuid-2" to mapOf("type" to "Value", "value" to markdownMiddle("second")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("first\n\nsecond", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `Markdown with blank text is ignored`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Value", "value" to markdownMiddle("  ")),
            "uuid-2" to mapOf("type" to "Value", "value" to markdownMiddle("real text")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("real text", CopilotJsonParser.parseAgentContents(json))
    }

    // -------------------------------------------------------------------------
    // AgentRound hat Vorrang vor Markdown (wenn beides vorhanden)
    // -------------------------------------------------------------------------

    @Test
    fun `AgentRound takes priority over Markdown when both present`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Value", "value" to markdownMiddle("markdown text")),
            "uuid-2" to mapOf("type" to "Value", "value" to agentRoundMiddle(1, "agent reply")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("agent reply", CopilotJsonParser.parseAgentContents(json))
    }

    // -------------------------------------------------------------------------
    // Variante C: Subgraph (neuere Agent-Sessions)
    // -------------------------------------------------------------------------

    @Test
    fun `Subgraph is recursively processed`() {
        val innerValue = valueOuter("inner-uuid", agentRoundMiddle(1, "subgraph reply"))
        val json = subgraphOuter("__first__", innerValue)
        assertEquals("subgraph reply", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `nested Subgraph in Subgraph is recursively processed`() {
        val deepValue = valueOuter("deep-uuid", agentRoundMiddle(1, "deep reply"))
        val middleSubgraph = subgraphOuter("mid", deepValue)
        val json = subgraphOuter("__first__", middleSubgraph)
        assertEquals("deep reply", CopilotJsonParser.parseAgentContents(json))
    }

    // -------------------------------------------------------------------------
    // Unbekannte / ignorierte Eintragstypen
    // -------------------------------------------------------------------------

    @Test
    fun `unknown entry types are ignored`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Steps", "value" to "ignored"),
            "uuid-2" to mapOf("type" to "References", "value" to "ignored"),
            "uuid-3" to mapOf("type" to "Value", "value" to agentRoundMiddle(1, "real reply")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("real reply", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `entry without type field is ignored`() {
        val entries = mapOf(
            "uuid-1" to mapOf("value" to "no type here"),
            "uuid-2" to mapOf("type" to "Value", "value" to agentRoundMiddle(1, "real reply")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("real reply", CopilotJsonParser.parseAgentContents(json))
    }

    // -------------------------------------------------------------------------
    // Fehlerbehandlung / Randwerte
    // -------------------------------------------------------------------------

    @Test
    fun `invalid JSON returns original string`() {
        val raw = "not valid json {{{"
        assertEquals(raw, CopilotJsonParser.parseAgentContents(raw))
    }

    @Test
    fun `JSON array (not object) returns original string`() {
        val raw = """["a", "b"]"""
        assertEquals(raw, CopilotJsonParser.parseAgentContents(raw))
    }

    @Test
    fun `empty object returns original string`() {
        val raw = "{}"
        assertEquals(raw, CopilotJsonParser.parseAgentContents(raw))
    }

    @Test
    fun `malformed inner JSON in Value is skipped gracefully`() {
        val entries = mapOf(
            "uuid-1" to mapOf("type" to "Value", "value" to "{ broken json"),
            "uuid-2" to mapOf("type" to "Value", "value" to agentRoundMiddle(1, "fallback reply")),
        )
        val json = mapper.writeValueAsString(entries)
        assertEquals("fallback reply", CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `reply text with special characters is preserved`() {
        val reply = "Here is code:\n```kotlin\nval x = 1 < 2\n```\nDone."
        val json = valueOuter("uuid-1", agentRoundMiddle(1, reply))
        assertEquals(reply, CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `unicode in reply is preserved`() {
        val reply = "Antwort auf Deutsch: Ä Ö Ü ß — こんにちは"
        val json = valueOuter("uuid-1", agentRoundMiddle(1, reply))
        assertEquals(reply, CopilotJsonParser.parseAgentContents(json))
    }

    @Test
    fun `no rounds and no markdown returns original string`() {
        // Value entry mit unbekanntem type auf Ebene 2
        val middle = mapper.writeValueAsString(mapOf("type" to "UnknownType", "data" to "something"))
        val json = valueOuter("uuid-1", middle)
        assertTrue(CopilotJsonParser.parseAgentContents(json).isNotEmpty())
    }
}
