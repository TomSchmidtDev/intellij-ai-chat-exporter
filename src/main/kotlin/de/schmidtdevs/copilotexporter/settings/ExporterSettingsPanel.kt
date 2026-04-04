package de.schmidtdevs.copilotexporter.settings

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorPanel
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import de.schmidtdevs.copilotexporter.services.CopilotChatReaderService
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

// =============================================================================
// ExporterSettingsPanel – Swing-UI für die Plugin-Einstellungen
//
// Aufbau:
//   1. "Debug DB"-Button: Diagnose-Tool für die Copilot-Datenbank
//   2. Trennlinie
//   3. HTML-Export-Farben: Profilauswahl + einzelne ColorPanel-Felder
//
// LERNHINWEIS: ColorPanel (com.intellij.ui.ColorPanel) ist eine IntelliJ-
// Komponente, die einen farbigen Rechteck-Button rendert. Klick öffnet den
// nativen Farb-Dialog. selectedColor get/set für Zugriff auf den Wert.
//
// LERNHINWEIS: FormBuilder.createFormBuilder() baut ein Grid-Layout aus
// Label-Komponenten-Paaren ohne manuellen GridBagLayout-Code.
// =============================================================================
class ExporterSettingsPanel : JPanel() {

    // -------------------------------------------------------------------------
    // Profil-Auswahl
    // -------------------------------------------------------------------------
    private val profileCombo = JComboBox(ColorProfiles.ALL_WITH_CUSTOM.toTypedArray())

    // -------------------------------------------------------------------------
    // Farb-Felder (ColorPanel = IntelliJ-Komponente mit eingebautem Color-Picker)
    // -------------------------------------------------------------------------
    private val bgColor = ColorPanel()
    private val textColor = ColorPanel()
    private val userMsgBg = ColorPanel()
    private val userMsgBorder = ColorPanel()
    private val asstMsgBg = ColorPanel()
    private val asstMsgBorder = ColorPanel()
    private val sessionHeaderBg = ColorPanel()
    private val sessionTitleColor = ColorPanel()
    private val codeBg = ColorPanel()
    private val borderColor = ColorPanel()

    /** Alle ColorPanels in Reihenfolge, mit get/set-Lambdas gegen ExporterSettings.State */
    private data class ColorEntry(
        val panel: ColorPanel,
        val getter: (ExporterSettings.State) -> String,
        val setter: (ExporterSettings.State, String) -> Unit,
        val fromProfile: (ColorProfile) -> String,
    )

    private val entries = listOf(
        ColorEntry(bgColor,          { it.background },             { s, v -> s.background = v },             { it.background }),
        ColorEntry(textColor,        { it.text },                   { s, v -> s.text = v },                   { it.text }),
        ColorEntry(userMsgBg,        { it.userMessageBg },          { s, v -> s.userMessageBg = v },          { it.userMessageBg }),
        ColorEntry(userMsgBorder,    { it.userMessageBorder },      { s, v -> s.userMessageBorder = v },      { it.userMessageBorder }),
        ColorEntry(asstMsgBg,        { it.assistantMessageBg },     { s, v -> s.assistantMessageBg = v },     { it.assistantMessageBg }),
        ColorEntry(asstMsgBorder,    { it.assistantMessageBorder }, { s, v -> s.assistantMessageBorder = v }, { it.assistantMessageBorder }),
        ColorEntry(sessionHeaderBg,  { it.sessionHeaderBg },        { s, v -> s.sessionHeaderBg = v },        { it.sessionHeaderBg }),
        ColorEntry(sessionTitleColor,{ it.sessionTitleColor },      { s, v -> s.sessionTitleColor = v },      { it.sessionTitleColor }),
        ColorEntry(codeBg,           { it.codeBg },                 { s, v -> s.codeBg = v },                 { it.codeBg }),
        ColorEntry(borderColor,      { it.borderColor },            { s, v -> s.borderColor = v },            { it.borderColor }),
    )

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        layout = java.awt.BorderLayout()
        border = JBUI.Borders.empty(8)

        val content = buildFormPanel()
        add(content, java.awt.BorderLayout.NORTH)

        // Profil-Auswahl: Farben sofort übernehmen
        profileCombo.addActionListener {
            val selected = profileCombo.selectedItem as? ColorProfile ?: return@addActionListener
            if (selected != ColorProfiles.CUSTOM) applyProfile(selected)
        }

        // Manuelle Farb-Änderung → Dropdown auf "Custom" setzen
        entries.forEach { entry ->
            entry.panel.addActionListener {
                if (profileCombo.selectedItem != ColorProfiles.CUSTOM) {
                    profileCombo.selectedItem = ColorProfiles.CUSTOM
                }
            }
        }

