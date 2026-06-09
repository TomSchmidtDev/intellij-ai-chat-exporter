package de.tomschmidtdev.copilotexporter.search

class SearchMatcher(private val root: SearchNode) {

    /** Returns true if [text] satisfies the full query (case-insensitive). */
    fun matches(text: String): Boolean = eval(root, text.lowercase())

    /**
     * Returns non-overlapping ranges in [text] where query terms appear.
     * Only call this when [matches] returns true — ranges may otherwise
     * belong to only one branch of a failed AND.
     */
    fun matchRanges(text: String): List<IntRange> = collectRanges(root, text.lowercase())

    private fun eval(node: SearchNode, lower: String): Boolean = when (node) {
        is SearchNode.Term   -> lower.contains(node.value.lowercase())
        is SearchNode.Phrase -> lower.contains(node.value.lowercase())
        is SearchNode.And    -> eval(node.left, lower) && eval(node.right, lower)
        is SearchNode.Or     -> eval(node.left, lower) || eval(node.right, lower)
    }

    private fun collectRanges(node: SearchNode, lower: String): List<IntRange> = when (node) {
        is SearchNode.Term   -> occurrences(lower, node.value.lowercase())
        is SearchNode.Phrase -> occurrences(lower, node.value.lowercase())
        is SearchNode.And    -> merge(collectRanges(node.left, lower) + collectRanges(node.right, lower))
        is SearchNode.Or     -> merge(collectRanges(node.left, lower) + collectRanges(node.right, lower))
    }

    private fun occurrences(text: String, term: String): List<IntRange> {
        if (term.isEmpty()) return emptyList()
        val result = mutableListOf<IntRange>()
        var start = 0
        while (true) {
            val idx = text.indexOf(term, start)
            if (idx == -1) break
            result += idx until idx + term.length
            start = idx + 1
        }
        return result
    }

    private fun merge(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val out = mutableListOf(sorted[0])
        for (r in sorted.drop(1)) {
            val last = out.last()
            if (r.first <= last.last + 1) out[out.lastIndex] = last.first..maxOf(last.last, r.last)
            else out += r
        }
        return out
    }
}

/**
 * Builds an HTML string where [ranges] are wrapped in highlight marks.
 * Characters outside ranges are HTML-escaped. Characters inside ranges are
 * also escaped before being wrapped, so `<` and `&` are always safe.
 *
 * Call [SearchMatcher.matchRanges] to obtain [ranges].
 */
fun String.buildHighlightedHtml(ranges: List<IntRange>): String {
    if (ranges.isEmpty()) return htmlEscape()
    val sb = StringBuilder()
    var cursor = 0
    for (r in ranges.sortedBy { it.first }) {
        if (r.first > cursor) sb.append(substring(cursor, r.first).htmlEscape())
        sb.append("<mark style='background:#6b5900;color:#ffd700'>")
        sb.append(substring(r.first, r.last + 1).htmlEscape())
        sb.append("</mark>")
        cursor = r.last + 1
    }
    if (cursor < length) sb.append(substring(cursor).htmlEscape())
    return sb.toString()
}

private fun String.htmlEscape(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
