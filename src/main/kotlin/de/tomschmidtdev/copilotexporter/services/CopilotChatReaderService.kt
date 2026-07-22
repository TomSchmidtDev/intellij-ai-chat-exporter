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
                    val turnMaps = resolveTurnMaps(db, config)

                    val allSessions = sessionMap.values().toList()
                    val allTurns = turnMaps.asSequence().flatMap { it.values().asSequence() }.toList()
                    report.appendLine("  Sessions: ${allSessions.size}, Turns: ${allTurns.size}")
                    // Group turns by sessionId for efficient per-session display
                    val turnsBySession: Map<String, List<Document>> = allTurns
                        .groupBy { doc ->
                            NitriteDocCompat.firstNonBlank(
                                doc,
                                "sessionId",
                                "session.id",
                                "chatSessionId",
                                "agentSessionId",
                                "conversationId",
                                "conversation.id",
                            ) ?: ""
                        }

                    allSessions
                        .sortedBy { (it.get("createdAt") as? Number)?.toLong() ?: 0L }
                        .forEach { doc: Document ->
                            val id = NitriteDocCompat.getString(doc, "id") ?: "(no id)"
                            val title = NitriteDocCompat.firstNonBlank(doc, "name.value", "name", "title") ?: "(no title)"
                            report.appendLine("  Session: $title [$id]")
                            report.appendLine("    Created: ${formatTimestamp(NitriteDocCompat.getValue(doc, "createdAt"))}")

                            val joinedTurns = turnsBySession[id] ?: emptyList()
                            val embeddedTurns = extractEmbeddedTurns(doc)
                            val usedEmbeddedFallback = joinedTurns.isEmpty() && embeddedTurns.isNotEmpty()
                            val sessionTurns = (joinedTurns.ifEmpty { embeddedTurns })
                                .sortedBy { (it.get("createdAt") as? Number)?.toLong() ?: 0L }
                            val activeTurns = sessionTurns.filterNot { isTurnDeleted(it) }
                            val deletedCount = sessionTurns.size - activeTurns.size

                            if (sessionTurns.isEmpty()) {
                                report.appendLine("    Turns: 0  → metadata-only fallback session will be shown in plugin")
                            } else {
                                if (usedEmbeddedFallback) {
                                    report.appendLine("    (no turns in separate collection — using turns embedded in session document)")
                                }
                                val deletedNote = if (deletedCount > 0) ", $deletedCount deleted" else ""
                                report.appendLine("    Turns: ${sessionTurns.size} total, ${activeTurns.size} active$deletedNote")
                                activeTurns.forEach { turn ->
                                    report.appendLine("    Turn [${formatTimestamp(NitriteDocCompat.getValue(turn, "createdAt"))}]:")
                                    val userText = NitriteDocCompat.firstNonBlank(turn, "request.stringContent", "request.text")
                                    val reqContents = NitriteDocCompat.getString(turn, "request.contents")?.takeIf { it.isNotBlank() }
                                    val hasResponse = NitriteDocCompat.getValue(turn, "response.stringContent") != null ||
                                        NitriteDocCompat.getValue(turn, "response.contents") != null
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
        val turnMaps = resolveTurnMaps(db, config)

        if (sessionMap.size() == 0L) return emptyList()

        return sessionMap.values().toList().mapNotNull { sessionDoc: Document ->
            buildChatSession(sessionDoc, turnMaps)
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
     *     turns: List<Document>  (neuere Copilot-Versionen betten Turns zusätzlich
     *                              direkt im Session-Dokument ein — s. u.)
     *
     *   NtAgentTurn / NtTurn / NtEditTurn:
     *     sessionId: String  → Fremdschlüssel
     *     request.stringContent: String  (User-Text, direkt)
     *     response.stringContent: String (Assistant-Text, kann leer sein)
     *     response.contents: String      (Agent-Modus: komplex JSON-kodiert)
     *     deletedAt: Long? (null oder 0 = aktiver Turn)
     *     createdAt: Long
     *
     * LERNHINWEIS (Bugfix): Copilot ändert das interne Nitrite-Schema mit jeder
     * größeren Plugin-Version (z. B. Wegfall von NtChatSession/NtTurn zugunsten
     * eines vereinheitlichten Agent-Schemas). Damit neu erstellte Sessions dabei
     * nicht kommentarlos aus der Exportliste verschwinden, arbeiten wir mit zwei
     * Fallback-Ebenen:
     *   1. Falls die separate Turn-Collection keine passenden Einträge liefert,
     *      versuchen wir die im Session-Dokument eingebetteten Turns.
     *   2. Falls für einen gefundenen Turn weder User- noch Assistant-Text
     *      extrahiert werden kann, wird ein Platzhalter statt eines stillen
     *      Verwerfens der ganzen Session eingefügt.
     */
    private fun buildChatSession(
        sessionDoc: Document,
        turnMaps: List<NitriteMap<NitriteId, Document>>,
    ): ChatSession? {
        val id = NitriteDocCompat.getString(sessionDoc, "id") ?: return null
        val title = (NitriteDocCompat.firstNonBlank(sessionDoc, "name.value", "name", "title") ?: id).take(80)
        val createdAt = NitriteDocCompat.getLong(sessionDoc, "createdAt") ?: 0L

        // LERNHINWEIS: Nitrite 4.x hat keine serverseitige Filterabfrage über openMap().
        // Wir filtern die Turns lazy per Sequence, sodass nicht-passende Dokumente
        // nicht im Heap gehalten werden. sortedBy materialisiert nur die gefilterten Turns.
        val joinedTurns = turnMaps.asSequence()
            .flatMap { turnMap -> turnMap.values().asSequence() }
            .filter { turn: Document -> NitriteDocCompat.matchesSessionId(turn, id) && !isTurnDeleted(turn) }
            .toList()

        // Fallback-Ebene 1: eingebettete Turns im Session-Dokument selbst, falls
        // die separate Turn-Collection (noch) nichts Passendes enthält.
        val turns = (joinedTurns.ifEmpty {
            extractEmbeddedTurns(sessionDoc).filterNot { isTurnDeleted(it) }
        }).sortedBy { turn: Document -> (turn.get("createdAt") as? Number)?.toLong() ?: 0L }

        val messages = mutableListOf<ChatMessage>()
        var index = 0
        var turnCount = 0
        var lastModifiedAt = 0L

        turns.forEach { turn ->
            turnCount++
            val turnTs = NitriteDocCompat.getLong(turn, "createdAt") ?: 0L
            if (turnTs > lastModifiedAt) lastModifiedAt = turnTs

            val userText = extractUserText(turn)
            val assistantText = extractAssistantText(turn)

            if (!userText.isNullOrBlank()) {
                messages.add(ChatMessage(Role.USER, userText, index++, turnTs))
            }
            if (!assistantText.isNullOrBlank()) {
                messages.add(ChatMessage(Role.ASSISTANT, assistantText, index++, turnTs))
            }
            // Fallback-Ebene 2: Turn existiert, aber kein Text extrahierbar (z. B.
            // unbekanntes Content-Format einer neueren Copilot-Version) → Platzhalter
            // statt die ganze Session stillschweigend zu verwerfen.
            if (userText.isNullOrBlank() && assistantText.isNullOrBlank()) {
                log.debug("Session '$title' [$id]: turn at $turnTs has no extractable text")
                messages.add(ChatMessage(Role.ASSISTANT, "[Content could not be read — unsupported format]", index++, turnTs))
            }
        }

        if (messages.isEmpty()) {
            // Fallback-Ebene 3: Aus der IDE gestartete Copilot-**CLI**-/Background-Sessions
            // legen im Nitrite-Store nur einen Metadaten-Stub ab; der eigentliche
            // Gesprächsverlauf liegt im Event-Log der CLI unter
            // ~/.copilot/session-state/<id>/events.jsonl. Diesen erschließen wir hier,
            // bevor wir auf den reinen Metadaten-Platzhalter zurückfallen.
            val conversationId = NitriteDocCompat.getString(sessionDoc, "conversationId")
            loadCliSession(id, title, createdAt, conversationId)?.let { return it }
        }

        if (messages.isEmpty()) {
            val modifiedAt = NitriteDocCompat.getLong(sessionDoc, "modifiedAt") ?: createdAt
            val metadataContext = CopilotSessionFallback.MetadataContext(
                targetType = NitriteDocCompat.getString(sessionDoc, "targetType"),
                modeId = NitriteDocCompat.getString(sessionDoc, "modeId"),
                conversationId = NitriteDocCompat.getString(sessionDoc, "conversationId"),
            )
            return CopilotSessionFallback.metadataOnlyOrNull(
                id = id,
                title = title,
                createdAt = createdAt,
                modifiedAt = modifiedAt,
                metadataContext = metadataContext,
            )
        }

        return ChatSession(id = id, title = title, createdAt = createdAt, messages = messages, lastModifiedAt = lastModifiedAt)
    }

    /**
     * Fallback-Ebene 3: Erschließt den Gesprächsverlauf einer aus der IDE
     * gestarteten Copilot-CLI-/Background-Session aus deren Event-Log
     * (`~/.copilot/session-state/<id>/events.jsonl`). Solche Sessions haben im
     * Nitrite-Store nur einen leeren Metadaten-Stub. Die Datei-Auffindung bleibt
     * hier (dünn); das Parsen übernimmt der unit-getestete
     * [CopilotCliSessionParser].
     */
    private fun loadCliSession(
        id: String,
        title: String,
        createdAt: Long,
        conversationId: String?,
    ): ChatSession? {
        val home = System.getProperty("user.home") ?: return null
        val candidateIds = listOf(id, conversationId).filterNotNull().filter { it.isNotBlank() }.distinct()

        for (candidate in candidateIds) {
            val eventsFile = File(home, ".copilot/session-state/$candidate/events.jsonl")
            if (!eventsFile.isFile) continue

            val jsonl = runCatching { eventsFile.readText() }.getOrNull() ?: continue
            val messages = CopilotCliSessionParser.parse(jsonl)
            if (messages.isEmpty()) continue

            val lastModifiedAt = messages.maxOf { it.timestamp }.takeIf { it > 0L } ?: createdAt
            log.debug("Session '$title' [$id]: loaded ${messages.size} messages from CLI event log ($candidate)")
            return ChatSession(
                id = id,
                title = title,
                createdAt = createdAt,
                messages = messages,
                lastModifiedAt = lastModifiedAt,
            )
        }
        return null
    }

    private fun isTurnDeleted(turn: Document): Boolean {
        val deletedAt = NitriteDocCompat.getLong(turn, "deletedAt") ?: return false
        return deletedAt > 0L
    }

    private fun extractUserText(turn: Document): String? {
        // Direkter Text-Inhalt (Chat-Modus)
        NitriteDocCompat.firstNonBlank(turn, "request.stringContent", "request.text")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // Agent/Edit-Modus: komplexes JSON
        NitriteDocCompat.getString(turn, "request.contents")
            ?.takeIf { it.isNotBlank() }
            ?.let { return CopilotJsonParser.parseAgentContents(it) }
        return null
    }

    private fun extractAssistantText(turn: Document): String? {
        // Direkter Text-Inhalt (Chat-Modus)
        NitriteDocCompat.firstNonBlank(turn, "response.stringContent", "response.text")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // Agent/Edit-Modus: komplexes JSON
        NitriteDocCompat.getString(turn, "response.contents")
            ?.takeIf { it.isNotBlank() }
            ?.let { return CopilotJsonParser.parseAgentContents(it) }
        return null
    }

    private fun extractEmbeddedTurns(sessionDoc: Document): List<Document> {
        val explicit = listOf("turns", "messages", "entries")
            .flatMap { key -> NitriteDocCompat.asDocumentList(NitriteDocCompat.getValue(sessionDoc, key)) }
        if (explicit.isNotEmpty()) return explicit

        return sessionDoc.getFields()
            .asSequence()
            .flatMap { fieldName -> NitriteDocCompat.asDocumentList(sessionDoc.get(fieldName)).asSequence() }
            .filter { turn ->
                NitriteDocCompat.getValue(turn, "request") != null ||
                    NitriteDocCompat.getValue(turn, "response") != null ||
                    NitriteDocCompat.getValue(turn, "request.stringContent") != null ||
                    NitriteDocCompat.getValue(turn, "response.stringContent") != null
            }
            .toList()
    }

    private fun resolveTurnMaps(db: Nitrite, config: SessionTypeConfig): List<NitriteMap<NitriteId, Document>> {
        val store = db.store
        val preferred = store.openMap<NitriteId, Document>(config.turnCollection, NitriteId::class.java, Document::class.java)
        val fallbackNames = getStoreMapNames(store)
            .asSequence()
            .filterNot { it.startsWith("\$nitrite_") }
            .filter { it != config.turnCollection }
            .filter { it.contains("Turn", ignoreCase = true) }
            .toList()

        val fallbacks: List<NitriteMap<NitriteId, Document>> = fallbackNames.mapNotNull { name ->
            runCatching { store.openMap<NitriteId, Document>(name, NitriteId::class.java, Document::class.java) }.getOrNull()
        }

        return buildList {
            add(preferred)
            fallbacks.forEach { fallback ->
                if (fallback.size() > 0L) add(fallback)
            }
        }
    }

    private fun getStoreMapNames(store: Any): List<String> {
        val catalogMethod = store::class.java.methods.firstOrNull { it.name == "getCatalog" && it.parameterCount == 0 }
            ?: return emptyList()
        val catalog = runCatching { catalogMethod.invoke(store) }.getOrNull() ?: return emptyList()
        val reposMethod = catalog::class.java.methods.firstOrNull { it.name == "getRepositoryNames" && it.parameterCount == 0 }
            ?: return emptyList()
        val repos = runCatching { reposMethod.invoke(catalog) }.getOrNull() ?: return emptyList()
        return (repos as? Iterable<*>)?.mapNotNull { it?.toString() } ?: emptyList()
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
