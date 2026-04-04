package de.schmidtdevs.copilotexporter.export

import de.schmidtdevs.copilotexporter.model.ChatMessage
import de.schmidtdevs.copilotexporter.model.ChatSession
import de.schmidtdevs.copilotexporter.model.Role
import de.schmidtdevs.copilotexporter.settings.ExporterSettings

/**
 * Konvertiert Chat-Sessions in selbst-enthaltenes, dark-theme HTML.
 *
 * "Selbst-enthaltend" bedeutet: kein externes CDN, keine Ressourcen-Requests –
 * die HTML-Datei funktioniert offline und in jedem Browser.
 */
object HtmlExporter {

    /**
     * Exportiert Sessions nach HTML.
     *
     * @param sessions         Die zu exportierenden Sessions
     * @param selectedMessages Optionale Filterung – wie in MarkdownExporter
     */
    fun export(
        sessions: List<ChatSession>,
        selectedMessages: Map<String, Set<Int>>? = null,
    ): String = buildString {
        appendLine(htmlHeader())
        appendLine("<body>")
        appendLine("""  <div class="container">""")
        appendLine("""    <h1 class="page-title">Copilot Chat Export</h1>""")

        sessions.forEach { session ->
            val messagesToExport = filterMessages(session, selectedMessages)
            appendSession(session, messagesToExport)
        }

        appendLine("  </div>")
        appendLine("</body>")
        appendLine("</html>")
    }

    // -------------------------------------------------------------------------
    // HTML-Struktur
    // -------------------------------------------------------------------------

    private fun StringBuilder.appendSession(session: ChatSession, messages: List<ChatMessage>) {
        appendLine("""  <section class="session">""")
        appendLine("""    <div class="session-header">""")
        appendLine("""      <h2 class="session-title">${session.title.escapeHtml()}</h2>""")
        if (session.createdAt > 0) {
            appendLine("""      <span class="session-meta">${session.formattedDate} · ${session.messageCount}</span>""")
        }
        appendLine("""    </div>""")
        appendLine("""    <div class="messages">""")

        messages.forEach { message ->
            appendMessage(message)
        }

        appendLine("""    </div>""")
        appendLine("""  </section>""")
    }

    private fun StringBuilder.appendMessage(message: ChatMessage) {
        val cssClass = when (message.role) {
            Role.USER -> "message user-message"
            Role.ASSISTANT -> "message copilot-message"
            Role.UNKNOWN -> "message unknown-message"
        }
        val label = message.role.displayName

        appendLine("""      <div class="$cssClass">""")
        appendLine("""        <div class="message-label">$label</div>""")
        appendLine("""        <div class="message-content">${formatMessageContent(message.content)}</div>""")
        appendLine("""      </div>""")
    }

