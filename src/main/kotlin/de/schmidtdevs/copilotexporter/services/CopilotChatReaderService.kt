package de.schmidtdevs.copilotexporter.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import de.schmidtdevs.copilotexporter.model.ChatMessage
import de.schmidtdevs.copilotexporter.model.ChatSession
import de.schmidtdevs.copilotexporter.model.Role
import com.fasterxml.jackson.databind.ObjectMapper
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteId
import org.dizitart.no2.common.mapper.JacksonMapperModule
import org.dizitart.no2.mvstore.MVStoreModule
import org.dizitart.no2.store.NitriteMap
import java.io.File
import java.nio.file.Files

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
    private val objectMapper = ObjectMapper()

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
        val dbFiles = findAllDatabaseFiles()
        if (dbFiles.isEmpty()) {
            log.info("No Copilot *.db files found under ${copilotBaseDirs().map { it.absolutePath }}")
            return emptyList()
        }
        return dbFiles.flatMap { (dbFile, config) ->
            readNitriteDb(dbFile, config)
        }.sortedByDescending { it.createdAt }
    }

    fun diagnose(): DiagnosticReport {
        val baseDirs = copilotBaseDirs()
        val dbFiles = findAllDatabaseFiles()
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
                    report.appendLine("  Sessions: ${sessionMap.size()}, Turns: ${turnMap.size()}")
                    sessionMap.values().asSequence().take(3).forEach { doc: Document ->
                        val id = doc.get("id") as? String ?: "(no id)"
                        val title = doc.get("name.value") as? String ?: "(no title)"
                        val created = doc.get("createdAt")
                        report.appendLine("    Session: $title [$id] created=$created")
                    }
                    turnMap.values().asSequence().take(5).forEach { doc: Document ->
                        val sessionId = doc.get("sessionId") as? String ?: "(no sessionId)"
                        val userText = (doc.get("request.stringContent") as? String)?.take(80)
                        val deleted = doc.get("deletedAt")
                        report.appendLine("    Turn: session=$sessionId deleted=$deleted")
                        userText?.let { report.appendLine("      request.stringContent: $it") }
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

        turns.forEach { turn ->
            val userText = extractUserText(turn)
            val assistantText = extractAssistantText(turn)

            if (!userText.isNullOrBlank()) {
                messages.add(ChatMessage(Role.USER, userText, index++))
            }
            if (!assistantText.isNullOrBlank()) {
                messages.add(ChatMessage(Role.ASSISTANT, assistantText, index++))
            }
        }

        if (messages.isEmpty()) return null

        return ChatSession(id = id, title = title, createdAt = createdAt, messages = messages)
    }

    private fun extractUserText(turn: Document): String? {
        // Direkter Text-Inhalt (Chat-Modus)
        (turn.get("request.stringContent") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        // Agent/Edit-Modus: komplexes JSON
        (turn.get("request.contents") as? String)?.takeIf { it.isNotBlank() }?.let { return parseAgentContents(it) }
        return null
    }

    private fun extractAssistantText(turn: Document): String? {
        // Direkter Text-Inhalt (Chat-Modus)
        (turn.get("response.stringContent") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        // Agent/Edit-Modus: komplexes JSON
        (turn.get("response.contents") as? String)?.takeIf { it.isNotBlank() }?.let { return parseAgentContents(it) }
        return null
    }

    /**
     * Extrahiert den Antwort-Text aus dem dreifach-verschachtelten JSON von Agent/Edit-Nachrichten.
     *
     * Variante A – Agent-Modus:
     *   Ebene 1: { "<uuid>": { "type": "Value", "value": "<json-string>" } }
     *   Ebene 2: { "type": "AgentRound", "data": "<json-string>" }
     *   Ebene 3: { "roundId": N, "reply": "<antworttext>", "toolCalls": [...] }
     *   → Wir nehmen den "reply" der Round mit der höchsten roundId.
     *
     * Variante B – Markdown:
     *   Ebene 2: { "type": "Markdown", "data": "<json-string>" }
     *   Ebene 3: { "text": "<markdown-text>", "annotations": [...] }
     *
     * Variante C – Subgraph (neuere Agent-Sessions):
     *   Ebene 1: { "__first__": { "type": "Subgraph", "value": "<json-string>" }, "__last__": ... }
     *   → "value" enthält wieder ein UUID→Value-Objekt derselben Struktur → rekursiv verarbeiten.
     *
     * "Steps"- und "References"-Einträge werden ignoriert.
     */
    private fun parseAgentContents(contents: String): String {
        return try {
            val outerNode = objectMapper.readTree(contents)
            if (!outerNode.isObject) return contents

            val rounds = mutableListOf<Pair<Int, String>>()
            val markdownTexts = mutableListOf<String>()

            fun processEntries(node: com.fasterxml.jackson.databind.JsonNode) {
                node.fields().forEach { (_, entry) ->
                    val entryType = entry.get("type")?.asText() ?: return@forEach
                    val valueStr = entry.get("value")?.asText() ?: return@forEach

                    when (entryType) {
                        "Subgraph" -> {
                            val subNode = runCatching { objectMapper.readTree(valueStr) }.getOrNull() ?: return@forEach
                            if (subNode.isObject) processEntries(subNode)
                        }
                        "Value" -> {
                            val middle = runCatching { objectMapper.readTree(valueStr) }.getOrNull() ?: return@forEach
                            val type = middle.get("type")?.asText() ?: return@forEach
                            val dataStr = middle.get("data")?.asText() ?: return@forEach
                            when (type) {
                                "AgentRound" -> {
                                    val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull() ?: return@forEach
                                    val reply = inner.get("reply")?.asText() ?: return@forEach
                                    val roundId = inner.get("roundId")?.asInt() ?: 0
                                    if (reply.isNotBlank()) rounds.add(roundId to reply)
                                }
                                "Markdown" -> {
                                    val inner = runCatching { objectMapper.readTree(dataStr) }.getOrNull() ?: return@forEach
                                    val text = inner.get("text")?.asText() ?: return@forEach
                                    if (text.isNotBlank()) markdownTexts.add(text)
                                }
                            }
                        }
                    }
                }
            }

            processEntries(outerNode)

            when {
                rounds.isNotEmpty() -> rounds.maxByOrNull { it.first }!!.second
                markdownTexts.isNotEmpty() -> markdownTexts.joinToString("\n\n")
                else -> contents
            }
        } catch (e: Exception) {
            log.warn("Failed to parse agent contents: ${e.message}")
            contents
        }
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

    private fun findAllDatabaseFiles(): List<Pair<File, SessionTypeConfig>> {
        val result = mutableListOf<Pair<File, SessionTypeConfig>>()
        val seen = mutableSetOf<String>()

        copilotBaseDirs().forEach { base ->
            base.listFiles()?.filter { it.isDirectory }?.forEach { ideDir ->
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
