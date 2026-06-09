package de.tomschmidtdev.copilotexporter.search

sealed class SearchNode {
    data class Term(val value: String) : SearchNode()
    data class Phrase(val value: String) : SearchNode()
    data class And(val left: SearchNode, val right: SearchNode) : SearchNode()
    data class Or(val left: SearchNode, val right: SearchNode) : SearchNode()
}
