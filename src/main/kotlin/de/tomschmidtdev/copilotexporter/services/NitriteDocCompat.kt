package de.tomschmidtdev.copilotexporter.services

import org.dizitart.no2.collection.Document

internal object NitriteDocCompat {
    private val sessionIdPaths = listOf("sessionId", "session.id", "chatSessionId", "agentSessionId", "conversationId", "conversation.id")

    fun asDocumentList(value: Any?): List<Document> =
        (value as? List<*>)?.mapNotNull { asDocument(it) } ?: emptyList()

    fun asDocument(value: Any?): Document? = when (value) {
        is Document -> value
        is Map<*, *> -> mapToDocument(value)
        else -> null
    }

    fun getValue(document: Document, path: String): Any? {
        document.get(path)?.let { return it }

        var current: Any? = document
        path.split('.').forEach { segment ->
            current = when (current) {
                is Document -> current.get(segment)
                is Map<*, *> -> current[segment]
                else -> null
            }
            if (current == null) return null
        }
        return current
    }

    fun getString(document: Document, path: String): String? {
        val value = getValue(document, path) ?: return null
        return when (value) {
            is String -> value
            else -> value.toString()
        }
    }

    fun getLong(document: Document, path: String): Long? {
        val value = getValue(document, path) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    fun firstNonBlank(document: Document, vararg paths: String): String? =
        paths.asSequence()
            .mapNotNull { getString(document, it) }
            .firstOrNull { it.isNotBlank() }

    fun matchesSessionId(document: Document, sessionId: String): Boolean =
        sessionIdPaths.any { getString(document, it) == sessionId }

    private fun mapToDocument(map: Map<*, *>): Document {
        val document = Document.createDocument()
        map.forEach { (key, value) ->
            if (key is String) {
                document.put(key, normalizeValue(value))
            }
        }
        return document
    }

    private fun normalizeValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> mapToDocument(value)
        is List<*> -> value.map { normalizeValue(it) }
        else -> value
    }
}
