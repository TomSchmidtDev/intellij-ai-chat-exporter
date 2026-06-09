package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeMessage
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import de.tomschmidtdev.copilotexporter.model.Role
import java.io.File

object ClaudeJsonParser {

    private val mapper = ObjectMapper()

    /**
     * Parses a single Claude Code session .jsonl file.
     *
     * Returns null if the file contains no extractable user/assistant messages.
     *
     * @param file  The .jsonl file; its name without extension is used as the session UUID.
     */
    fun parse(file: File): ClaudeCodeSession? {
        val lines = try { file.readLines() } catch (e: Exception) { return null }
        if (lines.isEmpty()) return null

        val sessionId = file.nameWithoutExtension
        var title = sessionId
        val messages = mutableListOf<ClaudeCodeMessage>()
        var index = 0

        for (line in lines) {
            val entry = try { mapper.readTree(line) } catch (e: Exception) { continue }
            if (entry == null || !entry.isObject) continue

            when (entry["type"]?.asText()) {
                "ai-title" -> {
                    entry["aiTitle"]?.asText()?.takeIf { it.isNotBlank() }?.let { title = it }
                }
                "custom-title" -> {
                    entry["customTitle"]?.asText()?.takeIf { it.isNotBlank() }?.let { title = it }
                }
                "user" -> {
                    if (entry["isSidechain"]?.asBoolean() == true) continue
                    val content = entry["message"]?.get("content") ?: continue
                    parseUserMessage(content, index)?.let { messages.add(it); index++ }
                }
                "assistant" -> {
                    if (entry["isSidechain"]?.asBoolean() == true) continue
                    val content = entry["message"]?.get("content") ?: continue
                    parseAssistantMessage(content, index)?.let { messages.add(it); index++ }
                }
            }
        }

        if (messages.isEmpty()) return null

        return ClaudeCodeSession(
            id = sessionId,
            title = title,
            projectSlug = file.parentFile?.name ?: "",
            projectPath = slugToPath(file.parentFile?.name ?: ""),
            lastModified = file.lastModified(),
            messages = messages,
        )
    }

    private fun parseUserMessage(content: JsonNode, index: Int): ClaudeCodeMessage? {
        val textBlocks = mutableListOf<String>()
        val toolResultBlocks = mutableListOf<String>()

        if (content.isTextual) {
            content.asText().trim().takeIf { it.isNotBlank() }?.let { textBlocks.add(it) }
        } else if (content.isArray) {
            for (block in content) {
                when (block["type"]?.asText()) {
                    "text" -> block["text"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { textBlocks.add(it) }
                    "tool_result" -> {
                        block["content"]?.takeIf { it.isArray }?.forEach { rb ->
                            rb["text"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { toolResultBlocks.add(it) }
                        }
                    }
                }
            }
        }

        if (textBlocks.isEmpty() && toolResultBlocks.isEmpty()) return null
        return ClaudeCodeMessage(
            role = Role.USER,
            index = index,
            textBlocks = textBlocks,
            thinkingBlocks = emptyList(),
            toolCallBlocks = toolResultBlocks,
        )
    }

    private fun parseAssistantMessage(content: JsonNode, index: Int): ClaudeCodeMessage? {
        if (!content.isArray) return null
        val textBlocks = mutableListOf<String>()
        val thinkingBlocks = mutableListOf<String>()
        val toolCallBlocks = mutableListOf<String>()

        for (block in content) {
            when (block["type"]?.asText()) {
                "text" -> block["text"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { textBlocks.add(it) }
                "thinking" -> block["thinking"]?.asText()?.trim()?.takeIf { it.isNotBlank() }?.let { thinkingBlocks.add(it) }
                "tool_use" -> {
                    val name = block["name"]?.asText() ?: continue
                    val input = block["input"]?.toString() ?: "{}"
                    toolCallBlocks.add("[Tool: $name]\n$input")
                }
            }
        }

        if (textBlocks.isEmpty() && thinkingBlocks.isEmpty() && toolCallBlocks.isEmpty()) return null
        return ClaudeCodeMessage(
            role = Role.CLAUDE,
            index = index,
            textBlocks = textBlocks,
            thinkingBlocks = thinkingBlocks,
            toolCallBlocks = toolCallBlocks,
        )
    }

    /**
     * Best-effort decode of a project slug back to a file path.
     *
     * Slug format: all path separators replaced with `-` (e.g. `-Users-alice-my-app`).
     * Strips the leading dash and replaces remaining dashes with `/`.
     * Ambiguous for directory names that contain `-`, but good enough for display.
     */
    fun slugToPath(slug: String): String {
        if (slug.isEmpty()) return slug
        val stripped = slug.trimStart('-')
        return "/" + stripped.replace("-", "/")
    }
}
