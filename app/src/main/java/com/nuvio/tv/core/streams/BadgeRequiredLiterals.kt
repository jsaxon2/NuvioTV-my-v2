package com.nuvio.tv.core.streams

import java.util.Locale

/**
 * Fork: works out, ahead of time, words a badge rule cannot match without. The result is a set of lowercase
 * strings, at least one of which appears (ignoring case) in any text the rule matches; or null when that can't
 * be told safely.
 *
 * Matching then runs a rule only on sources whose text contains one of its words, so most of a large pack's rules
 * are skipped with a plain `contains`. Only rules that cannot match are skipped: badges come out the same.
 *
 * The reading is conservative. Anything it doesn't fully understand (character classes, `.`, unknown escapes,
 * back-references, comment mode) ends the word at that point, or gives the rule no words at all.
 */
internal object BadgeRequiredLiterals {

    /** Shorter words are too common to be worth checking. */
    private const val MIN_LENGTH = 2

    fun of(pattern: String): Set<String>? {
        if (pattern.contains("\\Q") || Regex("""\(\?[a-zA-Z-]*x""").containsMatchIn(pattern)) return null
        return try {
            val parser = Parser(pattern)
            val result = parser.alternation()
            if (parser.pos != pattern.length) return null
            result?.takeIf { set -> set.isNotEmpty() && set.all { it.length >= MIN_LENGTH } }
        } catch (_: IllegalStateException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private sealed interface Escape {
        data object ZeroWidth : Escape
        data object Class : Escape
        data class Literal(val char: Char) : Escape
    }

    private enum class Quant { NONE, OPTIONAL, REQUIRED_REPEAT }

    private class Parser(private val p: String) {
        var pos = 0

        /** Alternatives separated by `|`: each must have a requirement, and any one of them may be the one used. */
        fun alternation(): Set<String>? {
            val options = mutableListOf<Set<String>?>()
            options += sequence()
            while (pos < p.length && p[pos] == '|') {
                pos++
                options += sequence()
            }
            if (options.any { it == null }) return null
            return options.flatMapTo(LinkedHashSet()) { it!! }
        }

        /** Items in a row up to `|` or `)`. Returns the most selective single requirement among them. */
        private fun sequence(): Set<String>? {
            val requirements = mutableListOf<Set<String>>()
            val run = StringBuilder()

            fun endRun() {
                if (run.length >= MIN_LENGTH) requirements += setOf(run.toString().lowercase(Locale.ROOT))
                run.setLength(0)
            }

            fun literal(ch: Char) {
                when (peekQuantifier()) {
                    Quant.NONE -> run.append(ch)
                    Quant.OPTIONAL -> {
                        quantifier()
                        endRun()
                    }
                    Quant.REQUIRED_REPEAT -> {
                        run.append(ch)
                        quantifier()
                        endRun()
                    }
                }
            }

            while (pos < p.length && p[pos] != '|' && p[pos] != ')') {
                val c = p[pos]
                when (c) {
                    '(' -> {
                        endRun()
                        val group = group()
                        val optional = quantifier()
                        if (group != null && !optional) requirements += group
                    }
                    '[' -> {
                        endRun()
                        skipClass()
                        quantifier()
                    }
                    '.' -> {
                        endRun()
                        pos++
                        quantifier()
                    }
                    '^', '$' -> {
                        endRun()
                        pos++
                    }
                    '\\' -> when (val e = escape()) {
                        Escape.ZeroWidth -> {
                            endRun()
                            quantifier()
                        }
                        Escape.Class -> {
                            endRun()
                            quantifier()
                        }
                        is Escape.Literal -> literal(e.char)
                    }
                    '*', '+', '?', '{' -> throw IllegalStateException("dangling quantifier")
                    else -> {
                        pos++
                        literal(c)
                    }
                }
            }
            endRun()
            // Prefer the requirement whose shortest word is longest, then the one with fewer choices.
            return requirements.maxWithOrNull(
                compareBy<Set<String>>({ set -> set.minOf { it.length } }, { set -> -set.size })
            )
        }

        private fun peekQuantifier(): Quant {
            if (pos >= p.length) return Quant.NONE
            return when (p[pos]) {
                '?', '*' -> Quant.OPTIONAL
                '+' -> Quant.REQUIRED_REPEAT
                '{' -> when (braceMin()) {
                    null -> Quant.NONE
                    0 -> Quant.OPTIONAL
                    else -> Quant.REQUIRED_REPEAT
                }
                else -> Quant.NONE
            }
        }

        private fun braceMin(): Int? {
            val close = p.indexOf('}', pos)
            if (close < 0) return null
            return p.substring(pos + 1, close).substringBefore(',').trim().toIntOrNull()
        }

        /** Consumes a quantifier if one follows. True when it allows zero repetitions. */
        private fun quantifier(): Boolean {
            if (pos >= p.length) return false
            val optional = when (p[pos]) {
                '?', '*' -> { pos++; true }
                '+' -> { pos++; false }
                '{' -> {
                    val min = braceMin() ?: return false
                    pos = p.indexOf('}', pos) + 1
                    min == 0
                }
                else -> return false
            }
            // Lazy or possessive suffix.
            if (pos < p.length && (p[pos] == '?' || p[pos] == '+')) pos++
            return optional
        }

        /** Reads `( ... )`. Negative lookarounds and inline flags require nothing. */
        private fun group(): Set<String>? {
            pos++ // (
            var negative = false
            if (pos < p.length && p[pos] == '?') {
                val rest = p.substring(pos)
                when {
                    rest.startsWith("?:") || rest.startsWith("?>") || rest.startsWith("?=") -> pos += 2
                    rest.startsWith("?!") -> { negative = true; pos += 2 }
                    rest.startsWith("?<=") -> pos += 3
                    rest.startsWith("?<!") -> { negative = true; pos += 3 }
                    rest.startsWith("?<") -> {
                        val close = p.indexOf('>', pos)
                        if (close < 0) throw IllegalStateException("bad group name")
                        pos = close + 1
                    }
                    else -> {
                        // Inline flags: (?i) on their own, or (?i:...) as a group.
                        var i = pos + 1
                        while (i < p.length && (p[i].isLetter() || p[i] == '-')) i++
                        if (i < p.length && p[i] == ')') {
                            pos = i + 1
                            return null
                        }
                        if (i < p.length && p[i] == ':') pos = i + 1
                        else throw IllegalStateException("unknown group")
                    }
                }
            }
            val inner = alternation()
            if (pos >= p.length || p[pos] != ')') throw IllegalStateException("unclosed group")
            pos++
            return if (negative) null else inner
        }

        private fun skipClass() {
            pos++ // [
            if (pos < p.length && p[pos] == '^') pos++
            if (pos < p.length && p[pos] == ']') pos++
            var depth = 1
            while (pos < p.length && depth > 0) {
                when (p[pos]) {
                    '\\' -> pos += 2
                    '[' -> { depth++; pos++ }
                    ']' -> { depth--; pos++ }
                    else -> pos++
                }
            }
            if (depth != 0) throw IllegalStateException("unclosed class")
        }

        private fun escape(): Escape {
            if (pos + 1 >= p.length) throw IllegalStateException("trailing backslash")
            val c = p[pos + 1]
            pos += 2
            return when (c) {
                'b', 'B', 'A', 'z', 'Z', 'G' -> Escape.ZeroWidth
                'd', 'D', 'w', 'W', 's', 'S', 'h', 'H', 'v', 'V', 'R', 'X', 'N' -> Escape.Class
                'p', 'P' -> {
                    if (pos < p.length && p[pos] == '{') {
                        val close = p.indexOf('}', pos)
                        pos = if (close < 0) p.length else close + 1
                    } else {
                        pos++
                    }
                    Escape.Class
                }
                't' -> Escape.Literal('\t')
                'n' -> Escape.Literal('\n')
                'r' -> Escape.Literal('\r')
                'f' -> Escape.Literal('\u000C')
                'x', 'u', 'c', '0', 'k', 'e', 'a' -> throw IllegalStateException("escape not handled")
                in '1'..'9' -> throw IllegalStateException("back-reference")
                else -> if (c.isLetterOrDigit()) throw IllegalStateException("unknown escape") else Escape.Literal(c)
            }
        }
    }
}
