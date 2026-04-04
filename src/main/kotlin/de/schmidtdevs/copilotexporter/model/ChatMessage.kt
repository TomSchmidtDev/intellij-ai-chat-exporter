package de.schmidtdevs.copilotexporter.model

/**
 * Repräsentiert eine einzelne Nachricht in einem Copilot-Chat.
 *
 * @param role     Wer die Nachricht gesendet hat (Nutzer oder Copilot)
 * @param content  Der Textinhalt der Nachricht
 * @param index    Position in der Session (0-basiert), für korrekte Reihenfolge
 */
data class ChatMessage(
    val role: Role,
    val content: String,
    val index: Int,
)

/**
 * Unterscheidet zwischen Nutzer-Eingaben und Copilot-Antworten.
 *
 * LERNHINWEIS: Sealed-Klassen oder Enums sind bei fixen Varianten idiomatischer
 * als String-Konstanten, da der Compiler Vollständigkeit prüfen kann.
 */
enum class Role(val displayName: String) {
    USER("User"),
    ASSISTANT("Copilot"),
    UNKNOWN("Unknown"),
    ;

    companion object {
        /**
         * Versucht, einen rohen String-Wert aus der Datenbank einer Rolle zuzuordnen.
         * Groß-/Kleinschreibung wird ignoriert, um Varianten wie "user", "USER" etc.
         * abzudecken.
         */
        fun fromRawValue(raw: String?): Role =
            when (raw?.lowercase()?.trim()) {
                "user", "human" -> USER
                "assistant", "copilot", "bot", "model" -> ASSISTANT
                else -> UNKNOWN
            }
    }
}
