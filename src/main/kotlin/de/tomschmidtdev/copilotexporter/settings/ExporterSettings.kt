package de.tomschmidtdev.copilotexporter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

// =============================================================================
// ExporterSettings – persistente Plugin-Einstellungen
//
// LERNHINWEIS: @State + PersistentStateComponent<S> ist der Standard-Mechanismus
// der IntelliJ Platform zum Speichern von Plugin-Einstellungen.
//
//   @State(name = "...", storages = [...])  →  XML-Datei in IDE-Konfigurationsordner
//   storages = [Storage("datei.xml")]       →  unter <IDE-Config>/options/datei.xml
//
// Das State-Objekt muss ein Plain-Java-Bean sein (keine val, nur var-Felder,
// Default-Konstruktor). IntelliJ serialisiert es per XmlSerializer automatisch.
//
// Service.Level.APP  →  ein einziges Objekt für die gesamte IDE-Instanz (nicht
// pro Projekt). Die Farbeinstellungen gelten für alle HTML-Exports.
// =============================================================================
@Service(Service.Level.APP)
@State(
    name = "CopilotExporterSettings",
    storages = [Storage("copilot-exporter.xml")],
)
class ExporterSettings : PersistentStateComponent<ExporterSettings.State> {

    /**
     * Das serialisierte Zustandsobjekt.
     *
     * LERNHINWEIS: Alle Felder müssen var sein (keine val) und einen Default-Wert
     * haben, damit XmlSerializer den Zustand lesen und schreiben kann.
     * Die Defaults entsprechen dem Catppuccin-Mocha-Farbprofil.
     */
    data class State(
        var background: String = "#1e1e2e",
        var text: String = "#cdd6f4",
        var userMessageBg: String = "#1e3a5f",
        var userMessageBorder: String = "#89b4fa",
        var assistantMessageBg: String = "#1b3a2f",
        var assistantMessageBorder: String = "#a6e3a1",
        var sessionHeaderBg: String = "#181825",
        var sessionTitleColor: String = "#cba6f7",
        var codeBg: String = "#11111b",
        var borderColor: String = "#313244",
    )

    private var myState = State()

    // LERNHINWEIS: getState() liefert das Objekt, das IntelliJ serialisiert.
    override fun getState(): State = myState

    // LERNHINWEIS: loadState() wird beim IDE-Start aufgerufen, sobald IntelliJ
    // den gespeicherten Zustand aus der XML-Datei gelesen hat.
    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        /**
         * Zugriffspunkt von überall im Plugin.
         *
         * LERNHINWEIS: ApplicationManager.getApplication().getService(T::class.java)
         * ist der idiomatische Weg, einen ApplicationService abzufragen.
         * In Kotlin gibt es alternativ: application.service<T>()
         */
        fun getInstance(): ExporterSettings =
            ApplicationManager.getApplication().getService(ExporterSettings::class.java)
    }
}
