package com.example.cramrescue.domain.quiz

import com.example.cramrescue.domain.model.QuizQuestion
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionTest {

    @Test
    fun calculateScorePrecision() {
        assertEquals(10.0f, QuizSessionEngine.calculateScore(10, 10), 0.01f)
        assertEquals(0.0f, QuizSessionEngine.calculateScore(0, 10), 0.01f)
        assertEquals(8.0f, QuizSessionEngine.calculateScore(8, 10), 0.01f)
        assertEquals(6.7f, QuizSessionEngine.calculateScore(2, 3), 0.05f)
        assertEquals(0.0f, QuizSessionEngine.calculateScore(0, 0), 0.01f)
    }

    @Test
    fun examDurationSecondsFormula() {
        assertEquals(300L, QuizSessionEngine.examDurationSeconds(5))
        assertEquals(600L, QuizSessionEngine.examDurationSeconds(10))
        assertEquals(900L, QuizSessionEngine.examDurationSeconds(15))
        assertEquals(1200L, QuizSessionEngine.examDurationSeconds(20))
    }

    @Test
    fun isWarningThreshold() {
        assertTrue(QuizSessionEngine.isWarningTime(59))
        assertTrue(QuizSessionEngine.isWarningTime(10))
        assertTrue(QuizSessionEngine.isWarningTime(0))
        assertEquals(false, QuizSessionEngine.isWarningTime(60))
        assertEquals(false, QuizSessionEngine.isWarningTime(120))
    }

    @Test
    fun retakeShufflingPreservesCorrectAnswerIdentity() {
        val questions = listOf(
            QuizQuestion(
                id = 1L,
                quizId = 10L,
                questionOrder = 1,
                stem = "Thuốc A?",
                options = listOf("Đúng", "Sai 1", "Sai 2", "Sai 3"),
                correctOptionIndex = 0,
                explanation = "A đúng",
                sourceBlockIds = listOf("B1")
            ),
            QuizQuestion(
                id = 2L,
                quizId = 10L,
                questionOrder = 2,
                stem = "Thuốc B?",
                options = listOf("Sai 1", "Đúng", "Sai 2", "Sai 3"),
                correctOptionIndex = 1,
                explanation = "B đúng",
                sourceBlockIds = listOf("B2")
            )
        )

        val deterministicRandom = Random(42)
        val shuffled = QuizSessionEngine.shuffleForRetake(questions, deterministicRandom)

        assertEquals(2, shuffled.size)
        shuffled.forEach { q ->
            assertEquals(4, q.options.size)
            assertTrue(q.correctOptionIndex in 0..3)
            val correctText = q.options[q.correctOptionIndex]
            assertEquals("Đúng", correctText)
        }
    }
}
