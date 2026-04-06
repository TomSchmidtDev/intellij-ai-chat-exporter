package de.tomschmidtdev.copilotexporter.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatMessageTest {

    @Test
    fun `user values map to USER`() {
        assertEquals(Role.USER, Role.fromRawValue("user"))
        assertEquals(Role.USER, Role.fromRawValue("human"))
    }

    @Test
    fun `assistant values map to ASSISTANT`() {
        assertEquals(Role.ASSISTANT, Role.fromRawValue("assistant"))
        assertEquals(Role.ASSISTANT, Role.fromRawValue("copilot"))
        assertEquals(Role.ASSISTANT, Role.fromRawValue("bot"))
        assertEquals(Role.ASSISTANT, Role.fromRawValue("model"))
    }

    @Test
    fun `mapping is case-insensitive`() {
        assertEquals(Role.USER, Role.fromRawValue("USER"))
        assertEquals(Role.USER, Role.fromRawValue("User"))
        assertEquals(Role.ASSISTANT, Role.fromRawValue("ASSISTANT"))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(Role.USER, Role.fromRawValue("  user  "))
    }

    @Test
    fun `null maps to UNKNOWN`() {
        assertEquals(Role.UNKNOWN, Role.fromRawValue(null))
    }

    @Test
    fun `empty string maps to UNKNOWN`() {
        assertEquals(Role.UNKNOWN, Role.fromRawValue(""))
    }

    @Test
    fun `unrecognized value maps to UNKNOWN`() {
        assertEquals(Role.UNKNOWN, Role.fromRawValue("system"))
        assertEquals(Role.UNKNOWN, Role.fromRawValue("admin"))
    }
}
