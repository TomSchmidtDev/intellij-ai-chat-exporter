package de.tomschmidtdev.copilotexporter.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import java.io.File

@Service(Service.Level.APP)
class ClaudeCodeReaderService {

    private val log = logger<ClaudeCodeReaderService>()
    private val mapper = ObjectMapper()

    /**
     * Reads all Claude Code sessions from ~/.claude/projects/.
     *
     * @param projectSlugFilter  When non-null, only sessions in this project directory are returned.
     */
    fun readSessions(projectSlugFilter: String? = null): List<ClaudeCodeSession> {
        val projectsDir = claudeProjectsDir() ?: return emptyList()
        if (!projectsDir.exists()) {
            log.info("Claude Code projects directory not found: ${projectsDir.absolutePath}")
            return emptyList()
        }

        val projectDirs = projectsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.let { dirs ->
                if (projectSlugFilter != null) dirs.filter { it.name == projectSlugFilter } else dirs
            }
            ?: return emptyList()

        val desktopTitles = loadDesktopTitles()

        return projectDirs.flatMap { projectDir ->
            projectDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".jsonl") }
                ?.mapNotNull { file ->
                    try {
                        ClaudeJsonParser.parse(file)
                    } catch (e: Exception) {
                        log.warn("Failed to parse ${file.absolutePath}: ${e.message}")
                        null
                    }
                }
                ?: emptyList()
        }
            .map { session ->
                val desktopTitle = desktopTitles[session.id]
                if (desktopTitle != null) session.copy(title = desktopTitle) else session
            }
            .sortedByDescending { it.lastModified }
    }

    /** Returns all distinct project slugs that have at least one session file. */
    fun listProjectSlugs(): List<String> {
        val projectsDir = claudeProjectsDir() ?: return emptyList()
        if (!projectsDir.exists()) return emptyList()
        return projectsDir.listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.any { f -> f.name.endsWith(".jsonl") } == true }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Reads auto-generated session titles from Claude Desktop's local metadata files.
     * Returns a map of CLI session UUID → title.
     *
     * Claude Desktop stores session metadata separately from the JSONL files used by the CLI.
     * Each local_*.json file contains a `cliSessionId` linking it to a CLI session and a
     * `title` field with the auto-generated or user-set session name.
     */
    private fun loadDesktopTitles(): Map<String, String> {
        val sessionsDir = claudeDesktopSessionsDir() ?: return emptyMap()
        if (!sessionsDir.exists()) return emptyMap()

        val result = mutableMapOf<String, String>()
        sessionsDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("local_") && it.name.endsWith(".json") }
            .forEach { file ->
                try {
                    val obj = mapper.readTree(file)
                    val cliId = obj["cliSessionId"]?.asText()?.takeIf { it.isNotBlank() } ?: return@forEach
                    val title = obj["title"]?.asText()?.takeIf { it.isNotBlank() } ?: return@forEach
                    result[cliId] = title
                } catch (e: Exception) {
                    log.debug("Failed to read Claude Desktop session metadata: ${file.name}")
                }
            }
        return result
    }

    private fun claudeProjectsDir(): File? {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()
        val claudeDir = when {
            os.contains("win") -> System.getenv("USERPROFILE")?.let { File(it, ".claude") }
                ?: File(home, ".claude")
            else -> File(home, ".claude")
        }
        return File(claudeDir, "projects")
    }

    private fun claudeDesktopSessionsDir(): File? {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()
        val appSupportDir = when {
            os.contains("mac") -> File(home, "Library/Application Support/Claude")
            os.contains("win") -> System.getenv("APPDATA")?.let { File(it, "Claude") } ?: return null
            else -> return null
        }
        return File(appSupportDir, "claude-code-sessions")
    }
}
