package de.tomschmidtdev.copilotexporter.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import de.tomschmidtdev.copilotexporter.export.HtmlExporter
import de.tomschmidtdev.copilotexporter.export.MarkdownExporter
import de.tomschmidtdev.copilotexporter.model.ChatMessage
import de.tomschmidtdev.copilotexporter.model.ChatSession
import de.tomschmidtdev.copilotexporter.model.Role
import de.tomschmidtdev.copilotexporter.services.CopilotChatReaderService
import de.tomschmidtdev.copilotexporter.settings.ExporterSettingsConfigurable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

// =============================================================================
// ExporterPanel
//
// LERNHINWEIS: IntelliJ-UIs werden mit Standard-Swing-Komponenten gebaut,
// ergänzt durch JetBrains-spezifische Klassen aus com.intellij.ui.*:
//   - JBLabel, JBScrollPane  → wie JLabel/JScrollPane, aber IDE-themen-kompatibel
//   - CheckBoxList<T>         → Liste mit integrierten Checkboxen;
//                               addItem(item, text, selected) steuert die Anzeige
//   - JBSplitter             → resizable Split-Panel (wie JSplitPane, aber besser)
//   - JBUI.Borders.*         → skaliert Abstände korrekt bei High-DPI-Displays
//
// Die gesamte UI wird auf dem Event Dispatch Thread (EDT) aufgebaut und
// modifiziert. DB-Lese-Operationen laufen auf einem Background-Thread
// (ProgressManager.getInstance().run(Task.Backgroundable)).
// =============================================================================
class ExporterPanel(private val project: Project) : JPanel(BorderLayout()) {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private var sessions: List<ChatSession> = emptyList()

    /** Nachrichten der aktuell angezeigten Session, sortiert nach Index. */
    private var currentMessages: List<ChatMessage> = emptyList()

    // -------------------------------------------------------------------------
    // UI-Komponenten
    // -------------------------------------------------------------------------

    // LERNHINWEIS: CheckBoxList<T> nimmt beliebige Objekte als Items.
    // Der anzuzeigende Text wird pro Item in addItem(item, text, selected) gesetzt.
    // CheckBoxList unterstützt HTML-Strings als Text für reiche Formatierung.

    /** Linke Seite: Liste aller Chat-Sessions mit Checkboxen */
    private val sessionList = CheckBoxList<ChatSession>()

    /** Rechte Seite: Nachrichten der ausgewählten Session mit Checkboxen */
    private val messageList = CheckBoxList<ChatMessage>()

    private val previewLabel = JBLabel("Preview").apply {
        border = JBUI.Borders.empty(6, 8, 4, 8)
        font = font.deriveFont(java.awt.Font.BOLD)
    }

