package de.schmidtdevs.copilotexporter.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repräsentiert eine vollständige Chat-Session mit GitHub Copilot.
 *
 * @param id         Eindeutige ID der Session (aus der Xodus-Datenbank)
 * @param title      Angezeigter Titel (ggf. aus der ersten Nutzernachricht abgeleitet)
 * @param createdAt  Unix-Timestamp in Millisekunden; 0 wenn unbekannt
 * @param messages   Alle Nachrichten dieser Session in chronologischer Reihenfolge
 */
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messages: List<ChatMessage>,
) {
    /** Formatierter Zeitstempel für die UI-Anzeige. */
    val formattedDate: String
        get() {
            if (createdAt == 0L) return "Unknown date"
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
            return formatter.format(Instant.ofEpochMilli(createdAt))
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
