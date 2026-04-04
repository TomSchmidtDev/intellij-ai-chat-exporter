package de.schmidtdevs.copilotexporter.export

import de.schmidtdevs.copilotexporter.model.ChatMessage
import de.schmidtdevs.copilotexporter.model.ChatSession
import de.schmidtdevs.copilotexporter.model.Role

/**
 * Konvertiert eine oder mehrere Chat-Sessions in valides Markdown.
 *
 * Das Ausgabeformat ist bewusst einfach gehalten und ohne externe Abhängigkeiten –
 * reines String-Building mit Kotlin's buildString{}.
 */
object MarkdownExporter {

    /**
     * Exportiert eine Liste von Sessions (mit ausgewählten Nachrichten) nach Markdown.
     *
     * @param sessions         Die zu exportierenden Sessions
     * @param selectedMessages Map von Session-ID zu den ausgewählten Nachrichtenindizes.
     *                         Wenn null oder kein Eintrag für eine Session → alle Nachrichten.
     */
    fun export(
        sessions: List<ChatSession>,
        selectedMessages: Map<String, Set<Int>>? = null,
    ): String = buildString {
        sessions.forEachIndexed { sessionIndex, session ->
            if (sessionIndex > 0) {
                appendLine()
                appendLine("---")
                appendLine()
            }

            appendSessionHeader(session)

            val messagesToExport = filterMessages(session, selectedMessages)
            messagesToExport.forEach { message ->
                appendMessage(message)
            }
        }
    }.trimEnd()

    // -------------------------------------------------------------------------
    // Private Hilfsmethoden
    // -------------------------------------------------------------------------

    private fun StringBuilder.appendSessionHeader(session: ChatSession) {
        appendLine("## ${escapeMarkdown(session.title)}")
        appendLine()
        if (session.createdAt > 0) {
            appendLine("_${session.formattedDate} · ${session.messageCount}_")
            appendLine()
        }
    }

    private fun StringBuilder.appendMessage(message: ChatMessage) {
        val prefix = when (message.role) {
            Role.USER -> "**User:**"
            Role.ASSISTANT -> "**Copilot:**"
            Role.UNKNOWN -> "**Unknown:**"
        }

        appendLine(prefix)
        appendLine()

        // Code-Blöcke im Inhalt korrekt einrücken/erhalten
        appendLine(message.content.trimEnd())
        appendLine()
    }

    /**
     * Filtert die Nachrichten einer Session anhand der Selektion.
     * Sortiert nach Index, um die chronologische Reihenfolge zu garantieren.
     */
    private fun filterMessages(
        session: ChatSession,
        selectedMessages: Map<String, Set<Int>>?,
    ): List<ChatMessage> {
        val allowedIndices = selectedMessages?.get(session.id)
        return if (allowedIndices == null) {
            session.messages.sortedBy { it.index }
        } else {
            session.messages.filter { it.index in allowedIndices }.sortedBy { it.index }
        }
    }

    /**
     * Escapet Markdown-Sonderzeichen in Titeln/Headings.
     * Im Nachrichteninhalt belassen wir Formatierung bewusst erhalten.
     */
    private fun escapeMarkdown(text: String): String =
        text.replace("[", "\\[").replace("]", "\\]")
}
