package de.tomschmidtdev.copilotexporter.ui

import com.intellij.ide.plugins.PluginManagerCore
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
        val panel = ExporterPanel(project)

        // LERNHINWEIS: PluginManagerCore.getPlugin(PluginId) liefert zur Laufzeit
        // die Plugin-Metadaten aus dem gebauten Plugin-Manifest. version ist die
        // in build.gradle.kts gesetzte Version (z.B. "1.3.0").
        val version = PluginManagerCore
            .getPlugin(PluginId.getId("de.tomschmidtdev.copilot-chat-exporter"))
            ?.version
        if (version != null) {
            // stripeTitle = Text im seitlichen Icon-Strip der IDE
            toolWindow.stripeTitle = "Copilot Chat Exporter $version"
        }

        val content = ContentFactory.getInstance()
            .createContent(
                panel,
                null,
                false,
            )

        toolWindow.contentManager.addContent(content)
    }

    /**
     * Ob das Tool Window in der aktuellen Situation sichtbar sein soll.
     * Wir zeigen es immer – auch ohne Copilot-Daten (mit leerem Zustand).
     */
    override fun shouldBeAvailable(project: Project): Boolean = true
}