    /**
     * Wandelt den Nachrichtentext in HTML um.
     * Erkennt Code-Blöcke (```lang ... ```) und stellt sie als <pre><code> dar.
     * Restlicher Text wird als normaler Paragraph formatiert.
     */
    private fun formatMessageContent(content: String): String {
        val result = StringBuilder()
        val codeBlockPattern = Regex("```(\\w*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
        var lastEnd = 0

        for (match in codeBlockPattern.findAll(content)) {
            // Text vor dem Code-Block
            val textBefore = content.substring(lastEnd, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                result.append(wrapParagraph(textBefore))
            }

            val language = match.groupValues[1].escapeHtml()
            val code = match.groupValues[2].escapeHtml()
            val langAttr = if (language.isNotEmpty()) """ class="language-$language"""" else ""
            result.append("""<pre><code$langAttr>$code</code></pre>""")
            result.append("\n")

            lastEnd = match.range.last + 1
        }

        // Verbleibender Text nach dem letzten Code-Block
        val remaining = content.substring(lastEnd).trim()
        if (remaining.isNotEmpty()) {
            result.append(wrapParagraph(remaining))
        }

        return result.toString().ifEmpty { content.escapeHtml() }
    }

    /**
     * Teilt Fließtext an Leerzeilen auf und verpackt jeden Absatz in <p>.
     * Konvertiert außerdem einfache Markdown-Inline-Elemente:
     *   **bold** → <strong>, *italic* → <em>, `code` → <code>
     */
    private fun wrapParagraph(text: String): String =
        text.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { paragraph ->
                val html = paragraph
                    .escapeHtml()
                    .replace(Regex("""\*\*(.+?)\*\*""")) { "<strong>${it.groupValues[1]}</strong>" }
                    .replace(Regex("""\*(.+?)\*""")) { "<em>${it.groupValues[1]}</em>" }
                    .replace(Regex("""`([^`]+)`""")) { "<code>${it.groupValues[1]}</code>" }
                    .replace("\n", "<br>")
                "<p>$html</p>"
            }

    // -------------------------------------------------------------------------
    // CSS + HTML-Head
    // -------------------------------------------------------------------------

    /**
     * Generiert den HTML-Head mit CSS, das die Farben aus ExporterSettings liest.
     *
     * LERNHINWEIS: Die Farben werden zur Export-Zeit aus den gespeicherten Settings
     * gelesen (nicht beim Laden des Exporters). So wirken Einstellungsänderungen
     * sofort beim nächsten Export ohne IDE-Neustart.
     */
    private fun htmlHeader(): String {
        val c = ExporterSettings.getInstance().state
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Copilot Chat Export</title>
          <style>
            /* === Reset & Base === */
            *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
              background: ${c.background};
              color: ${c.text};
              line-height: 1.6;
              padding: 2rem 1rem;
            }

            /* === Layout === */
            .container { max-width: 860px; margin: 0 auto; }

            .page-title {
              font-size: 1.5rem;
              font-weight: 600;
              color: ${c.userMessageBorder};
              margin-bottom: 2rem;
              padding-bottom: 0.75rem;
              border-bottom: 1px solid ${c.borderColor};
            }

            /* === Session === */
            .session {
              margin-bottom: 2.5rem;
              border: 1px solid ${c.borderColor};
              border-radius: 10px;
              overflow: hidden;
            }

            .session-header {
              background: ${c.sessionHeaderBg};
              padding: 1rem 1.25rem;
              border-bottom: 1px solid ${c.borderColor};
              display: flex;
              align-items: baseline;
              gap: 1rem;
              flex-wrap: wrap;
            }

            .session-title {
              font-size: 1.1rem;
              font-weight: 600;
              color: ${c.sessionTitleColor};
            }

            .session-meta {
              font-size: 0.8rem;
              color: ${c.borderColor};
              filter: brightness(1.5);
            }

            /* === Messages === */
            .messages { padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }

            .message {
              border-radius: 8px;
              padding: 0.85rem 1rem;
              max-width: 92%;
            }

            .user-message {
              background: ${c.userMessageBg};
              border-left: 3px solid ${c.userMessageBorder};
              align-self: flex-end;
            }

            .copilot-message {
              background: ${c.assistantMessageBg};
              border-left: 3px solid ${c.assistantMessageBorder};
              align-self: flex-start;
            }

            .unknown-message {
              background: ${c.sessionHeaderBg};
              border-left: 3px solid ${c.borderColor};
            }

            .message-label {
              font-size: 0.72rem;
              font-weight: 700;
              text-transform: uppercase;
              letter-spacing: 0.08em;
              margin-bottom: 0.4rem;
              opacity: 0.7;
            }

            .user-message .message-label   { color: ${c.userMessageBorder}; }
            .copilot-message .message-label { color: ${c.assistantMessageBorder}; }

            .message-content { font-size: 0.95rem; }
            .message-content p { margin-bottom: 0.5rem; }
            .message-content p:last-child { margin-bottom: 0; }

            /* === Code Blocks === */
            pre {
              background: ${c.codeBg};
              border: 1px solid ${c.borderColor};
              border-radius: 6px;
              padding: 0.75rem 1rem;
              overflow-x: auto;
              margin: 0.5rem 0;
            }

            code {
              font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
              font-size: 0.88rem;
              color: ${c.text};
            }

            /* inline code */
            p code {
              background: ${c.borderColor};
              padding: 0.1em 0.35em;
              border-radius: 3px;
            }
          </style>
        </head>
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Hilfsfunktionen
    // -------------------------------------------------------------------------

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

    /** Escapet die fünf HTML-Sonderzeichen. */
    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
