package de.tomschmidtdev.copilotexporter.services

import de.tomschmidtdev.copilotexporter.model.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotSessionFallbackTest {

    @Test
    fun `creates metadata only placeholder session when descriptive title exists`() {
        val session = CopilotSessionFallback.metadataOnlyOrNull(
            id = "session-1",
            title = "Wie behebe ich den Fehler?",
            createdAt = 1000L,
            modifiedAt = 2000L,
        )!!

        assertEquals("session-1", session.id)
        assertEquals("Wie behebe ich den Fehler?", session.title)
        assertEquals(2, session.messages.size)
        assertEquals(Role.USER, session.messages.first().role)
        assertEquals("Wie behebe ich den Fehler?", session.messages.first().content)
        assertEquals(Role.ASSISTANT, session.messages[1].role)
        assertTrue(session.messages[1].content.contains("No local turns found"))
        assertEquals(2000L, session.lastModifiedAt)
    }

    @Test
    fun `uses descriptive session title as fallback user prompt`() {
        val session = CopilotSessionFallback.metadataOnlyOrNull(
            id = "session-2",
            title = "Welcher Pfad muss hier angegeben werden?",
            createdAt = 1000L,
            modifiedAt = 2000L,
        )!!

        assertEquals(2, session.messages.size)
        assertEquals(Role.USER, session.messages[0].role)
        assertEquals("Welcher Pfad muss hier angegeben werden?", session.messages[0].content)
        assertEquals(Role.ASSISTANT, session.messages[1].role)
        assertTrue(session.messages[1].content.contains("No local turns found"))
    }

    @Test
    fun `suppresses generic untitled metadata-only sessions`() {
        val session = CopilotSessionFallback.metadataOnlyOrNull(
            id = "session-3",
            title = "New Chat Session",
            createdAt = 1000L,
            modifiedAt = 2000L,
        )

        assertNull(session)
    }

    @Test
    fun `adds available session metadata to placeholder message`() {
        val session = CopilotSessionFallback.metadataOnlyOrNull(
            id = "session-4",
            title = "Wie kann ich das manuell testen?",
            createdAt = 1000L,
            modifiedAt = 2000L,
            metadataContext = CopilotSessionFallback.MetadataContext(
                targetType = "BACKGROUND",
                modeId = "ask",
                conversationId = "conv-123",
            ),
        )!!

        assertEquals(Role.ASSISTANT, session.messages[1].role)
        assertTrue(session.messages[1].content.contains("targetType=BACKGROUND"))
        assertTrue(session.messages[1].content.contains("modeId=ask"))
        assertTrue(session.messages[1].content.contains("conversationId=conv-123"))
    }
}
