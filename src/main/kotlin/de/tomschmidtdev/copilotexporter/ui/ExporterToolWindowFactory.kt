package de.tomschmidtdev.copilotexporter.ui

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

// =============================================================================
// ExporterToolWindowFactory
//
// LERNHINWEIS: ToolWindowFactory ist ein Extension Point der IntelliJ Platform.
// Wenn die IDE ein Tool Window zum ersten Mal anzeigt, ruft sie createToolWindowContent()
// auf. Davor existiert das Tool Window nur als Tab ohne Inhalt (lazy loading).
//
// Die Registrierung erfolgt in plugin.xml:
//   <toolWindow factoryClass="...ExporterToolWindowFactory" .../>
//
// Das Tool Window wird NICHT instanziiert, solange der Nutzer es nicht öffnet.
// Das spart Ressourcen beim IDE-Start.
// =============================================================================
class ExporterToolWindowFactory : ToolWindowFactory {

    /**
     * Wird aufgerufen, wenn der Nutzer das Tool Window zum ersten Mal öffnet.
     *
     * LERNHINWEIS: ContentFactory.getInstance().createContent() verpackt
     * eine beliebige JComponent in ein "Content"-Objekt, das dem Tool Window
     * hinzugefügt werden kann. displayName ist der Text im Tab.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val version = PluginManager
            .getPlugin(PluginId.getId("de.tomschmidtdev.copilot-chat-exporter"))
            ?.version

        if (version != null) {
            // stripeTitle = Text im seitlichen Icon-Strip der IDE
            toolWindow.stripeTitle = "AI Chat Exporter $version"
        }

        val tabbedPane = javax.swing.JTabbedPane().apply {
            addTab("Copilot", ExporterPanel(project))
            addTab("Claude Code", ClaudeCodePanel(project))

            val settings = de.tomschmidtdev.copilotexporter.settings.ExporterSettings.getInstance()
            selectedIndex = settings.state.selectedTabIndex.coerceIn(0, tabCount - 1)
            addChangeListener { settings.state.selectedTabIndex = selectedIndex }
        }

        val content = ContentFactory.getInstance()
            .createContent(tabbedPane, null, false)

        toolWindow.contentManager.addContent(content)
    }

    /**
     * Ob das Tool Window in der aktuellen Situation sichtbar sein soll.
     * Wir zeigen es immer – auch ohne Copilot-Daten (mit leerem Zustand).
     */
    override fun shouldBeAvailable(project: Project): Boolean = true
}
