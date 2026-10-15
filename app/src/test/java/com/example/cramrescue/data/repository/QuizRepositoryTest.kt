package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.QuizDao
import com.example.cramrescue.data.local.entity.QuizAttemptEntity
import com.example.cramrescue.data.local.entity.QuizEntity
import com.example.cramrescue.data.local.entity.QuizQuestionEntity
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizAttemptAnswer
import com.example.cramrescue.domain.model.QuizQuestion
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuizRepositoryTest {

    private class FakeQuizDao : QuizDao {
        val quizzes = mutableListOf<QuizEntity>()
        val questions = mutableListOf<QuizQuestionEntity>()
        val attempts = mutableListOf<QuizAttemptEntity>()
        val quizzesFlow = MutableStateFlow<List<QuizEntity>>(emptyList())

        private var nextQuizId = 1L
        private var nextQuestionId = 1L
        private var nextAttemptId = 1L

        override suspend fun insertQuiz(quiz: QuizEntity): Long {
            val id = nextQuizId++
            val saved = quiz.copy(id = id)
            quizzes.add(saved)
            quizzesFlow.value = quizzes.toList()
            return id
        }

        override suspend fun insertQuestions(items: List<QuizQuestionEntity>) {
            items.forEach { q ->
                questions.add(q.copy(id = nextQuestionId++))
            }
        }

        override suspend fun insertAttempt(attempt: QuizAttemptEntity): Long {
            val id = nextAttemptId++
            val saved = attempt.copy(id = id)
            attempts.add(saved)
            return id
        }

        override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<QuizEntity>> {
            return quizzesFlow.map { list -> list.filter { it.campaignId == campaignId } }
        }

        override suspend fun getQuizzesByCampaign(campaignId: Long): List<QuizEntity> {
            return quizzes.filter { it.campaignId == campaignId }
        }

        override suspend fun getQuizById(quizId: Long): QuizEntity? {
            return quizzes.firstOrNull { it.id == quizId }
        }

        override suspend fun getQuestionsForQuiz(quizId: Long): List<QuizQuestionEntity> {
            return questions.filter { it.quizId == quizId }
        }

        override suspend fun getAttemptsForQuiz(quizId: Long): List<QuizAttemptEntity> {
            return attempts.filter { it.quizId == quizId }
        }

        override suspend fun getAttemptById(attemptId: Long): QuizAttemptEntity? {
            return attempts.firstOrNull { it.id == attemptId }
        }

        override suspend fun updateBestScore(quizId: Long, bestScore: Float) {
            val index = quizzes.indexOfFirst { it.id == quizId }
            if (index >= 0) {
                quizzes[index] = quizzes[index].copy(bestScore = bestScore)
                quizzesFlow.value = quizzes.toList()
            }
        }

        override suspend fun deleteQuiz(quizId: Long) {
            quizzes.removeAll { it.id == quizId }
            questions.removeAll { it.quizId == quizId }
            attempts.removeAll { it.quizId == quizId }
            quizzesFlow.value = quizzes.toList()
        }
    }

    @Test
    fun saveQuizAndRetrieveDetails() = runBlocking {
        val dao = FakeQuizDao()
        val repository = QuizRepositoryImpl(dao)

        val createdNow = Instant.ofEpochMilli(1_700_000_000_000L)
        val quiz = Quiz(
            campaignId = 42L,
            title = "Chiến dịch Dược lý - Bài 1",
            questionCount = 1,
            createdAt = createdNow,
            bestScore = null
        )

        val question = QuizQuestion(
            questionOrder = 1,
            stem = "Thuốc nào sau đây thuộc nhóm kháng sinh beta-lactam?",
            options = listOf("Amoxicillin", "Paracetamol", "Ibuprofen", "Diazepam"),
            correctOptionIndex = 0,
            explanation = "Amoxicillin là penicillin thuộc phân nhóm aminopenicillin.",
            sourceBlockIds = listOf("B1", "B2")
        )

        val quizId = repository.saveQuiz(quiz, listOf(question))
        assertEquals(1L, quizId)

        val retrieved = repository.getQuizDetail(quizId)
        assertNotNull(retrieved)
        assertEquals("Chiến dịch Dược lý - Bài 1", retrieved?.quiz?.title)
        assertEquals(1, retrieved?.questions?.size)

        val retrievedQuestion = retrieved?.questions?.first()
        assertEquals("Thuốc nào sau đây thuộc nhóm kháng sinh beta-lactam?", retrievedQuestion?.stem)
        assertEquals(4, retrievedQuestion?.options?.size)
        assertEquals("Amoxicillin", retrievedQuestion?.options?.get(0))
        assertEquals(0, retrievedQuestion?.correctOptionIndex)
        assertEquals(listOf("B1", "B2"), retrievedQuestion?.sourceBlockIds)
    }

    @Test
    fun recordAttemptAndUpdateBestScore() = runBlocking {
        val dao = FakeQuizDao()
        val repository = QuizRepositoryImpl(dao)

        val quiz = Quiz(
            campaignId = 1L,
            title = "Test Quiz",
            questionCount = 1,
            createdAt = Instant.now()
        )
        val quizId = repository.saveQuiz(quiz, listOf())

        val attempt = QuizAttempt(
            quizId = quizId,
            attemptNumber = 1,
            score = 8.5f,
            correctCount = 8,
            totalQuestions = 10,
            durationSeconds = 420L,
            completedAt = Instant.now(),
            answers = listOf(QuizAttemptAnswer(questionId = 101L, selectedOptionIndex = 2))
        )

        val attemptId = repository.saveAttempt(attempt)
        assertEquals(1L, attemptId)

        repository.updateBestScore(quizId, 8.5f)

        val updatedQuiz = repository.getQuizById(quizId)
        assertEquals(8.5f, updatedQuiz?.bestScore)

        val retrievedAttempt = repository.getAttemptById(attemptId)
        assertNotNull(retrievedAttempt)
        assertEquals(8.5f, retrievedAttempt?.score)
        assertEquals(1, retrievedAttempt?.answers?.size)
        assertEquals(101L, retrievedAttempt?.answers?.first()?.questionId)
        assertEquals(2, retrievedAttempt?.answers?.first()?.selectedOptionIndex)
    }
}
