package com.example.cramrescue.presentation.quiz

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import com.example.cramrescue.domain.usecase.quiz.GenerateQuizResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizListViewModelTest {

    private class FakeQuizRepository : QuizRepository {
        val quizzes = mutableListOf<Quiz>()
        val quizzesFlow = MutableStateFlow<List<Quiz>>(emptyList())

        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long = 1L
        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = quizzesFlow
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = quizzes
        override suspend fun getQuizById(quizId: Long): Quiz? = quizzes.firstOrNull { it.id == quizId }
        override suspend fun getQuizDetail(quizId: Long): QuizDetail? = null
        override suspend fun saveAttempt(attempt: QuizAttempt): Long = 1L
        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? = null
        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {}
        override suspend fun deleteQuiz(quizId: Long) {
            quizzes.removeAll { it.id == quizId }
            quizzesFlow.value = quizzes.toList()
        }
    }

    @Test
    fun loadsQuizzesForCampaign() = runBlocking {
        val repo = FakeQuizRepository()
        val quiz1 = Quiz(1L, 10L, "Bài 1", 10, Instant.now(), 8.0f)
        repo.quizzes.add(quiz1)
        repo.quizzesFlow.value = listOf(quiz1)

        val viewModel = QuizListViewModel(
            campaignId = 10L,
            quizRepository = repo,
            generateQuiz = { _, _ -> GenerateQuizResult.Success(1L) },
            dispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.quizzes.size)
        assertEquals("Bài 1", state.quizzes.first().title)
    }

    @Test
    fun deleteQuizRemovesFromRepository() = runBlocking {
        val repo = FakeQuizRepository()
        val quiz1 = Quiz(1L, 10L, "Bài 1", 10, Instant.now(), 8.0f)
        repo.quizzes.add(quiz1)
        repo.quizzesFlow.value = listOf(quiz1)

        val viewModel = QuizListViewModel(
            campaignId = 10L,
            quizRepository = repo,
            generateQuiz = { _, _ -> GenerateQuizResult.Success(1L) },
            dispatcher = Dispatchers.Unconfined
        )

        assertEquals(1, viewModel.uiState.value.quizzes.size)

        viewModel.deleteQuiz(1L)

        assertTrue(viewModel.uiState.value.quizzes.isEmpty())
    }

    @Test
    fun generateQuizCallsUseCaseAndNavigatesOnSuccess() = runBlocking {
        val repo = FakeQuizRepository()
        var generatedId: Long? = null

        val viewModel = QuizListViewModel(
            campaignId = 10L,
            quizRepository = repo,
            generateQuiz = { _, count ->
                assertEquals(15, count)
                GenerateQuizResult.Success(42L)
            },
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.generateNewQuiz(questionCount = 15) { quizId ->
            generatedId = quizId
        }

        assertEquals(42L, generatedId)
        assertFalse(viewModel.uiState.value.isGenerating)
    }
}
