package com.example.cramrescue.domain.usecase.quiz

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitQuizAttemptUseCaseTest {

    private class FakeQuizRepo(
        var quiz: Quiz,
        val questions: List<QuizQuestion>,
        val attempts: MutableList<QuizAttempt> = mutableListOf()
    ) : QuizRepository {
        var lastUpdatedBestScore: Float? = null

        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long = quiz.id
        override suspend fun updateQuiz(quiz: Quiz, questions: List<QuizQuestion>) {}
        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = emptyFlow()
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = listOf(quiz)
        override suspend fun getQuizById(quizId: Long): Quiz? = if (quiz.id == quizId) quiz else null

        override suspend fun getQuizDetail(quizId: Long): QuizDetail? =
            if (quiz.id == quizId) QuizDetail(quiz, questions, attempts) else null

        override suspend fun saveAttempt(attempt: QuizAttempt): Long {
            val id = attempts.size + 1L
            attempts.add(attempt.copy(id = id))
            return id
        }

        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? =
            attempts.firstOrNull { it.id == attemptId }

        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {
            quiz = quiz.copy(bestScore = bestScore)
            lastUpdatedBestScore = bestScore
        }

        override suspend fun deleteQuiz(quizId: Long) {}
    }

    @Test
    fun recordsAttemptAndUpdatesBestScore() = runBlocking {
        val quiz = Quiz(
            id = 1L,
            campaignId = 10L,
            title = "Test Quiz",
            questionCount = 2,
            createdAt = Instant.now(),
            bestScore = 5.0f
        )
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 2, "Exp 2", listOf("B2"))
        )

        val repo = FakeQuizRepo(quiz, questions)
        val useCase = SubmitQuizAttemptUseCase(repo)

        // User answered Q1 correctly (0) and Q2 correctly (2) -> 10.0 score
        val answers = mapOf(101L to 0, 102L to 2)
        val result = useCase(quizId = 1L, userAnswers = answers, durationSeconds = 90L)

        assertTrue(result is SubmitQuizResult.Success)
        val success = result as SubmitQuizResult.Success
        assertEquals(10.0f, success.score, 0.01f)
        assertEquals(2, success.correctCount)
        assertEquals(2, success.totalQuestions)
        assertEquals(10.0f, repo.lastUpdatedBestScore)
        assertEquals(1, repo.attempts.size)
        assertEquals(1, repo.attempts.first().attemptNumber)
    }

    @Test
    fun doesNotLowerBestScore() = runBlocking {
        val quiz = Quiz(
            id = 1L,
            campaignId = 10L,
            title = "Test Quiz",
            questionCount = 2,
            createdAt = Instant.now(),
            bestScore = 9.0f
        )
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 2, "Exp 2", listOf("B2"))
        )

        val repo = FakeQuizRepo(quiz, questions)
        val useCase = SubmitQuizAttemptUseCase(repo)

        // User answered Q1 correctly (0) and Q2 incorrectly (1) -> 5.0 score
        val answers = mapOf(101L to 0, 102L to 1)
        val result = useCase(quizId = 1L, userAnswers = answers, durationSeconds = 50L)

        assertTrue(result is SubmitQuizResult.Success)
        val success = result as SubmitQuizResult.Success
        assertEquals(5.0f, success.score, 0.01f)
        // Best score should remain 9.0f (not downgraded)
        assertEquals(null, repo.lastUpdatedBestScore)
        assertEquals(9.0f, repo.quiz.bestScore)
    }
}
