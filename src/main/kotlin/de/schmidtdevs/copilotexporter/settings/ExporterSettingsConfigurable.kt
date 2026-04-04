package de.schmidtdevs.copilotexporter.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

// =============================================================================
// ExporterSettingsConfigurable – Bindeglied zwischen IDE-Settings und UI
//
// LERNHINWEIS: Configurable ist der Standard-Extension-Point für Settings-Seiten.
// Die IDE ruft createComponent() auf, wenn der Nutzer die Seite öffnet (lazy).
// apply() / reset() werden beim OK/Apply- bzw. Cancel-Klick aufgerufen.
//
// Registrierung in plugin.xml:
//   <applicationConfigurable instance="...ExporterSettingsConfigurable" .../>
//
// applicationConfigurable  →  erscheint in Settings > Tools (IDE-weit, nicht pro Projekt).
// projectConfigurable      →  erscheint unter Project-Settings (einmal pro Projekt).
// Wir nutzen applicationConfigurable, weil Farbeinstellungen IDE-weit gelten.
// =============================================================================
class ExporterSettingsConfigurable : Configurable {

    private var panel: ExporterSettingsPanel? = null

    override fun getDisplayName(): String = "Copilot Chat Exporter"

    /**
     * Wird beim ersten Öffnen der Settings-Seite aufgerufen.
     * LERNHINWEIS: Lazy – das Panel wird erst erstellt, wenn der Nutzer die Seite öffnet.
     */
    override fun createComponent(): JComponent {
        panel = ExporterSettingsPanel()
        return panel!!
    }

    /**
     * Gibt true zurück, wenn der Nutzer etwas geändert hat (aktiviert den Apply-Button).
     */
    override fun isModified(): Boolean = panel?.isModified() ?: false

    /**
     * Wird beim Klick auf OK/Apply ausgeführt – speichert die Einstellungen.
     */
    override fun apply() {
        panel?.apply()
    }

    /**
     * Wird beim Klick auf Cancel/Reset aufgerufen – setzt das Panel auf gespeicherten Stand zurück.
     */
    override fun reset() {
        panel?.reset()
    }

    /**
     * Wird aufgerufen, wenn die Settings-Seite geschlossen wird.
     * LERNHINWEIS: Hier Panel-Referenz freigeben um Memory Leaks zu vermeiden.
     */
    override fun disposeUIResources() {
        panel = null
    }
}
