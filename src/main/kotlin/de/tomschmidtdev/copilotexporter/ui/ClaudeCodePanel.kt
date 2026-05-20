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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import de.tomschmidtdev.copilotexporter.export.HtmlExporter
import de.tomschmidtdev.copilotexporter.export.MarkdownExporter
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeMessage
import de.tomschmidtdev.copilotexporter.model.ClaudeCodeSession
import de.tomschmidtdev.copilotexporter.model.Role
import de.tomschmidtdev.copilotexporter.services.ClaudeCodeReaderService
import de.tomschmidtdev.copilotexporter.services.ClaudeJsonParser
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class ClaudeCodePanel(private val project: Project) : JPanel(BorderLayout()) {

    private var sessions: List<ClaudeCodeSession> = emptyList()
    private var currentMessages: List<ClaudeCodeMessage> = emptyList()

    private val sessionList = CheckBoxList<ClaudeCodeSession>()
    private val messageList = CheckBoxList<ClaudeCodeMessage>()

    private val previewLabel = JBLabel("Preview").apply {
        border = JBUI.Borders.empty(6, 8, 4, 8)
        font = font.deriveFont(java.awt.Font.BOLD)
    }
    private val statusLabel = JBLabel("Loading…").apply {
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.empty(4, 8)
    }
    private val projectCombo = JComboBox<String>()

    // Toggle buttons — stored as fields so we can read their state
    private val toggleUser = JToggleButton("User", AllIcons.General.User, true)
    private val toggleAssistant = JToggleButton("Assistant", AllIcons.Actions.Preview, true)
    private val toggleToolCalls = JToggleButton("Tool Calls", AllIcons.Debugger.Console, false)
    private val toggleThinking = JToggleButton("Thinking", AllIcons.Actions.Lightning, false)

    // Known project slugs for mapping combo display names back to slugs
    private var knownSlugs: List<String> = emptyList()

    init {
        buildLayout()
        loadSessionsInBackground()
        sessionList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) updateMessagePreview()
        }
        listOf(toggleUser, toggleAssistant, toggleToolCalls, toggleThinking).forEach { btn ->
            btn.addActionListener { rebuildMessageListPreservingChecks() }
        }
    }

    private fun buildLayout() {
        border = JBUI.Borders.empty(4)

        val separatorBorder = BorderFactory.createMatteBorder(
            0, 0, 1, 0,
            JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        )

        projectCombo.addItem("All Projects")
        projectCombo.addActionListener { loadSessionsInBackground() }

        val leftButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel("Project:"))
            add(projectCombo)
            add(JButton("Refresh", AllIcons.Actions.Refresh).apply {
                toolTipText = "Reload Claude Code sessions"
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

        val toolbar = JPanel(BorderLayout()).apply {
            border = separatorBorder
            add(leftButtons, BorderLayout.WEST)
        }

        val sessionPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Sessions").apply {
                border = JBUI.Borders.empty(6, 8, 4, 8)
                font = font.deriveFont(java.awt.Font.BOLD)
            }, BorderLayout.NORTH)
            add(JBScrollPane(sessionList), BorderLayout.CENTER)
        }

        val togglePanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(toggleUser.apply { toolTipText = "Show/hide user messages" })
            add(toggleAssistant.apply { toolTipText = "Show/hide assistant messages" })
            add(toggleToolCalls.apply { toolTipText = "Show/hide tool calls (hidden by default)" })
            add(toggleThinking.apply { toolTipText = "Show/hide thinking blocks (hidden by default)" })
        }

        val previewHeader = JPanel(BorderLayout()).apply {
            border = BorderFactory.createMatteBorder(
                0, 0, 1, 0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            )
            add(previewLabel, BorderLayout.NORTH)
            add(togglePanel, BorderLayout.SOUTH)
        }

        val messagePanel = JPanel(BorderLayout()).apply {
            add(previewHeader, BorderLayout.NORTH)
            add(JBScrollPane(messageList), BorderLayout.CENTER)
        }

        val splitter = JBSplitter(false, 0.3f).apply {
            firstComponent = sessionPanel
            secondComponent = messagePanel
        }

        add(toolbar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun loadSessionsInBackground() {
        setStatus("Loading…")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reading Claude Code sessions", false) {
            private var result: List<ClaudeCodeSession> = emptyList()
            private var slugs: List<String> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val reader = ApplicationManager.getApplication().service<ClaudeCodeReaderService>()
                slugs = reader.listProjectSlugs()
                val selectedSlug = selectedProjectSlug()
                result = reader.readSessions(projectSlugFilter = selectedSlug)
            }

            override fun onSuccess() {
                refreshProjectCombo(slugs)
                sessions = result
                populateSessionList()
            }

            override fun onThrowable(error: Throwable) {
                setStatus("Error: ${error.message}")
            }
        })
    }

    /** Returns the slug for the currently selected project, or null for "All Projects". */
    private fun selectedProjectSlug(): String? {
        val idx = projectCombo.selectedIndex
        if (idx <= 0) return null
        return knownSlugs.getOrNull(idx - 1) // offset by 1 for "All Projects" at index 0
    }

    private fun refreshProjectCombo(slugs: List<String>) {
        val previousSlug = selectedProjectSlug()
        knownSlugs = slugs

        projectCombo.removeActionListener(projectCombo.actionListeners.firstOrNull())
        projectCombo.removeAllItems()
        projectCombo.addItem("All Projects")
        slugs.forEach { slug ->
            val lastComponent = ClaudeJsonParser.slugToPath(slug)
                .trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: slug
            projectCombo.addItem(lastComponent)
        }

        // Restore previous selection
        val restoreIdx = if (previousSlug != null) slugs.indexOf(previousSlug) + 1 else 0
        projectCombo.selectedIndex = restoreIdx.coerceIn(0, projectCombo.itemCount - 1)
        projectCombo.addActionListener { loadSessionsInBackground() }
    }

    private fun populateSessionList() {
        sessionList.clear()
        if (sessions.isEmpty()) {
            setStatus("No Claude Code sessions found.")
            return
        }
        sessions.forEachIndexed { index, session ->
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

    private fun updateMessagePreview() {
        val session = selectedSession() ?: return
        val title = truncate(session.title, 50).escapeHtml()
        previewLabel.text = "<html><b>Preview</b> <span style='color:gray; font-weight:normal'>&mdash; $title</span></html>"
        currentMessages = session.messages
        rebuildMessageList(BooleanArray(currentMessages.size) { true })
    }

    private fun rebuildMessageListPreservingChecks() {
        val checks = BooleanArray(currentMessages.size) { messageList.isItemSelected(it) }
        rebuildMessageList(checks)
    }

    private fun rebuildMessageList(checkedStates: BooleanArray) {
        messageList.clear()
        currentMessages.forEachIndexed { i, msg ->
            val visible = when (msg.role) {
                Role.USER -> toggleUser.isSelected
                Role.CLAUDE -> toggleAssistant.isSelected
                else -> true
            }
            if (!visible) return@forEachIndexed
            val roleColor = if (msg.role == Role.USER) "#6ea8fe" else "#75b798"
            val roleName = msg.role.displayName
            val preview = truncate(msg.previewText.replace("\n", " "), 55).escapeHtml()
            val displayText = "<html><span style='color:$roleColor'><b>$roleName:</b></span> $preview</html>"
            messageList.addItem(msg, displayText, checkedStates.getOrElse(i) { true })
        }
    }

    private fun selectedSession(): ClaudeCodeSession? {
        val idx = sessionList.selectedIndex
        return if (idx >= 0) sessionList.getItemAt(idx) else null
    }

    private enum class ExportFormat(val extension: String) { MARKDOWN("md"), HTML("html") }

    private fun doExport(format: ExportFormat) {
        val checkedExports = buildCheckedSessionExports()
        if (checkedExports.isEmpty()) {
            setStatus("No sessions selected for export.")
            return
        }

        val descriptor = FileSaverDescriptor(
            "Export Claude Code Chat",
            "Save as ${format.name.lowercase()} file",
            format.extension,
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val baseDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val defaultName = checkedExports.firstOrNull()?.first?.title
            ?.take(60)?.replace(Regex("[\\\\/:*?\"<>|]"), "-")?.trim('-', ' ')?.ifBlank { "claude-code-export" }
            ?: "claude-code-export"
        val wrapper = dialog.save(baseDir, "$defaultName.${format.extension}") ?: return
        val outputFile = wrapper.file

        val showThinking = toggleThinking.isSelected
        val showToolCalls = toggleToolCalls.isSelected

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting chat…", false) {
            override fun run(indicator: ProgressIndicator) {
                val chatSessions = checkedExports.map { (claudeSession, selected) ->
                    claudeSession.toChatSession(selected, showThinking, showToolCalls)
                }
                val content = when (format) {
                    ExportFormat.MARKDOWN -> MarkdownExporter.export(chatSessions)
                    ExportFormat.HTML -> HtmlExporter.export(chatSessions)
                }
                outputFile.writeText(content, Charsets.UTF_8)
            }

            override fun onSuccess() {
                setStatus("Exported to: ${outputFile.name}")
                ApplicationManager.getApplication().invokeLater {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile)?.let { vf ->
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vf, true)
                    }
                }
            }

            override fun onThrowable(error: Throwable) { setStatus("Export failed: ${error.message}") }
        })
    }

    /** Returns (ClaudeCodeSession, selectedMessages) for all checked sessions. */
    private fun buildCheckedSessionExports(): List<Pair<ClaudeCodeSession, List<ClaudeCodeMessage>>> {
        val result = mutableListOf<Pair<ClaudeCodeSession, List<ClaudeCodeMessage>>>()
        for (i in 0 until sessionList.model.size) {
            if (!sessionList.isItemSelected(i)) continue
            val session = sessionList.getItemAt(i) ?: continue
            val selectedMessages = if (session == selectedSession()) {
                val checked = mutableListOf<ClaudeCodeMessage>()
                for (j in 0 until messageList.model.size) {
                    if (messageList.isItemSelected(j)) messageList.getItemAt(j)?.let { checked.add(it) }
                }
                checked
            } else {
                session.messages
            }
            result.add(session to selectedMessages)
        }
        return result
    }

    private fun setStatus(text: String) {
        SwingUtilities.invokeLater { statusLabel.text = text }
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "…" else text

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
