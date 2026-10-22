package com.example.cramrescue.presentation.quiz

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizAttemptAnswer
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizResultViewModelTest {

    private class FakeQuizRepository(
        val quiz: Quiz,
        val questions: List<QuizQuestion>,
        val attempt: QuizAttempt
    ) : QuizRepository {
        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long = quiz.id
        override suspend fun updateQuiz(quiz: Quiz, questions: List<QuizQuestion>) {}
        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = emptyFlow()
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = listOf(quiz)
        override suspend fun getQuizById(quizId: Long): Quiz? = if (quiz.id == quizId) quiz else null
        override suspend fun getQuizDetail(quizId: Long): QuizDetail? =
            if (quiz.id == quizId) QuizDetail(quiz, questions, listOf(attempt)) else null
        override suspend fun saveAttempt(attempt: QuizAttempt): Long = attempt.id
        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? =
            if (attempt.id == attemptId) attempt else null
        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {}
        override suspend fun deleteQuiz(quizId: Long) {}
    }

    @Test
    fun loadsQuizAttemptAndCalculatesStatsCorrectly() = runBlocking {
        val quiz = Quiz(1L, 10L, "Dược lý 1", 2, Instant.now(), 10.0f)
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 2, "Exp 2", listOf("B2"))
        )
        val attempt = QuizAttempt(
            id = 50L,
            quizId = 1L,
            attemptNumber = 1,
            score = 5.0f,
            correctCount = 1,
            totalQuestions = 2,
            durationSeconds = 75L,
            completedAt = Instant.now(),
            answers = listOf(
                QuizAttemptAnswer(101L, 0), // Correct
                QuizAttemptAnswer(102L, 1)  // Incorrect (correct is 2)
            )
        )

        val repo = FakeQuizRepository(quiz, questions, attempt)

        val viewModel = QuizResultViewModel(
            quizId = 1L,
            attemptId = 50L,
            quizRepository = repo,
            dispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.quiz)
        assertNotNull(state.attempt)
        assertEquals(5.0f, state.stats.score, 0.01f)
        assertEquals(1, state.stats.correctCount)
        assertEquals(1, state.stats.incorrectCount)
        assertEquals(2, state.stats.totalQuestions)
        assertEquals(75L, state.stats.durationSeconds)

        assertEquals(2, state.reviewItems.size)
        val item1 = state.reviewItems.first()
        assertTrue(item1.isCorrect)
        assertEquals(0, item1.selectedOptionIndex)

        val item2 = state.reviewItems.last()
        assertFalse(item2.isCorrect)
        assertEquals(1, item2.selectedOptionIndex)
        assertEquals(2, item2.question.correctOptionIndex)
    }
}
