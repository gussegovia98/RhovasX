package dev.willbanders.rhovas.x.parser.rhovas

import dev.willbanders.rhovas.x.parser.Diagnostic
import dev.willbanders.rhovas.x.parser.Lexer
import dev.willbanders.rhovas.x.parser.ParseException
import dev.willbanders.rhovas.x.parser.Token

class RhovasLexer(input: String) : Lexer<RhovasTokenType>(input) {

    override fun lexToken(): Token<RhovasTokenType>? {
        while (peek("[ \t\n\r]")) {
            if (match('\n', '\r') || match('\r', '\n') || match("[\n\r]")) {
                chars.reset()
                chars.newline()
            } else {
                chars.advance()
            }
        }
        chars.reset()
        return when {
            chars[0] == null -> null
            peek("[A-Za-z_]") -> lexIdentifier()
            peek("[0-9]") -> lexNumber()
            peek('\'') -> lexCharacter()
            peek('\"') -> lexString()
            peek('-', '-') -> lexComment()
            else -> lexOperator()
        }
    }

    private fun lexIdentifier(): Token<RhovasTokenType> {
        require(match("[A-Za-z_]"))
        while (match("[A-Za-z_0-9]")) {}
        return chars.emit(RhovasTokenType.IDENTIFIER)
    }

    private fun lexNumber(): Token<RhovasTokenType> {
        require(match("[0-9]"))
        while (match("[0-9]")) {}
        if (match('.', "[0-9]")) {
            while (match("[0-9]")) {}
            return chars.emit(RhovasTokenType.DECIMAL)
        }
        return chars.emit(RhovasTokenType.INTEGER)
    }

    private fun lexCharacter(): Token<RhovasTokenType> {
        require(match('\''))
        require(chars[0] != null) { error(
            "Unterminated character literal.",
            "A character literal must start and end with a single quote `\'`, contain a single character, and cannot span multiple lines, as in `\'c\'`.",
        )}
        require(!match('\'')) { error(
            "Empty character literal.",
            "A character literal must contain a single character, as in `\'c\'`. If a literal single-quote is desired, use an escape as in `\'\\\'\'`.",
        )}
        lexEscape()
        if (!match('\'')) {
            while (match("[^\'\n\r]")) {}
            require(match('\'')) { error(
                "Unterminated character literal.",
                "A character literal must start and end with a single quote `\'`, contain a single character, and cannot span multiple lines, as in `\'c\'`.",
            )}
            throw ParseException(error(
                "Character literal contains multiple characters.",
                "A character literal must contain a single character, as in `\'c\'. If multiple characters is desired, use a string as in `\"abc\"`.",
            ))
        }
        return chars.emit(RhovasTokenType.CHARACTER)
    }

    private fun lexString(): Token<RhovasTokenType> {
        require(match('\"'))
        while (peek("[^\"\n\r]")) { lexEscape() }
        require(match('\"')) { error(
            "Unterminated string literal.",
            "A string literal must start and end with a double quote (\") and cannot span multiple lines."
        )}
        return chars.emit(RhovasTokenType.STRING)
    }

    private fun lexEscape() {
        if (match('\\')) {
            val range = chars.range
            if (match('u')) {
                for (i in 0..3) {
                    if (chars[0] != null) {
                        require(match("[0-9A-F]")) { Diagnostic.Error(
                            "Invalid unicode escape character.",
                            "A unicode escape is in the form \\uXXXX, where X is a hexadecimal digit (0-9 & A-F). If a literal backslash is desired, use an escape as in \"abc\\\\123\".",
                            Diagnostic.Range(range.index + range.length - 1, range.line, range.column + range.length - 1, i + 3),
                            emptySet()
                        )}
                    }
                }
            } else {
                require(match("[bnrt\'\"\\\\]")) { Diagnostic.Error(
                    "Invalid escape character.",
                    "An escape is in the form \\char, where char is one of b, f, n, r, t, \', \", and \\. If a literal backslash is desired, use an escape as in \"abc\\\\123\".",
                    Diagnostic.Range(range.index + range.length - 1, range.line, range.column + range.length - 1, 2),
                    emptySet()
                )}
            }
        } else {
            chars.advance()
        }
    }

    private fun lexComment(): Token<RhovasTokenType>? {
        match('-', '-')
        if (match('[')) {
            while (chars[0] != null && !match('-', '-', ']')) { chars.advance() }
        } else {
            while (match("[^\n\r]")) {}
        }
        chars.reset()
        return lexToken()
    }

    private fun lexOperator(): Token<RhovasTokenType> {
        chars.advance()
        return chars.emit(RhovasTokenType.OPERATOR)
    }

}
