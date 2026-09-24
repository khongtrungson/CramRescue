package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.MAX_BULLETS
import com.example.cramrescue.domain.model.MAX_WORDS_PER_BULLET

/**
 * Client-side enforcement of the 3-Bullet Budget (FR-6, Constraint 6.2). The prompt and schema ask
 * for it too, but the app never relies on the model obeying:
 *  - a 4th+ bullet is removed and moved to notes ("Ghi chú mở rộng").
 *  - a bullet longer than 20 words is cut to 20 words + "…"; its full text goes to notes.
 */
object ThreeBulletBudget {

    data class Result(val bullets: List<String>, val notes: String?)

    private val WHITESPACE = Regex("\\s+")

    fun enforce(rawBullets: List<String>): Result {
        val cleaned = rawBullets.map { it.normalizeWhitespace() }.filter { it.isNotEmpty() }
        val notes = mutableListOf<String>()

        val kept = cleaned.take(MAX_BULLETS).map { bullet ->
            val words = bullet.split(' ')
            if (words.size <= MAX_WORDS_PER_BULLET) {
                bullet
            } else {
                notes += bullet
                words.take(MAX_WORDS_PER_BULLET).joinToString(" ") + "…"
            }
        }
        notes += cleaned.drop(MAX_BULLETS)

        return Result(bullets = kept, notes = notes.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }

    fun wordCount(text: String): Int = text.normalizeWhitespace().split(' ').count { it.isNotEmpty() }

    private fun String.normalizeWhitespace(): String = trim().replace(WHITESPACE, " ")
}
