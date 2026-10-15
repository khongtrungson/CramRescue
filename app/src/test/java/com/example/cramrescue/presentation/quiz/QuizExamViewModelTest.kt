package com.example.cramrescue.presentation.quiz

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import com.example.cramrescue.domain.usecase.quiz.SubmitQuizAttemptUseCase
import com.example.cramrescue.domain.usecase.quiz.SubmitQuizResult
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

class QuizExamViewModelTest {

    private class FakeQuizRepository(
        val quiz: Quiz,
        val questions: List<QuizQuestion>
    ) : QuizRepository {
        var lastAttemptSaved: QuizAttempt? = null

        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long = quiz.id
        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = emptyFlow()
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = listOf(quiz)
        override suspend fun getQuizById(quizId: Long): Quiz? = if (quiz.id == quizId) quiz else null
        override suspend fun getQuizDetail(quizId: Long): QuizDetail? =
            if (quiz.id == quizId) QuizDetail(quiz, questions) else null

        override suspend fun saveAttempt(attempt: QuizAttempt): Long {
            lastAttemptSaved = attempt
            return 999L
        }

        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? = lastAttemptSaved
        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {}
        override suspend fun deleteQuiz(quizId: Long) {}
    }

    @Test
    fun loadsExamAndInitializesTimer() = runBlocking {
        val quiz = Quiz(1L, 10L, "Dược lý 1", 2, Instant.now(), null)
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 1, "Exp 2", listOf("B2"))
        )
        val repo = FakeQuizRepository(quiz, questions)
        val submitUseCase = SubmitQuizAttemptUseCase(repo)

        val viewModel = QuizExamViewModel(
            quizId = 1L,
            quizRepository = repo,
            submitQuizAttempt = submitUseCase,
            dispatcher = Dispatchers.Unconfined,
            startTicker = false
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.questions.size)
        // 2 questions * 60s = 120s
        assertEquals(120L, state.remainingSeconds)
        assertFalse(state.isWarning)
    }

    @Test
    fun selectingAnswerUpdatesState() = runBlocking {
        val quiz = Quiz(1L, 10L, "Dược lý 1", 2, Instant.now(), null)
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 1, "Exp 2", listOf("B2"))
        )
        val repo = FakeQuizRepository(quiz, questions)
        val submitUseCase = SubmitQuizAttemptUseCase(repo)

        val viewModel = QuizExamViewModel(
            quizId = 1L,
            quizRepository = repo,
            submitQuizAttempt = submitUseCase,
            dispatcher = Dispatchers.Unconfined,
            startTicker = false
        )

        viewModel.selectOption(101L, 2)
        assertEquals(2, viewModel.uiState.value.selectedAnswers[101L])

        // Can change selection
        viewModel.selectOption(101L, 0)
        assertEquals(0, viewModel.uiState.value.selectedAnswers[101L])
    }

    @Test
    fun navigationBetweenQuestions() = runBlocking {
        val quiz = Quiz(1L, 10L, "Dược lý 1", 3, Instant.now(), null)
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 1, "Exp 2", listOf("B2")),
            QuizQuestion(103L, 1L, 3, "Q3", listOf("A", "B", "C", "D"), 2, "Exp 3", listOf("B3"))
        )
        val repo = FakeQuizRepository(quiz, questions)
        val submitUseCase = SubmitQuizAttemptUseCase(repo)

        val viewModel = QuizExamViewModel(
            quizId = 1L,
            quizRepository = repo,
            submitQuizAttempt = submitUseCase,
            dispatcher = Dispatchers.Unconfined,
            startTicker = false
        )

        assertEquals(0, viewModel.uiState.value.currentQuestionIndex)
        viewModel.nextQuestion()
        assertEquals(1, viewModel.uiState.value.currentQuestionIndex)
        viewModel.jumpToQuestion(2)
        assertEquals(2, viewModel.uiState.value.currentQuestionIndex)
        viewModel.previousQuestion()
        assertEquals(1, viewModel.uiState.value.currentQuestionIndex)
    }

    @Test
    fun manualSubmitNavigatesWithAttemptId() = runBlocking {
        val quiz = Quiz(1L, 10L, "Dược lý 1", 2, Instant.now(), null)
        val questions = listOf(
            QuizQuestion(101L, 1L, 1, "Q1", listOf("A", "B", "C", "D"), 0, "Exp 1", listOf("B1")),
            QuizQuestion(102L, 1L, 2, "Q2", listOf("A", "B", "C", "D"), 1, "Exp 2", listOf("B2"))
        )
        val repo = FakeQuizRepository(quiz, questions)
        val submitUseCase = SubmitQuizAttemptUseCase(repo)

        val viewModel = QuizExamViewModel(
            quizId = 1L,
            quizRepository = repo,
            submitQuizAttempt = submitUseCase,
            dispatcher = Dispatchers.Unconfined,
            startTicker = false
        )

        viewModel.selectOption(101L, 0)
        viewModel.selectOption(102L, 1)

        var submittedAttemptId: Long? = null
        viewModel.submitExam { attemptId ->
            submittedAttemptId = attemptId
        }

        assertEquals(999L, submittedAttemptId)
        assertTrue(viewModel.uiState.value.isSubmitted)
        assertNotNull(repo.lastAttemptSaved)
        assertEquals(2, repo.lastAttemptSaved?.correctCount)
        assertEquals(10.0f, repo.lastAttemptSaved?.score)
    }
}
