package de.tomschmidtdev.copilotexporter.services

import org.dizitart.no2.collection.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class NitriteDocCompatTest {

    @Test
    fun `embedded turns as map list are converted and readable`() {
        val embedded = listOf(
            mapOf(
                "createdAt" to 42L,
                "request" to mapOf("stringContent" to "Hi"),
                "response" to mapOf("stringContent" to "Hello"),
            )
        )

        val turns = NitriteDocCompat.asDocumentList(embedded)

        assertEquals(1, turns.size)
        assertEquals(42L, NitriteDocCompat.getLong(turns[0], "createdAt"))
        assertEquals("Hi", NitriteDocCompat.getString(turns[0], "request.stringContent"))
        assertEquals("Hello", NitriteDocCompat.getString(turns[0], "response.stringContent"))
    }

    @Test
    fun `dot notation key stays supported`() {
        val turn = Document.createDocument("request.stringContent", "Ping")

        assertEquals("Ping", NitriteDocCompat.getString(turn, "request.stringContent"))
    }

    @Test
    fun `nested document path is resolved`() {
        val turn = Document.createDocument("request", Document.createDocument("stringContent", "Nested"))

        val value = NitriteDocCompat.getString(turn, "request.stringContent")

        assertNotNull(value)
        assertEquals("Nested", value)
    }
}
