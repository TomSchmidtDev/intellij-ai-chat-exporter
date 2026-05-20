package de.tomschmidtdev.copilotexporter.services

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.ChatSession
import de.tomschmidtdev.copilotexporter.model.Role
import de.tomschmidtdev.copilotexporter.settings.ExporterSettings
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteId
import org.dizitart.no2.common.mapper.JacksonMapperModule
import org.dizitart.no2.mvstore.MVStoreModule
import org.dizitart.no2.store.NitriteMap
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// =============================================================================
// CopilotChatReaderService
//
// GitHub Copilot speichert Chats in Nitrite 4.x-Datenbanken (H2 MVStore 2.x).
// Die Dateien liegen unter:
//   Linux:   ~/.config/github-copilot/<ide>/<session-type>/<hash>/*.db
//   macOS:   ~/Library/Application Support/github-copilot/<ide>/...
//   Windows: %APPDATA%\github-copilot\<ide>\...
//
// LERNHINWEIS: Copilot nutzt H2 MVStore Write-Format 3, das H2 2.x erfordert.
// Nitrite 3.x (mit H2 1.4.200) kann diese Dateien NICHT lesen.
//
// LERNHINWEIS: Copilot registriert Collections als Repositories, deshalb gibt
// db.listCollectionNames() immer 0 zurück. Stattdessen direkt über den Store:
//   db.store.openMap(name, NitriteId::class.java, Document::class.java)
//
// LERNHINWEIS: Felder in Nitrite 4.x Documents sind mit Dot-Notation abgelegt:
//   "name.value", "request.stringContent", "response.contents", "sessionId", etc.
//
// Da Copilot die DB exklusiv sperren kann, wird die *.db-Datei vor dem
// Öffnen in ein temp-Verzeichnis kopiert.
// =============================================================================
@Service(Service.Level.PROJECT)
class CopilotChatReaderService(private val project: Project) {

    private val log = logger<CopilotChatReaderService>()

    // -------------------------------------------------------------------------
    // Mapping: Session-Typ → Dateiname, Session-Collection, Turn-Collection
    // -------------------------------------------------------------------------

    private data class SessionTypeConfig(
        val dbFileName: String,
        val sessionCollection: String,
        val turnCollection: String,
    )

    // LERNHINWEIS: Die Collection-Namen entsprechen den vollqualifizierten
    // Java-Klassennamen der Entity-Klassen im Copilot-Plugin. Nitrite 4.x
    // verwendet diese als MVStore-Map-Namen.
    private val sessionTypes = mapOf(
        "chat-sessions" to SessionTypeConfig(
            dbFileName = "copilot-chat-nitrite.db",
            sessionCollection = "com.github.copilot.chat.session.persistence.nitrite.entity.NtChatSession",
            turnCollection = "com.github.copilot.chat.session.persistence.nitrite.entity.NtTurn",
        ),
        "chat-agent-sessions" to SessionTypeConfig(
            dbFileName = "copilot-agent-sessions-nitrite.db",
            sessionCollection = "com.github.copilot.agent.session.persistence.nitrite.entity.NtAgentSession",
            turnCollection = "com.github.copilot.agent.session.persistence.nitrite.entity.NtAgentTurn",
        ),
        "chat-edit-sessions" to SessionTypeConfig(
            dbFileName = "copilot-edit-sessions-nitrite.db",
            sessionCollection = "com.github.copilot.agent.edit.session.persistence.nitrite.entity.NtEditSession",
            turnCollection = "com.github.copilot.agent.edit.session.persistence.nitrite.entity.NtEditTurn",
        ),
    )

    // -------------------------------------------------------------------------
    // Öffentliche API
    // -------------------------------------------------------------------------

    fun readSessions(): List<ChatSession> {
        val showAllIdes = ExporterSettings.getInstance().state.showAllIdes
        val dbFiles = findAllDatabaseFiles(showAllIdes)
        if (dbFiles.isEmpty()) {
            log.info("No Copilot *.db files found under ${copilotBaseDirs().map { it.absolutePath }}")
            return emptyList()
        }
        return dbFiles.flatMap { (dbFile, config) ->
            readNitriteDb(dbFile, config)
        }.sortedByDescending { if (it.lastModifiedAt > 0L) it.lastModifiedAt else it.createdAt }
    }

