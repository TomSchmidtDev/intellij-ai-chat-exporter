package de.tomschmidtdev.copilotexporter.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
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
import de.tomschmidtdev.copilotexporter.model.CopilotCliMessage
import de.tomschmidtdev.copilotexporter.model.CopilotCliSession
import de.tomschmidtdev.copilotexporter.model.Role
import de.tomschmidtdev.copilotexporter.services.CopilotCliSessionReaderService
import de.tomschmidtdev.copilotexporter.settings.ExporterSettingsConfigurable
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class CopilotCliPanel(private val project: Project) : JPanel(BorderLayout()) {

    private var sessions: List<CopilotCliSession> = emptyList()
    private var currentMessages: List<CopilotCliMessage> = emptyList()

    private val sessionList = CheckBoxList<CopilotCliSession>()
    private val messageList = object : CheckBoxList<CopilotCliMessage>() {
        init {
            toolTipText = "" // aktiviert ToolTipManager für diese Komponente
        }

        override fun getToolTipText(e: java.awt.event.MouseEvent): String? {
            val index = locationToIndex(e.point)
            if (index < 0) return null
            return getItemAt(index)?.let { buildMessageTooltip(it) }
        }
    }

    private val previewLabel = JBLabel("Preview").apply {
        border = JBUI.Borders.empty(6, 8, 4, 8)
        font = font.deriveFont(java.awt.Font.BOLD)
    }
    private val statusLabel = JBLabel("Loading…").apply {
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.empty(4, 8)
    }

    init {
        buildLayout()
        loadSessionsInBackground()
        sessionList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) updateMessagePreview()
        }
    }

    private fun buildLayout() {
        border = JBUI.Borders.empty(4)

        val separatorBorder = BorderFactory.createMatteBorder(
            0, 0, 1, 0,
            JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        )

        val leftButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JButton("Refresh", AllIcons.Actions.Refresh).apply {
                toolTipText = "Reload Copilot CLI sessions"
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

        val settingsBtn = JButton(AllIcons.General.Settings).apply {
            toolTipText = "Open AI Chat Exporter settings"
            border = JBUI.Borders.empty(3, 6)
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, ExporterSettingsConfigurable::class.java)
            }
        }

        val toolbar = JPanel(BorderLayout()).apply {
            border = separatorBorder
            add(leftButtons, BorderLayout.WEST)
            add(settingsBtn, BorderLayout.EAST)
        }

        val sessionPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Sessions").apply {
                border = JBUI.Borders.empty(6, 8, 4, 8)
                font = font.deriveFont(java.awt.Font.BOLD)
            }, BorderLayout.NORTH)
            add(JBScrollPane(sessionList), BorderLayout.CENTER)
        }

        val togglePanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JButton("Prompts", AllIcons.General.User).apply {
                toolTipText = "Select / deselect all user prompts"
                addActionListener { toggleTypeSelection { it.role == Role.USER } }
            })
            add(JButton("Assistant", AllIcons.Actions.Preview).apply {
                toolTipText = "Select / deselect all assistant messages"
                addActionListener { toggleTypeSelection { it.role == Role.ASSISTANT } }
            })
            add(JButton("Tool Calls", AllIcons.Debugger.Console).apply {
                toolTipText = "Select / deselect all messages with tool calls"
                addActionListener { toggleTypeSelection { it.toolCallBlocks.isNotEmpty() } }
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
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reading Copilot CLI sessions", false) {
            private var result: List<CopilotCliSession> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val reader = ApplicationManager.getApplication().service<CopilotCliSessionReaderService>()
                result = reader.readSessions()
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

    private fun populateSessionList() {
        sessionList.clear()

        if (sessions.isEmpty()) {
            setStatus("No Copilot CLI sessions found.")
            return
        }

        sessions.forEachIndexed { index, session ->
            val title = truncate(session.title, 30).escapeHtml()
            val location = (session.repository ?: session.cwd ?: "unknown location").escapeHtml()
            val branchPart = session.branch?.let { " ($it)" }?.escapeHtml() ?: ""
            val meta = "$location$branchPart · ${session.formattedDate} · ${session.messageCount}"
            val displayText = "<html><b>$title</b><br><small>$meta</small></html>"
            sessionList.addItem(session, displayText, index == 0)
        }

        setStatus("${sessions.size} session${if (sessions.size != 1) "s" else ""} found.")

        sessionList.selectedIndex = 0
        updateMessagePreview()
    }

    private fun updateMessagePreview() {
        val session = selectedSession() ?: return
        val title = truncate(session.title, 50).escapeHtml()
        previewLabel.text = "<html><b>Preview</b> <span style='color:gray; font-weight:normal'>&mdash; $title</span></html>"
        currentMessages = session.messages
        rebuildMessageList(BooleanArray(currentMessages.size) { defaultChecked(currentMessages[it]) })
    }

    private fun defaultChecked(msg: CopilotCliMessage): Boolean = when (msg.role) {
        Role.USER -> true
        Role.ASSISTANT -> msg.textBlocks.isNotEmpty()
        else -> true
    }

    private fun rebuildMessageList(checkedStates: BooleanArray) {
        messageList.clear()
        currentMessages.forEachIndexed { i, msg ->
            val roleColor = if (msg.role == Role.USER) "#6ea8fe" else "#75b798"
            val roleName = msg.role.displayName
            val plain = truncate(msg.previewText.replace("\n", " "), 55).escapeHtml()
            val displayText = "<html><span style='color:$roleColor'><b>$roleName:</b></span> $plain</html>"
            messageList.addItem(msg, displayText, checkedStates.getOrElse(i) { defaultChecked(msg) })
        }
    }

    private fun toggleTypeSelection(filter: (CopilotCliMessage) -> Boolean) {
        if (currentMessages.isEmpty()) return
        val matchingIndices = currentMessages.indices.filter { filter(currentMessages[it]) }
        if (matchingIndices.isEmpty()) return
        val checkedStates = BooleanArray(currentMessages.size) { messageList.isItemSelected(it) }
        val allSelected = matchingIndices.all { checkedStates[it] }
        matchingIndices.forEach { checkedStates[it] = !allSelected }
        rebuildMessageList(checkedStates)
    }

    private fun selectedSession(): CopilotCliSession? {
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
            "Export Copilot CLI Chat",
            "Save as ${format.name.lowercase()} file",
            format.extension,
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val baseDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val defaultName = checkedExports.firstOrNull()?.first?.title
            ?.take(60)?.replace(Regex("[\\\\/:*?\"<>|]"), "-")?.trim('-', ' ')?.ifBlank { "copilot-cli-export" }
            ?: "copilot-cli-export"
        val wrapper = dialog.save(baseDir, "$defaultName.${format.extension}") ?: return
        val outputFile = wrapper.file

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Exporting chat…", false) {
            override fun run(indicator: ProgressIndicator) {
                val chatSessions = checkedExports.map { (cliSession, selected) ->
                    cliSession.toChatSession(selected, showToolCalls = true)
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

    /** Returns (CopilotCliSession, selectedMessages) for all checked sessions. */
    private fun buildCheckedSessionExports(): List<Pair<CopilotCliSession, List<CopilotCliMessage>>> {
        val result = mutableListOf<Pair<CopilotCliSession, List<CopilotCliMessage>>>()
        for (i in 0 until sessionList.model.size) {
            if (!sessionList.isItemSelected(i)) continue
            val session = sessionList.getItemAt(i) ?: continue
            val selectedMessages = if (session == selectedSession()) {
                val checked = mutableListOf<CopilotCliMessage>()
                for (j in 0 until messageList.model.size) {
                    if (messageList.isItemSelected(j)) messageList.getItemAt(j)?.let { checked.add(it) }
                }
                checked
            } else {
                session.messages.filter { defaultChecked(it) }
            }
            result.add(session to selectedMessages)
        }
        return result
    }

    private fun buildMessageTooltip(msg: CopilotCliMessage): String {
        val lines = mutableListOf<String>()
        msg.textBlocks.forEach { lines.addAll(it.lines()) }
        if (msg.toolCallBlocks.isNotEmpty()) {
            lines.add("[Tool Calls]")
            msg.toolCallBlocks.forEach { lines.addAll(it.lines()) }
        }
        if (lines.isEmpty()) return ""
        val sb = StringBuilder("<html>")
        if (msg.timestamp != 0L) {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
            val dateStr = formatter.format(Instant.ofEpochMilli(msg.timestamp))
            sb.append("<b>$dateStr</b><hr>")
        }
        val shown = lines.take(10)
        shown.forEach { line ->
            val display = if (line.length > 100) line.take(99) + "…" else line
            sb.append(display.escapeHtml()).append("<br>")
        }
        if (lines.size > 10) {
            val remaining = lines.size - 10
            sb.append("<i>… ($remaining further line${if (remaining != 1) "s" else ""})</i>")
        }
        sb.append("</html>")
        return sb.toString()
    }

    private fun setStatus(text: String) {
        SwingUtilities.invokeLater { statusLabel.text = text }
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.take(maxLen - 1) + "…" else text

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
