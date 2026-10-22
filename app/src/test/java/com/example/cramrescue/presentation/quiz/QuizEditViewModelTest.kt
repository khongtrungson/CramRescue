package com.example.cramrescue.presentation.quiz

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import com.example.cramrescue.domain.usecase.quiz.UpdateQuizUseCase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEditViewModelTest {

    private class FakeQuizRepository : QuizRepository {
        var detail: QuizDetail? = null
        var lastSavedQuiz: Quiz? = null
        var lastSavedQuestions: List<QuizQuestion>? = null

        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long = 1L
        override suspend fun updateQuiz(quiz: Quiz, questions: List<QuizQuestion>) {
            lastSavedQuiz = quiz
            lastSavedQuestions = questions
            detail = QuizDetail(quiz, questions)
        }
        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = emptyFlow()
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = emptyList()
        override suspend fun getQuizById(quizId: Long): Quiz? = detail?.quiz
        override suspend fun getQuizDetail(quizId: Long): QuizDetail? = detail
        override suspend fun saveAttempt(attempt: QuizAttempt): Long = 1L
        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? = null
        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {}
        override suspend fun deleteQuiz(quizId: Long) {}
    }

    private fun createTestDetail(quizId: Long = 1L): QuizDetail {
        val quiz = Quiz(
            id = quizId,
            campaignId = 10L,
            title = "Chiến dịch 1: Đề số 1",
            questionCount = 2,
            createdAt = Instant.now()
        )
        val q1 = QuizQuestion(
            id = 101L,
            quizId = quizId,
            questionOrder = 1,
            stem = "Câu hỏi 1",
            options = listOf("A", "B", "C", "D"),
            correctOptionIndex = 0,
            explanation = "Giải thích 1",
            sourceBlockIds = listOf("B1")
        )
        val q2 = QuizQuestion(
            id = 102L,
            quizId = quizId,
            questionOrder = 2,
            stem = "Câu hỏi 2",
            options = listOf("1", "2", "3", "4"),
            correctOptionIndex = 1,
            explanation = "Giải thích 2",
            sourceBlockIds = listOf("B2")
        )
        return QuizDetail(quiz, listOf(q1, q2))
    }

    @Test
    fun loadsQuizDetailSuccessfully() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasUnsavedChanges)
        assertEquals("Chiến dịch 1: Đề số 1", state.title)
        assertEquals(2, state.questions.size)
        assertEquals("Câu hỏi 1", state.questions[0].stem)
        assertEquals("Câu hỏi 2", state.questions[1].stem)
    }

    @Test
    fun updateTitleMarksUnsavedChanges() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.updateTitle("Tiêu đề mới")
        val state = viewModel.uiState.value
        assertEquals("Tiêu đề mới", state.title)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun updateQuestionFieldsMarksUnsavedChanges() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.updateQuestionStem(0, "Stem mới")
        viewModel.updateOption(0, 1, "B mới")
        viewModel.selectCorrectOption(0, 2)
        viewModel.updateExplanation(0, "Giải thích mới")

        val state = viewModel.uiState.value
        assertTrue(state.hasUnsavedChanges)
        assertEquals("Stem mới", state.questions[0].stem)
        assertEquals("B mới", state.questions[0].options[1])
        assertEquals(2, state.questions[0].correctOptionIndex)
        assertEquals("Giải thích mới", state.questions[0].explanation)
    }

    @Test
    fun addQuestionAppendsNewItemAndMarksUnsaved() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.addQuestion()
        val state = viewModel.uiState.value
        assertEquals(3, state.questions.size)
        assertTrue(state.hasUnsavedChanges)
        assertEquals(0L, state.questions[2].id)
    }

    @Test
    fun deleteQuestionRemovesItemWhenMoreThanOne() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.deleteQuestion(0)
        val state = viewModel.uiState.value
        assertEquals(1, state.questions.size)
        assertEquals("Câu hỏi 2", state.questions[0].stem)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun deleteQuestionPreventsRemovingLastRemainingQuestion() = runBlocking {
        val repo = FakeQuizRepository()
        val singleQuestionQuiz = QuizDetail(
            Quiz(1L, 10L, "T", 1, Instant.now()),
            listOf(QuizQuestion(101L, 1L, 1, "Q", listOf("1", "2", "3", "4"), 0, "", emptyList()))
        )
        repo.detail = singleQuestionQuiz
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.deleteQuestion(0)
        val state = viewModel.uiState.value
        assertEquals(1, state.questions.size)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun saveQuizSuccessUpdatesStateAndCallsCallback() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.updateTitle("Đề thi sau chỉnh sửa")
        var successCalled = false
        viewModel.saveQuiz { successCalled = true }

        val state = viewModel.uiState.value
        assertTrue(successCalled)
        assertTrue(state.isSaved)
        assertFalse(state.hasUnsavedChanges)
        assertFalse(state.isSaving)
        assertEquals("Đề thi sau chỉnh sửa", repo.lastSavedQuiz?.title)
    }

    @Test
    fun saveQuizValidationErrorShowsErrorMessage() = runBlocking {
        val repo = FakeQuizRepository()
        repo.detail = createTestDetail(1L)
        val updateQuizUseCase = UpdateQuizUseCase(repo)

        val viewModel = QuizEditViewModel(
            quizId = 1L,
            quizRepository = repo,
            updateQuizUseCase = updateQuizUseCase,
            dispatcher = Dispatchers.Unconfined
        )

        viewModel.updateTitle("   ") // invalid blank title
        var successCalled = false
        viewModel.saveQuiz { successCalled = true }

        val state = viewModel.uiState.value
        assertFalse(successCalled)
        assertFalse(state.isSaved)
        assertNotNull(state.errorMessage)
        assertEquals("Tiêu đề đề thi không được để trống.", state.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
