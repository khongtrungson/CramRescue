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

class UpdateQuizUseCaseTest {

    private class FakeQuizRepository : QuizRepository {
        var lastUpdatedQuiz: Quiz? = null
        var lastUpdatedQuestions: List<QuizQuestion>? = null

        override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long = 1L
        override suspend fun updateQuiz(quiz: Quiz, questions: List<QuizQuestion>) {
            lastUpdatedQuiz = quiz
            lastUpdatedQuestions = questions
        }
        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> = emptyFlow()
        override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> = emptyList()
        override suspend fun getQuizById(quizId: Long): Quiz? = null
        override suspend fun getQuizDetail(quizId: Long): QuizDetail? = null
        override suspend fun saveAttempt(attempt: QuizAttempt): Long = 1L
        override suspend fun getAttemptById(attemptId: Long): QuizAttempt? = null
        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {}
        override suspend fun deleteQuiz(quizId: Long) {}
    }

    private val baseQuiz = Quiz(
        id = 1L,
        campaignId = 10L,
        title = "Valid Title",
        questionCount = 1,
        createdAt = Instant.now()
    )

    private val validQuestion = QuizQuestion(
        id = 100L,
        quizId = 1L,
        questionOrder = 1,
        stem = "What is Kotlin?",
        options = listOf("Language", "Animal", "Food", "Car"),
        correctOptionIndex = 0,
        explanation = "Kotlin is a programming language.",
        sourceBlockIds = listOf("B1")
    )

    @Test
    fun rejectsBlankTitle() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val result = useCase(baseQuiz.copy(title = "   "), listOf(validQuestion))
        assertTrue(result is UpdateQuizResult.ValidationError)
        assertEquals("Tiêu đề đề thi không được để trống.", (result as UpdateQuizResult.ValidationError).message)
    }

    @Test
    fun rejectsEmptyQuestions() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val result = useCase(baseQuiz, emptyList())
        assertTrue(result is UpdateQuizResult.ValidationError)
        assertEquals("Đề thi phải có ít nhất 1 câu hỏi.", (result as UpdateQuizResult.ValidationError).message)
    }

    @Test
    fun rejectsBlankStem() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val result = useCase(baseQuiz, listOf(validQuestion.copy(stem = "   ")))
        assertTrue(result is UpdateQuizResult.ValidationError)
        assertEquals("Nội dung câu hỏi 1 không được để trống.", (result as UpdateQuizResult.ValidationError).message)
    }

    @Test
    fun rejectsInvalidOptionCount() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val result = useCase(baseQuiz, listOf(validQuestion.copy(options = listOf("A", "B", "C"))))
        assertTrue(result is UpdateQuizResult.ValidationError)
        assertEquals("Câu hỏi 1 phải có đúng 4 phương án lựa chọn.", (result as UpdateQuizResult.ValidationError).message)
    }

    @Test
    fun rejectsBlankOption() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val result = useCase(baseQuiz, listOf(validQuestion.copy(options = listOf("A", "   ", "C", "D"))))
        assertTrue(result is UpdateQuizResult.ValidationError)
        assertEquals("Phương án B ở câu 1 không được để trống.", (result as UpdateQuizResult.ValidationError).message)
    }

    @Test
    fun rejectsInvalidCorrectOptionIndex() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val result = useCase(baseQuiz, listOf(validQuestion.copy(correctOptionIndex = 4)))
        assertTrue(result is UpdateQuizResult.ValidationError)
        assertEquals("Vui lòng chọn một đáp án đúng (A, B, C hoặc D) cho câu 1.", (result as UpdateQuizResult.ValidationError).message)
    }

    @Test
    fun successfulUpdateSanitizesAndUpdatesRepository() = runBlocking {
        val repo = FakeQuizRepository()
        val useCase = UpdateQuizUseCase(repo)

        val q1 = validQuestion.copy(
            stem = "  Trimmed stem  ",
            options = listOf(" A ", " B ", " C ", " D "),
            explanation = "  Trimmed explanation  "
        )
        val result = useCase(baseQuiz.copy(title = "  Trimmed Title  "), listOf(q1))

        assertTrue(result is UpdateQuizResult.Success)
        assertEquals("Trimmed Title", repo.lastUpdatedQuiz?.title)
        assertEquals(1, repo.lastUpdatedQuiz?.questionCount)
        assertEquals("Trimmed stem", repo.lastUpdatedQuestions?.first()?.stem)
        assertEquals(listOf("A", "B", "C", "D"), repo.lastUpdatedQuestions?.first()?.options)
        assertEquals("Trimmed explanation", repo.lastUpdatedQuestions?.first()?.explanation)
    }
}
