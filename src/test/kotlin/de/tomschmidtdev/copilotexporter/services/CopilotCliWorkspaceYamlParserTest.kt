package de.tomschmidtdev.copilotexporter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopilotCliWorkspaceYamlParserTest {

    @Test
    fun `parses simple key value pairs`() {
        val yaml = """
            id: abc-123
            cwd: /Users/schmidt/projects/foo
            branch: main
        """.trimIndent()

        val result = CopilotCliWorkspaceYamlParser.parse(yaml)

        assertEquals("abc-123", result["id"])
        assertEquals("/Users/schmidt/projects/foo", result["cwd"])
        assertEquals("main", result["branch"])
    }

    @Test
    fun `handles missing optional fields by simply not having the key`() {
        val yaml = """
            id: abc-123
            cwd: /Users/schmidt
            summary_count: 0
            created_at: 2026-04-19T09:00:48.986Z
            updated_at: 2026-04-19T09:00:48.991Z
        """.trimIndent()

        val result = CopilotCliWorkspaceYamlParser.parse(yaml)

        assertEquals(5, result.size)
        assertNull(result["repository"])
        assertNull(result["branch"])
        assertNull(result["name"])
    }

    @Test
    fun `ignores blank lines`() {
        val yaml = "id: abc-123\n\n\ncwd: /Users/schmidt\n"
        val result = CopilotCliWorkspaceYamlParser.parse(yaml)
        assertEquals(2, result.size)
    }

    @Test
    fun `ignores lines without a colon`() {
        val yaml = "id: abc-123\nthis is not a valid line\ncwd: /Users/schmidt\n"
        val result = CopilotCliWorkspaceYamlParser.parse(yaml)
        assertEquals(2, result.size)
    }

    @Test
    fun `strips surrounding double quotes from values`() {
        val yaml = "name: \"Rename VirtualCamFilterApp\""
        val result = CopilotCliWorkspaceYamlParser.parse(yaml)
        assertEquals("Rename VirtualCamFilterApp", result["name"])
    }

    @Test
    fun `handles values that themselves contain a colon`() {
        val yaml = "name: /migrate:workspace add a copilot log"
        val result = CopilotCliWorkspaceYamlParser.parse(yaml)
        assertEquals("/migrate:workspace add a copilot log", result["name"])
    }

    @Test
    fun `returns empty map for blank input`() {
        val result = CopilotCliWorkspaceYamlParser.parse("")
        assertTrue(result.isEmpty())
    }
}
