package com.example.cramrescue.domain.recall

import com.example.cramrescue.domain.model.MicroCard

/**
 * Local, deterministic Cloze selection (FR-11: hide 1–2 core keywords). No AI, no NLP library,
 * no randomness: the same card always gives the same prompt.
 *
 * Bullets are tried in order (bullets cut short by the 3-Bullet Budget, ending in "…", are
 * skipped). The first bullet where one rule finds a target wins; rules in priority order:
 *  1. complexity notation `O(...)`, or a formula — a run of math-like tokens containing `=`;
 *  2. technical identifiers (`@Annotation`, `List<String>`, camelCase, `call()`), at most 2,
 *     skipping one that starts the bullet (usually the subject) when there are others;
 *  3. a standalone number that isn't a list marker;
 *  4. a definition tail: the 1–6 words after the last relation marker (" là ", " thành ", ...),
 *     without commas, leaving at least 3 words of context visible.
 * A target that appears in the card title is never hidden (the title is on screen). If no rule
 * applies, [RecallPrompt.General] is used instead of a nonsense Cloze.
 */
object RecallPromptBuilder {

    private const val MAX_KEYWORDS = 2
    private const val MAX_TAIL_WORDS = 6
    private const val MIN_CONTEXT_WORDS = 3

    private val TOKEN = Regex("\\S+")
    private val BIG_O = Regex("(?<![\\p{L}\\p{N}])[OΘΩ]\\([^()]{1,24}\\)")
    private val NUMBER = Regex("\\d+([.,]\\d+)?%?")
    private val SHORT_ASCII_VARIABLE = Regex("[A-Za-z]{1,2}")
    private val CAMEL_CASE = Regex("\\p{Ll}\\p{Lu}")
    private val CALL = Regex("[A-Za-z_][\\w.]*\\(\\)")
    private val ANNOTATION = Regex("@[A-Za-z_][\\w.]*")
    private const val MATH_OPERATORS = "=+-−*/^²³√±<>≤≥()"
    private const val EDGE_PUNCTUATION = ".,;:!?\"'“”‘’…"
    private val RELATION_MARKERS = listOf(
        " là ", " thành ", " gồm ", " yêu cầu ", " với ", " bằng ", " nhằm ", ": ",
        " is ", " are ", " requires ", " means "
    )

    private data class Token(val text: String, val start: Int) {
        val end: Int get() = start + text.length // exclusive
    }

    fun build(card: MicroCard): RecallPrompt {
        val title = card.title.lowercase()
        card.bullets.forEachIndexed { index, bullet ->
            if (bullet.isBlank() || bullet.trimEnd().endsWith("…")) return@forEachIndexed
            val targets = findTargets(bullet)
                .map { it.trim() }
                .filter { it.any(Char::isLetterOrDigit) && !title.contains(it.lowercase()) }
                .distinct()
                .take(MAX_KEYWORDS)
            if (targets.isEmpty()) return@forEachIndexed
            val ranges = occurrences(bullet, targets)
            if (ranges.isNotEmpty() && hasEnoughContext(bullet, ranges)) {
                return RecallPrompt.Cloze(index, ranges)
            }
        }
        return RecallPrompt.General
    }

    private fun findTargets(bullet: String): List<String> {
        val tokens = TOKEN.findAll(bullet).map { Token(it.value, it.range.first) }.toList()
        return formulaTargets(bullet, tokens).ifEmpty {
            technicalTargets(tokens).ifEmpty {
                numberTargets(tokens).ifEmpty { definitionTail(bullet) }
            }
        }
    }

    private fun formulaTargets(bullet: String, tokens: List<Token>): List<String> {
        BIG_O.find(bullet)?.let { return listOf(it.value) }

        var runStart = -1
        for (i in 0..tokens.size) {
            val isMath = i < tokens.size && isMathLike(tokens[i].text.trimEnd(*EDGE_PUNCTUATION.toCharArray()))
            if (isMath && runStart == -1) runStart = i
            if (!isMath && runStart != -1) {
                val run = tokens.subList(runStart, i)
                if (run.size >= 3 && run.any { '=' in it.text || '≤' in it.text || '≥' in it.text }) {
                    return listOf(bullet.substring(run.first().start, run.last().end).trimEnd(*EDGE_PUNCTUATION.toCharArray()))
                }
                runStart = -1
            }
        }
        return emptyList()
    }

