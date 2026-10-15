package com.example.cramrescue.domain.quiz

import com.example.cramrescue.domain.model.QuizQuestion
import kotlin.math.roundToInt
import kotlin.random.Random

object QuizSessionEngine {

    const val SECONDS_PER_QUESTION = 60L
    const val WARNING_THRESHOLD_SECONDS = 60L

    fun examDurationSeconds(questionCount: Int): Long =
        questionCount * SECONDS_PER_QUESTION

    fun isWarningTime(remainingSeconds: Long): Boolean =
        remainingSeconds < WARNING_THRESHOLD_SECONDS

    fun calculateScore(correctCount: Int, totalQuestions: Int): Float {
        if (totalQuestions <= 0) return 0.0f
        val raw = (correctCount.toFloat() / totalQuestions.toFloat()) * 10f
        return (raw * 10).roundToInt() / 10f
    }

    /**
     * Shuffles the questions and their options for retaking a quiz.
     * The correctOptionIndex is recomputed so the correct answer identity is preserved.
     */
    fun shuffleForRetake(
        questions: List<QuizQuestion>,
        random: Random = Random
    ): List<QuizQuestion> {
        val shuffledQuestions = questions.shuffled(random)
        return shuffledQuestions.mapIndexed { index, question ->
            val correctOptionText = question.options[question.correctOptionIndex]
            val shuffledOptions = question.options.shuffled(random)
            val newCorrectIndex = shuffledOptions.indexOf(correctOptionText)

            question.copy(
                questionOrder = index + 1,
                options = shuffledOptions,
                correctOptionIndex = newCorrectIndex
            )
        }
    }

    fun formatDurationMMSS(seconds: Long): String {
        val m = (seconds.coerceAtLeast(0)) / 60
        val s = (seconds.coerceAtLeast(0)) % 60
        return "%02d:%02d".format(m, s)
    }
}
