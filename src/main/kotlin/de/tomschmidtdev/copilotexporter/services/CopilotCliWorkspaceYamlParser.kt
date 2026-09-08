package de.tomschmidtdev.copilotexporter.services

/**
 * Liest `~/.copilot/session-state/<id>/workspace.yaml`: flache `key: value`-
 * Paare ohne Verschachtelung (in keinem beobachteten Sample vorhanden), daher
 * bewusst ohne YAML-Bibliothek. Werte, die selbst einen Doppelpunkt enthalten
 * (z. B. `/migrate:workspace ...`), werden korrekt am ERSTEN Doppelpunkt
 * getrennt.
 */
internal object CopilotCliWorkspaceYamlParser {

    fun parse(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val separatorIdx = line.indexOf(':')
            if (separatorIdx <= 0) return@forEach
            val key = line.substring(0, separatorIdx).trim()
            val value = line.substring(separatorIdx + 1).trim().trim('"')
            if (key.isNotEmpty() && value.isNotEmpty()) result[key] = value
        }
        return result
    }
}