    private val statusLabel = JBLabel("Loading…").apply {
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.empty(4, 8)
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        buildLayout()
        loadSessionsInBackground()

        // Wenn eine Session in der linken Liste angeklickt wird, rechts updaten
        sessionList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) updateMessagePreview()
        }
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private fun buildLayout() {
        border = JBUI.Borders.empty(4)

        // --- Toolbar (oben) ---
        // LERNHINWEIS: BorderLayout für die Toolbar erlaubt es, den Settings-Button
        // rechts auszurichten (EAST) während die Aktionsbuttons links bleiben (WEST).
        val toolbarSeparatorBorder = BorderFactory.createMatteBorder(
            0, 0, 1, 0,
            JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        )

        val leftButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JButton("Refresh", AllIcons.Actions.Refresh).apply {
                toolTipText = "Reload chat sessions from Copilot database"
                addActionListener { loadSessionsInBackground() }
            })
            add(JButton("Export MD", AllIcons.FileTypes.Text).apply {
                toolTipText = "Export selected sessions to Markdown"
                addActionListener { doExport(ExportFormat.MARKDOWN) }
            })
            add(JButton("Export HTML", AllIcons.FileTypes.Html).apply {
                toolTipText = "Export selected sessions to HTML"
                addActionListener { doExport(ExportFormat.HTML) }
            })
        }

        // Zahnrad-Button öffnet direkt die Settings-Seite des Plugins
        val settingsBtn = JButton(AllIcons.General.Settings).apply {
            toolTipText = "Open Copilot Chat Exporter settings"
            border = JBUI.Borders.empty(3, 6)
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, ExporterSettingsConfigurable::class.java)
            }
        }

        val toolbar = JPanel(BorderLayout()).apply {
            border = toolbarSeparatorBorder
            add(leftButtons, BorderLayout.WEST)
            add(settingsBtn, BorderLayout.EAST)
        }

        // --- Session-Panel (links) ---
        val sessionPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Sessions").apply {
                border = JBUI.Borders.empty(6, 8, 4, 8)
                font = font.deriveFont(java.awt.Font.BOLD)
            }, BorderLayout.NORTH)
            add(JBScrollPane(sessionList), BorderLayout.CENTER)
        }

        // --- Toggle-Buttons unterhalb des Preview-Titels ---
        val togglePanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JButton("Prompts", AllIcons.General.User).apply {
                toolTipText = "Select / deselect all user prompts"
                addActionListener { toggleRoleSelection(Role.USER) }
            })
            add(JButton("Copilot", AllIcons.Actions.Preview).apply {
                toolTipText = "Select / deselect all Copilot responses"
                addActionListener { toggleRoleSelection(Role.ASSISTANT) }
            })
        }

        val previewHeader = JPanel(BorderLayout()).apply {
            border = BorderFactory.createMatteBorder(
                0, 0, 1, 0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            )
            add(previewLabel, BorderLayout.NORTH)
            add(togglePanel, BorderLayout.SOUTH)
        }

        // --- Message-Panel (rechts) ---
        val messagePanel = JPanel(BorderLayout()).apply {
            add(previewHeader, BorderLayout.NORTH)
            add(JBScrollPane(messageList), BorderLayout.CENTER)
        }

        // --- Splitter: Session-Liste | Message-Preview ---
        // LERNHINWEIS: JBSplitter ist eine verbesserte JSplitPane.
        // proportion: 0.0 = ganz links, 1.0 = ganz rechts; 0.3 = 30% links
        val splitter = JBSplitter(false, 0.3f).apply {
            firstComponent = sessionPanel
            secondComponent = messagePanel
        }

        add(toolbar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    // -------------------------------------------------------------------------
    // Daten laden
    // -------------------------------------------------------------------------

    /**
     * Liest die Xodus-DB auf einem Background-Thread, um den EDT nicht zu blockieren.
     *
     * LERNHINWEIS: Nie blockierende Operationen (I/O, DB-Zugriff) auf dem EDT!
     * Task.Backgroundable zeigt einen Fortschrittsbalken unten in der IDE.
     * onSuccess() wird danach auf dem EDT aufgerufen → sicher für UI-Updates.
     */
    private fun loadSessionsInBackground() {
        setStatus("Loading…")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Reading Copilot chat sessions",
            false,
        ) {
            private var result: List<ChatSession> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                // project.service<T>() holt den registrierten ProjectService
                result = project.service<CopilotChatReaderService>().readSessions()
            }

            override fun onSuccess() {
                sessions = result
                populateSessionList()
            }

            override fun onThrowable(error: Throwable) {
                setStatus("Error: ${error.message}")
            }
        })
    }

    /** Befüllt die Session-Liste mit den geladenen Daten. */
    private fun populateSessionList() {
        sessionList.clear()

        if (sessions.isEmpty()) {
            setStatus("No Copilot chat sessions found in this project.")
            return
        }

        sessions.forEachIndexed { index, session ->
            // LERNHINWEIS: addItem(item, text, selected) – der text-Parameter
            // kann ein HTML-String sein für reiche Formatierung in der Liste.
            val displayText = "<html><b>${truncate(session.title, 30).escapeHtml()}</b>" +
                    "<br><small>${session.formattedDate} · ${session.messageCount}</small></html>"
            sessionList.addItem(session, displayText, index == 0)
        }

        setStatus("${sessions.size} session${if (sessions.size != 1) "s" else ""} found.")

        if (sessions.isNotEmpty()) {
            sessionList.selectedIndex = 0
            updateMessagePreview()
        }
    }

    /** Aktualisiert die rechte Message-Liste anhand der links ausgewählten Session. */
    private fun updateMessagePreview() {
        val selectedSession = getSelectedSessionFromList() ?: return

        val title = truncate(selectedSession.title, 50).escapeHtml()
        previewLabel.text = "<html><b>Preview</b> <span style='color:gray; font-weight:normal'>&mdash; $title</span></html>"

        currentMessages = selectedSession.messages.sortedBy { it.index }
        rebuildMessageList(BooleanArray(currentMessages.size) { true })
    }

    /**
     * Baut die messageList komplett neu auf.
     * Ein einziger Durchlauf verhindert das O(n²)-Problem von setItemSelected.
     */
    private fun rebuildMessageList(checkedStates: BooleanArray) {
        messageList.clear()
        currentMessages.forEachIndexed { i, message ->
            val roleColor = if (message.role == Role.USER) "#6ea8fe" else "#75b798"
            val preview = truncate(message.content.replace("\n", " "), 55).escapeHtml()
            val displayText = "<html><span style='color:$roleColor'>" +
                    "<b>${message.role.displayName}:</b></span> $preview</html>"
            messageList.addItem(message, displayText, checkedStates.getOrElse(i) { true })
        }
    }

    /** Gibt die aktuell in der linken Liste angeklickte Session zurück. */
    private fun getSelectedSessionFromList(): ChatSession? {
        val idx = sessionList.selectedIndex
        return if (idx >= 0) sessionList.getItemAt(idx) else null
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    private enum class ExportFormat(val extension: String) {
        MARKDOWN("md"),
        HTML("html"),
    }

    private fun doExport(format: ExportFormat) {
        val checkedSessions = getCheckedSessions()
        if (checkedSessions.isEmpty()) {
            setStatus("No sessions selected for export.")
            return
        }

        val selectedMessages = buildSelectedMessagesMap()

        // LERNHINWEIS: FileSaverDescriptor beschreibt den Datei-Speichern-Dialog.
        // Die letzte vararg-Parameter-Liste enthält erlaubte Dateiendungen.
        val descriptor = FileSaverDescriptor(
            "Export Copilot Chat",
            "Save as ${format.name.lowercase()} file",
            format.extension,
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val baseDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        // Dateiname aus dem Titel der ersten selektierten Session ableiten.
        // Unerlaubte Dateisystem-Zeichen werden durch "-" ersetzt.
        val defaultName = checkedSessions.firstOrNull()?.title
            ?.take(60)
            ?.replace(Regex("[\\\\/:*?\"<>|]"), "-")
            ?.trim('-', ' ')
            ?.ifBlank { "copilot-chat-export" }
            ?: "copilot-chat-export"
        val wrapper = dialog.save(baseDir, "$defaultName.${format.extension}") ?: return

        val outputFile = wrapper.file

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting chat…", false) {
            override fun run(indicator: ProgressIndicator) {
                val content = when (format) {
                    ExportFormat.MARKDOWN -> MarkdownExporter.export(checkedSessions, selectedMessages)
                    ExportFormat.HTML -> HtmlExporter.export(checkedSessions, selectedMessages)
                }
                outputFile.writeText(content, Charsets.UTF_8)
            }

            override fun onSuccess() {
                setStatus("Exported to: ${outputFile.name}")
                // Datei im IDE-Editor öffnen
                ApplicationManager.getApplication().invokeLater {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile)?.let { vf ->
                        com.intellij.openapi.fileEditor.FileEditorManager
                            .getInstance(project)
                            .openFile(vf, true)
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                setStatus("Export failed: ${error.message}")
            }
        })
    }

    /** Gibt alle Sessions zurück, deren Checkbox aktiv ist. */
    private fun getCheckedSessions(): List<ChatSession> {
        val result = mutableListOf<ChatSession>()
        for (i in 0 until sessionList.model.size) {
            if (sessionList.isItemSelected(i)) {
                sessionList.getItemAt(i)?.let { result.add(it) }
            }
        }
        return result
    }

    /**
     * Baut die Map Session-ID → ausgewählte Nachrichtenindizes.
     * Liest die aktuell in der rechten Liste angezeigten Checkboxen.
     */
    private fun buildSelectedMessagesMap(): Map<String, Set<Int>>? {
        val currentSession = getSelectedSessionFromList() ?: return null
        val selectedIndices = mutableSetOf<Int>()
        for (i in 0 until messageList.model.size) {
            if (messageList.isItemSelected(i)) {
                messageList.getItemAt(i)?.let { selectedIndices.add(it.index) }
            }
        }
        return mapOf(currentSession.id to selectedIndices)
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    private fun setStatus(text: String) {
        SwingUtilities.invokeLater { statusLabel.text = text }
    }

    /**
     * Selektiert alle Nachrichten der gegebenen Rolle, falls nicht alle ausgewählt sind;
     * deselektiert sie, wenn bereits alle ausgewählt sind.
     *
     * Statt setItemSelected pro Item (O(n²)) werden die Zustände einmalig gelesen,
     * geändert und die Liste in einem einzigen Durchlauf neu aufgebaut.
     */
    private fun toggleRoleSelection(role: Role) {
        if (currentMessages.isEmpty()) return
        val roleIndices = currentMessages.indices.filter { currentMessages[it].role == role }
        if (roleIndices.isEmpty()) return

        val checkedStates = BooleanArray(currentMessages.size) { messageList.isItemSelected(it) }
        val allSelected = roleIndices.all { checkedStates[it] }
        val newState = !allSelected
        roleIndices.forEach { checkedStates[it] = newState }

        rebuildMessageList(checkedStates)
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "…" else text

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

}
