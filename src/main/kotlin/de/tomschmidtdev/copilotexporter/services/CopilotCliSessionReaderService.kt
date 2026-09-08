package de.tomschmidtdev.copilotexporter.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import de.tomschmidtdev.copilotexporter.model.CopilotCliSession
import de.tomschmidtdev.copilotexporter.model.Role
import java.io.File
import java.time.Instant

@Service(Service.Level.APP)
class CopilotCliSessionReaderService {

    private val log = logger<CopilotCliSessionReaderService>()

    /** Reads all standalone Copilot CLI sessions from ~/.copilot/session-state/. */
    fun readSessions(): List<CopilotCliSession> {
        val dir = sessionStateDir()
        if (!dir.exists()) {
            log.info("Copilot CLI session-state directory not found: ${dir.absolutePath}")
            return emptyList()
        }

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { sessionDir ->
                try {
                    readSession(sessionDir)
                } catch (e: Exception) {
                    log.warn("Failed to read Copilot CLI session ${sessionDir.name}: ${e.message}")
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    private fun readSession(sessionDir: File): CopilotCliSession? {
        val eventsFile = File(sessionDir, "events.jsonl")
        if (!eventsFile.isFile) return null

        val messages = CopilotCliSessionParser.parseMessages(eventsFile.readText(Charsets.UTF_8))
        if (messages.isEmpty()) return null

        val yamlFile = File(sessionDir, "workspace.yaml")
        val meta = if (yamlFile.isFile) {
            runCatching { CopilotCliWorkspaceYamlParser.parse(yamlFile.readText(Charsets.UTF_8)) }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val id = meta["id"] ?: sessionDir.name
        val cwd = meta["cwd"]
        val createdAt = parseIsoOrZero(meta["created_at"])
        val updatedAt = parseIsoOrZero(meta["updated_at"]).takeIf { it > 0L } ?: createdAt

        return CopilotCliSession(
            id = id,
            title = resolveTitle(meta, messages, cwd, id),
            cwd = cwd,
            repository = meta["repository"],
            branch = meta["branch"],
            clientName = meta["client_name"],
            createdAt = createdAt,
            updatedAt = updatedAt,
            messages = messages,
        )
    }

    /** Title fallback chain: name -> summary -> first user message -> cwd basename -> id. */
    private fun resolveTitle(
        meta: Map<String, String>,
        messages: List<de.tomschmidtdev.copilotexporter.model.CopilotCliMessage>,
        cwd: String?,
        id: String,
    ): String {
        meta["name"]?.takeIf { it.isNotBlank() }?.let { return it }
        meta["summary"]?.takeIf { it.isNotBlank() }?.let { return it }
        messages.firstOrNull { it.role == Role.USER }
            ?.textBlocks?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { return it.take(80) }
        cwd?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { return it }
        return id
    }

    private fun parseIsoOrZero(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
    }

    private fun sessionStateDir(): File {
        val home = File(System.getProperty("user.home"))
        return File(File(home, ".copilot"), "session-state")
    }
}