    fun diagnose(): DiagnosticReport {
        val baseDirs = copilotBaseDirs()
        val showAllIdes = ExporterSettings.getInstance().state.showAllIdes
        val dbFiles = findAllDatabaseFiles(showAllIdes)
        val report = StringBuilder()

        if (dbFiles.isEmpty()) {
            return DiagnosticReport(
                basePaths = baseDirs.map { it.absolutePath },
                foundFiles = emptyList(),
                details = "No Copilot *.db files found.",
                error = null,
            )
        }

        dbFiles.forEach { (dbFile, config) ->
            // LERNHINWEIS: Pfad-Label enthält IDE-Ordner, Session-Typ und Hash-Verzeichnis.
            // dbFile = ~/.config/github-copilot/<ide>/<session-type>/<hash>/<db>.db
            val ideDir = dbFile.parentFile.parentFile.parentFile.name
            val sessionTypeDir = dbFile.parentFile.parentFile.name
            val hashDir = dbFile.parentFile.name
            val label = "$ideDir/$sessionTypeDir/$hashDir"
            report.appendLine("=== $label ===")
            report.appendLine("  File: ${dbFile.name} (${dbFile.length() / 1024}KB)")

            val tempDir = Files.createTempDirectory("copilot-diag-").toFile()
            val tempFile = File(tempDir, dbFile.name)
            try {
                dbFile.copyTo(tempFile, overwrite = true)
                openNitrite(tempFile) { db ->
                    val store = db.store
                    val sessionMap = store.openMap<NitriteId, Document>(config.sessionCollection, NitriteId::class.java, Document::class.java)
                    val turnMap = store.openMap<NitriteId, Document>(config.turnCollection, NitriteId::class.java, Document::class.java)

                    val allSessions = sessionMap.values().toList()
                    val allTurns = turnMap.values().toList()
                    report.appendLine("  Sessions: ${allSessions.size}, Turns: ${allTurns.size}")

                    // Group turns by sessionId for efficient per-session display
                    val turnsBySession: Map<String, List<Document>> = allTurns
                        .groupBy { doc -> doc.get("sessionId") as? String ?: "" }

                    allSessions
                        .sortedBy { (it.get("createdAt") as? Number)?.toLong() ?: 0L }
                        .forEach { doc: Document ->
                            val id = doc.get("id") as? String ?: "(no id)"
                            val title = doc.get("name.value") as? String ?: "(no title)"
                            report.appendLine("  Session: $title [$id]")
                            report.appendLine("    Created: ${formatTimestamp(doc.get("createdAt"))}")

                            val sessionTurns = (turnsBySession[id] ?: emptyList())
                                .sortedBy { (it.get("createdAt") as? Number)?.toLong() ?: 0L }
                            val activeTurns = sessionTurns.filter { it.get("deletedAt") == null }
                            val deletedCount = sessionTurns.size - activeTurns.size

                            if (sessionTurns.isEmpty()) {
                                report.appendLine("    Turns: 0  → FILTERED (no turns, will not appear in plugin)")
                            } else {
                                val deletedNote = if (deletedCount > 0) ", $deletedCount deleted" else ""
                                report.appendLine("    Turns: ${sessionTurns.size} total, ${activeTurns.size} active$deletedNote")
                                activeTurns.forEach { turn ->
                                    report.appendLine("    Turn [${formatTimestamp(turn.get("createdAt"))}]:")
                                    val userText = (turn.get("request.stringContent") as? String)?.takeIf { it.isNotBlank() }
                                    val reqContents = (turn.get("request.contents") as? String)?.takeIf { it.isNotBlank() }
                                    val hasResponse = turn.get("response.stringContent") != null || turn.get("response.contents") != null
                                    when {
                                        userText != null -> report.appendLine("      USER: ${userText.take(120)}")
                                        reqContents != null -> report.appendLine("      USER (contents): ${reqContents.take(80)}")
                                        else -> report.appendLine("      USER: (no request content)")
                                    }
                                    if (hasResponse) report.appendLine("      ASSISTANT: [response available]")
                                    else report.appendLine("      ASSISTANT: (no response content)")
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                report.appendLine("  ERROR: ${e.message?.take(300)}")
            } finally {
                tempDir.deleteRecursively()
            }
            report.appendLine()
        }

        return DiagnosticReport(
            basePaths = baseDirs.map { it.absolutePath },
            foundFiles = dbFiles.map { (f, _) ->
                val ide = f.parentFile.parentFile.parentFile.name
                val sessionType = f.parentFile.parentFile.name
                val hash = f.parentFile.name
                "$ide/$sessionType/$hash/${f.name}"
            },
            details = report.toString().trimEnd(),
            error = null,
        )
    }

    // -------------------------------------------------------------------------
    // Datenbank lesen
    // -------------------------------------------------------------------------

    private fun readNitriteDb(dbFile: File, config: SessionTypeConfig): List<ChatSession> {
        val tempDir = Files.createTempDirectory("copilot-export-").toFile()
        val tempFile = File(tempDir, dbFile.name)
        return try {
            dbFile.copyTo(tempFile, overwrite = true)
            openNitrite(tempFile) { db ->
                readSessionsFromDb(db, config)
            }
        } catch (e: Exception) {
            log.warn("Failed to read ${dbFile.absolutePath}: ${e.message}")
            emptyList()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Öffnet die Nitrite-4.x-Datenbank und führt den Block aus.
     *
     * LERNHINWEIS: Nitrite 4.x trennt Storage-Backend (MVStoreModule) und
     * Object-Mapping (JacksonMapperModule) in separate Module. Ohne explizites
     * Laden von MVStoreModule würde Nitrite keinen Dateipfad akzeptieren.
     *
     * LERNHINWEIS: readOnly(true) ist beim MVStoreModule verfügbar, aber da wir
     * eine temp-Kopie öffnen, ist es nicht notwendig.
     */
    private fun <T> openNitrite(dbFile: File, block: (Nitrite) -> T): T {
        val db = Nitrite.builder()
            .loadModule(MVStoreModule.withConfig().filePath(dbFile.absolutePath).build())
            .loadModule(JacksonMapperModule())
            .openOrCreate()
        return db.use { block(it) }
    }

    /**
     * Liest Sessions und Turns direkt über den MVStore-Layer.
     *
     * LERNHINWEIS: Copilot registriert seine Collections als Repositories
     * (nicht als Collections), deshalb gibt db.listCollectionNames() 0 zurück.
     * db.store.openMap() umgeht diesen Check und öffnet die Map direkt.
     */
    private fun readSessionsFromDb(db: Nitrite, config: SessionTypeConfig): List<ChatSession> {
        val store = db.store
        val sessionMap = store.openMap<NitriteId, Document>(config.sessionCollection, NitriteId::class.java, Document::class.java)
        val turnMap = store.openMap<NitriteId, Document>(config.turnCollection, NitriteId::class.java, Document::class.java)

        if (sessionMap.size() == 0L) return emptyList()

        return sessionMap.values().toList().mapNotNull { sessionDoc: Document ->
            buildChatSession(sessionDoc, turnMap)
        }
    }

    /**
     * Wandelt ein Nitrite-Session-Dokument in eine ChatSession um.
     *
     * Schema (aus NitriteTest3.java-Erkundung und Copilot-Plugin-Binaries):
     *   NtAgentSession / NtChatSession / NtEditSession:
     *     id: String
     *     name.value: String   (Dot-Notation! Kein verschachteltes Objekt)
     *     createdAt: Long (Unix ms)
     *
     *   NtAgentTurn / NtTurn / NtEditTurn:
     *     sessionId: String  → Fremdschlüssel
     *     request.stringContent: String  (User-Text, direkt)
     *     response.stringContent: String (Assistant-Text, kann leer sein)
     *     response.contents: String      (Agent-Modus: komplex JSON-kodiert)
     *     deletedAt: Long? (null = aktiver Turn)
     *     createdAt: Long
     */
    private fun buildChatSession(
        sessionDoc: Document,
        turnMap: NitriteMap<NitriteId, Document>,
    ): ChatSession? {
        val id = sessionDoc.get("id") as? String ?: return null
        val title = (sessionDoc.get("name.value") as? String)?.take(80) ?: id
        val createdAt = (sessionDoc.get("createdAt") as? Number)?.toLong() ?: 0L

        // LERNHINWEIS: Nitrite 4.x hat keine serverseitige Filterabfrage über openMap().
        // Wir filtern die Turns lazy per Sequence, sodass nicht-passende Dokumente
        // nicht im Heap gehalten werden. sortedBy materialisiert nur die gefilterten Turns.
        val turns = turnMap.values().asSequence()
            .filter { turn: Document -> turn.get("sessionId") == id && turn.get("deletedAt") == null }
            .sortedBy { turn: Document -> (turn.get("createdAt") as? Number)?.toLong() ?: 0L }

        val messages = mutableListOf<ChatMessage>()
        var index = 0
        var turnCount = 0
        var lastModifiedAt = 0L

        turns.forEach { turn ->
            turnCount++
            val turnTs = (turn.get("createdAt") as? Number)?.toLong() ?: 0L
            if (turnTs > lastModifiedAt) lastModifiedAt = turnTs

            val userText = extractUserText(turn)
            val assistantText = extractAssistantText(turn)

            if (!userText.isNullOrBlank()) {
                messages.add(ChatMessage(Role.USER, userText, index++, turnTs))
            }
            if (!assistantText.isNullOrBlank()) {
                messages.add(ChatMessage(Role.ASSISTANT, assistantText, index++, turnTs))
            }
        }

        if (messages.isEmpty()) {
            if (turnCount > 0) {
                log.debug("Session '$title' [$id]: $turnCount active turn(s) found but no text could be extracted")
            }
            return null
        }

        return ChatSession(id = id, title = title, createdAt = createdAt, messages = messages, lastModifiedAt = lastModifiedAt)
    }

    private fun extractUserText(turn: Document): String? {
        // Direkter Text-Inhalt (Chat-Modus)
        (turn.get("request.stringContent") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        // Agent/Edit-Modus: komplexes JSON
        (turn.get("request.contents") as? String)?.takeIf { it.isNotBlank() }?.let { return CopilotJsonParser.parseAgentContents(it) }
        return null
    }

    private fun extractAssistantText(turn: Document): String? {
        // Direkter Text-Inhalt (Chat-Modus)
        (turn.get("response.stringContent") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        // Agent/Edit-Modus: komplexes JSON
        (turn.get("response.contents") as? String)?.takeIf { it.isNotBlank() }?.let { return CopilotJsonParser.parseAgentContents(it) }
        return null
    }

    private fun formatTimestamp(value: Any?): String {
        val ms = (value as? Number)?.toLong() ?: return "N/A"
        if (ms == 0L) return "N/A"
        val formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return "${formatter.format(Instant.ofEpochMilli(ms))} (ms=$ms)"
    }

    // -------------------------------------------------------------------------
    // Pfad-Auflösung (cross-platform)
    // -------------------------------------------------------------------------

    private fun copilotBaseDirs(): List<File> {
        val home = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()

        val candidates = when {
            os.contains("win") -> listOfNotNull(
                System.getenv("APPDATA")?.let { File(it, "github-copilot") },
                System.getenv("LOCALAPPDATA")?.let { File(it, "github-copilot") },
            )
            os.contains("mac") -> listOf(
                File(home, ".config/github-copilot"),
                File(home, "Library/Application Support/github-copilot"),
                File(home, "Library/Application Support/GitHub Copilot"),
            )
            else -> listOf(
                File(home, ".config/github-copilot"),
            )
        }

        return candidates.filter { it.exists() && it.isDirectory }
    }

    private fun findAllDatabaseFiles(showAllIdes: Boolean): List<Pair<File, SessionTypeConfig>> {
        val result = mutableListOf<Pair<File, SessionTypeConfig>>()
        val seen = mutableSetOf<String>()
        val currentIde = currentIdeDirectoryName()

        copilotBaseDirs().forEach { base ->
            base.listFiles()?.filter { it.isDirectory }
                ?.filter { showAllIdes || it.name.lowercase() == currentIde }
                ?.forEach { ideDir ->
                    sessionTypes.forEach { (sessionTypeName, config) ->
                        File(ideDir, sessionTypeName).takeIf { it.exists() }
                            ?.listFiles()?.filter { it.isDirectory }
                            ?.forEach { hashDir ->
                                val dbFile = File(hashDir, config.dbFileName)
                                if (dbFile.exists() && seen.add(dbFile.canonicalPath)) {
                                    result.add(dbFile to config)
                                }
                            }
                    }
                }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Diagnose-Datenklasse
    // -------------------------------------------------------------------------

    companion object {
        /** Returns the lowercase product code used by Copilot as the IDE directory name (e.g. "iu", "py", "cl"). */
        fun currentIdeDirectoryName(): String =
            ApplicationInfo.getInstance().build.productCode.lowercase()
    }

    data class DiagnosticReport(
        val basePaths: List<String>,
        val foundFiles: List<String>,
        val details: String,
        val error: String?,
    ) {
        fun toDisplayString(): String = buildString {
            appendLine("=== Copilot Chat DB Diagnostic ===")
            appendLine("Base paths:")
            basePaths.forEach { appendLine("  $it") }
            appendLine("Databases found: ${foundFiles.size}")
            foundFiles.forEach { appendLine("  • $it") }
            appendLine()
            appendLine("--- Collection Details ---")
            appendLine(details)
            error?.let { appendLine("\nERROR: $it") }
        }
    }
}
