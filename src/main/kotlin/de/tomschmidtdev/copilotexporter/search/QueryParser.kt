package de.tomschmidtdev.copilotexporter.search

class QueryParseException(message: String) : Exception(message)

class QueryParser(input: String) {

    private enum class TokenKind { WORD, PHRASE, AND, OR, LPAREN, RPAREN, EOF }
    private data class Token(val kind: TokenKind, val value: String)

    private val tokens: List<Token> = tokenize(input)
    private var pos = 0

    /** Returns the parsed AST, or null for empty/blank input. Throws [QueryParseException] on syntax errors. */
    fun parse(): SearchNode? {
        if (peek().kind == TokenKind.EOF) return null
        val node = parseOr()
        val remaining = peek()
        if (remaining.kind != TokenKind.EOF) throw QueryParseException("Unexpected token: '${remaining.value}'")
        return node
    }

    private fun tokenize(raw: String): List<Token> {
        val result = mutableListOf<Token>()
        var i = 0
        while (i < raw.length) {
            when {
                raw[i].isWhitespace() -> i++
                raw[i] == '"' -> {
                    val end = raw.indexOf('"', i + 1)
                    if (end == -1) throw QueryParseException("Unclosed quote")
                    result += Token(TokenKind.PHRASE, raw.substring(i + 1, end))
                    i = end + 1
                }
                raw[i] == '(' -> { result += Token(TokenKind.LPAREN, "("); i++ }
                raw[i] == ')' -> { result += Token(TokenKind.RPAREN, ")"); i++ }
                else -> {
                    val start = i
                    while (i < raw.length && !raw[i].isWhitespace() &&
                           raw[i] != '(' && raw[i] != ')' && raw[i] != '"') i++
                    val word = raw.substring(start, i)
                    val kind = when (word.uppercase()) {
                        "AND"  -> TokenKind.AND
                        "OR"   -> TokenKind.OR
                        "NOT"  -> throw QueryParseException("NOT operator is not supported in v1")
                        else   -> TokenKind.WORD
                    }
                    result += Token(kind, word)
                }
            }
        }
        result += Token(TokenKind.EOF, "")
        return result
    }

    private fun peek(): Token = tokens[pos]
    private fun consume(): Token = tokens[pos++]

    private fun parseOr(): SearchNode {
        var node = parseAnd()
        while (peek().kind == TokenKind.OR) {
            consume()
            node = SearchNode.Or(node, parseAnd())
        }
        return node
    }

    private fun parseAnd(): SearchNode {
        var node = parseAtom()
        while (true) {
            node = when (peek().kind) {
                TokenKind.AND -> { consume(); SearchNode.And(node, parseAtom()) }
                TokenKind.WORD, TokenKind.PHRASE, TokenKind.LPAREN ->
                    SearchNode.And(node, parseAtom())  // implicit AND
                else -> break
            }
        }
        return node
    }

    private fun parseAtom(): SearchNode {
        val t = peek()
        return when (t.kind) {
            TokenKind.WORD   -> SearchNode.Term(consume().value)
            TokenKind.PHRASE -> SearchNode.Phrase(consume().value)
            TokenKind.LPAREN -> {
                consume()
                val inner = parseOr()
                if (peek().kind != TokenKind.RPAREN) throw QueryParseException("Expected closing ')'")
                consume()
                inner
            }
            else -> throw QueryParseException("Unexpected token: '${t.value}'")
        }
    }
}