        reset()
    }

    // -------------------------------------------------------------------------
    // UI-Aufbau
    // -------------------------------------------------------------------------

    private fun buildFormPanel(): JPanel {
        // --- Zwei-Spalten-Zeilen für Nachrichtenfarben (Hintergrund + Rahmen) ---
        fun colorPair(bgPanel: ColorPanel, bgLabel: String, borderPanel: ColorPanel): JPanel =
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(bgPanel)
                add(Box.createHorizontalStrut(6))
                add(JBLabel(bgLabel).apply { font = font.deriveFont(Font.PLAIN, 11f) })
                add(Box.createHorizontalStrut(12))
                add(borderPanel)
                add(Box.createHorizontalStrut(6))
                add(JBLabel("Border").apply { font = font.deriveFont(Font.PLAIN, 11f) })
            }

        // --- Profil-Zeile ---
        val profileRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(profileCombo.apply { maximumSize = Dimension(240, preferredSize.height) })
        }

        // --- Farbformular mit FormBuilder ---
        // LERNHINWEIS: FormBuilder.createFormBuilder() erstellt ein Panel mit
        // GridBagLayout-ähnlicher Ausrichtung. addLabeledComponent(label, comp)
        // setzt Label links, Komponente rechts.
        val colorForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("Profile:", profileRow)
            .addVerticalGap(4)
            .addLabeledComponent("Background:", bgColor)
            .addLabeledComponent("Text:", textColor)
            .addLabeledComponent("User message:", colorPair(userMsgBg, "Background", userMsgBorder))
            .addLabeledComponent("Assistant message:", colorPair(asstMsgBg, "Background", asstMsgBorder))
            .addLabeledComponent("Session header:", sessionHeaderBg)
            .addLabeledComponent("Session title:", sessionTitleColor)
            .addLabeledComponent("Code background:", codeBg)
            .addLabeledComponent("Border:", borderColor)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // --- Debug-Bereich (unten) ---
        val debugSection = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JButton("Debug DB").apply {
                toolTipText = "Show raw Copilot database contents for debugging"
                addActionListener { runDebugDiagnostics() }
            })
            add(Box.createHorizontalStrut(8))
            add(JBLabel("Inspect raw Copilot DB structure and document counts").apply {
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
        }

        return JPanel(java.awt.BorderLayout()).apply {
            border = JBUI.Borders.empty(0)
            add(TitledSeparator("HTML Export Colors"), java.awt.BorderLayout.NORTH)
            add(colorForm, java.awt.BorderLayout.CENTER)
            add(debugSection, java.awt.BorderLayout.SOUTH)
        }
    }

    // -------------------------------------------------------------------------
    // Configurable-API (aufgerufen von ExporterSettingsConfigurable)
    // -------------------------------------------------------------------------

    fun isModified(): Boolean {
        val state = ExporterSettings.getInstance().state
        return entries.any { entry ->
            entry.panel.selectedColor?.toHex() != entry.getter(state)
        }
    }

    fun apply() {
        val state = ExporterSettings.getInstance().state
        entries.forEach { entry ->
            entry.panel.selectedColor?.toHex()?.let { hex -> entry.setter(state, hex) }
        }
    }

    fun reset() {
        val state = ExporterSettings.getInstance().state
        entries.forEach { entry ->
            entry.panel.selectedColor = Color.decode(entry.getter(state))
        }
        // Passendes Profil im Dropdown anzeigen (oder "Custom")
        profileCombo.selectedItem = detectProfile(state)
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    /** Wendet ein Farbprofil auf alle ColorPanels an. */
    private fun applyProfile(profile: ColorProfile) {
        entries.forEach { entry ->
            entry.panel.selectedColor = Color.decode(entry.fromProfile(profile))
        }
    }

    /**
     * Prüft, ob der gespeicherte Zustand einem vordefinierten Profil entspricht.
     * Gibt das passende Profil zurück, sonst CUSTOM.
     */
    private fun detectProfile(state: ExporterSettings.State): ColorProfile {
        return ColorProfiles.ALL.find { profile ->
            entries.all { entry -> entry.fromProfile(profile) == entry.getter(state) }
        } ?: ColorProfiles.CUSTOM
    }

    /**
     * Führt die DB-Diagnose im Hintergrund aus und zeigt das Ergebnis in einem Dialog.
     *
     * LERNHINWEIS: CopilotChatReaderService ist ein ProjectService. Da die Einstellungs-
     * Seite keine Projekt-Referenz hat (applicationConfigurable), wird hier das erste
     * geöffnete Projekt verwendet. Die Diagnose liest aus ~/.config/github-copilot/ und
     * ist damit nicht wirklich projekt-spezifisch.
     */
    private fun runDebugDiagnostics() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: run {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                "No project is currently open. Open a project first.",
                "Debug DB"
            )
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reading DB diagnostics…", false) {
            private var report: CopilotChatReaderService.DiagnosticReport? = null

            override fun run(indicator: ProgressIndicator) {
                report = project.service<CopilotChatReaderService>().diagnose()
            }

            override fun onSuccess() {
                val text = report?.toDisplayString() ?: "No report generated."

                object : DialogWrapper(project) {
                    init {
                        title = "Copilot DB Diagnostic"
                        init()
                    }

                    override fun createCenterPanel(): JComponent {
                        val textArea = JBTextArea(text).apply {
                            isEditable = false
                            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                            lineWrap = false
                        }
                        return JBScrollPane(textArea).apply {
                            preferredSize = Dimension(800, 500)
                        }
                    }

                    override fun createActions() = arrayOf(okAction)
                }.show()
            }
        })
    }

    // -------------------------------------------------------------------------
    // Color-Hilfsfunktionen
    // -------------------------------------------------------------------------

    private fun Color.toHex(): String =
        String.format("#%02x%02x%02x", red, green, blue)
}
