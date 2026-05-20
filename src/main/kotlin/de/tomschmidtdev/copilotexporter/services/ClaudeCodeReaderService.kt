package de.tomschmidtdev.copilotexporter.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import java.io.File

@Service(Service.Level.APP)
class ClaudeCodeReaderService {

    private val log = logger<ClaudeCodeReaderService>()

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
        }.sortedByDescending { it.lastModified }
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
}
