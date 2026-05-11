package de.tomschmidtdev.copilotexporter.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repräsentiert eine vollständige Chat-Session mit GitHub Copilot.
 *
 * @param id              Eindeutige ID der Session (aus der Nitrite-Datenbank)
 * @param title           Angezeigter Titel
 * @param createdAt       Anlage-Timestamp in Millisekunden; 0 wenn unbekannt
 * @param messages        Alle Nachrichten dieser Session in chronologischer Reihenfolge
 * @param lastModifiedAt  Timestamp des letzten aktiven Turns; 0 wenn unbekannt
 */
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMessage>,
    val lastModifiedAt: Long = 0L,
) {
    /** Formatierter Änderungs-Zeitstempel für die UI-Anzeige (fällt auf createdAt zurück). */
    val formattedDate: String
        get() {
            val ts = if (lastModifiedAt > 0L) lastModifiedAt else createdAt
            if (ts == 0L) return "Unknown date"
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            return formatter.format(Instant.ofEpochMilli(ts))
        }

    /**
     * Kurze Vorschau der ersten Nutzernachricht (max. 60 Zeichen).
     * Wird in der Session-Liste als Subtitle angezeigt.
     */
    val preview: String
        get() {
            val firstUser = messages.firstOrNull { it.role == Role.USER }?.content ?: title
            return if (firstUser.length > 60) firstUser.take(57) + "…" else firstUser
        }

    /** Anzahl der Nachrichten als lesbarer String. */
    val messageCount: String
        get() = "${messages.size} message${if (messages.size != 1) "s" else ""}"
}