    private fun isMathLike(token: String): Boolean {
        if (token.isEmpty()) return false
        val allowed = token.all { it.isLetterOrDigit() || it in MATH_OPERATORS || it in "Δπ.," }
        val mathSignal = token.any { it in MATH_OPERATORS || it.isDigit() } || SHORT_ASCII_VARIABLE.matches(token)
        return allowed && mathSignal
    }

    private fun technicalTargets(tokens: List<Token>): List<String> {
        val technical = tokens.withIndex()
            .map { (i, token) -> i to stripUnbalancedParentheses(token.text.trim(*EDGE_PUNCTUATION.toCharArray())) }
            .filter { (_, text) -> isTechnical(text) }
        val withoutSubject = if (technical.size > 1) technical.filter { it.first != 0 } else technical
        return withoutSubject.map { it.second }
    }

    /** "(Test" → "Test", "sort())" → "sort()", but "sort()" stays intact. */
    private fun stripUnbalancedParentheses(text: String): String {
        var result = text
        if (result.startsWith("(") && result.count { it == '(' } > result.count { it == ')' }) result = result.drop(1)
        if (result.endsWith(")") && result.count { it == ')' } > result.count { it == '(' }) result = result.dropLast(1)
        return result.trim(*EDGE_PUNCTUATION.toCharArray())
    }

    private fun isTechnical(text: String): Boolean =
        ANNOTATION.matches(text) ||
            ('<' in text && '>' in text && text.indexOf('<') < text.lastIndexOf('>')) ||
            CALL.matches(text) ||
            (CAMEL_CASE.containsMatchIn(text) && text.all { it.isLetterOrDigit() || it in "_.<>" })

    private fun numberTargets(tokens: List<Token>): List<String> =
        tokens.drop(1) // a leading number is a list marker ("1. Khảo sát"), not knowledge
            .map { it.text.trim(*EDGE_PUNCTUATION.toCharArray()) }
            .firstOrNull { NUMBER.matches(it) }
            ?.let(::listOf)
            .orEmpty()

    private fun definitionTail(bullet: String): List<String> {
        val (markerIndex, marker) = RELATION_MARKERS
            .map { bullet.lastIndexOf(it) to it }
            .filter { it.first >= 0 }
            .maxByOrNull { it.first } ?: return emptyList()
        val contextEnd = markerIndex + marker.length
        val tail = bullet.substring(contextEnd).trim().trimEnd(*EDGE_PUNCTUATION.toCharArray()).trim()
        val words = TOKEN.findAll(tail).count()
        val contextWords = TOKEN.findAll(bullet.substring(0, contextEnd)).count()
        val acceptable = words in 1..MAX_TAIL_WORDS &&
            ',' !in tail && ';' !in tail &&
            tail.length >= 3 &&
            contextWords >= MIN_CONTEXT_WORDS
        return if (acceptable) listOf(tail) else emptyList()
    }

    /** Every occurrence of every target, sorted, overlaps dropped — so the answer can't leak. */
    private fun occurrences(bullet: String, targets: List<String>): List<IntRange> {
        val ranges = targets.flatMap { target ->
            generateSequence(bullet.indexOf(target)) { previous ->
                bullet.indexOf(target, previous + target.length).takeIf { it >= 0 }
            }.takeWhile { it >= 0 }.map { it until it + target.length }.toList()
        }.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        ranges.forEach { range -> if (merged.isEmpty() || range.first > merged.last().last) merged += range }
        return merged
    }

    private fun hasEnoughContext(bullet: String, ranges: List<IntRange>): Boolean {
        val visible = StringBuilder(bullet)
        ranges.sortedByDescending { it.first }.forEach { visible.replace(it.first, it.last + 1, " ") }
        return TOKEN.findAll(visible).count { token -> token.value.any(Char::isLetter) } >= 2
    }
}
