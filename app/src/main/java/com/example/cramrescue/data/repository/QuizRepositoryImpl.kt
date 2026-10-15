package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.QuizDao
import com.example.cramrescue.data.mapper.QuizMapper
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuizRepositoryImpl(
    private val quizDao: QuizDao
) : QuizRepository {

    override suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long {
        val quizEntity = QuizMapper.toEntity(quiz)
        val quizId = quizDao.insertQuiz(quizEntity)
        val questionEntities = questions.map { QuizMapper.toEntity(it, quizId) }
        quizDao.insertQuestions(questionEntities)
        return quizId
    }

    override fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>> {
        return quizDao.observeQuizzesByCampaign(campaignId).map { list ->
            list.map { QuizMapper.toDomain(it) }
        }
    }

    override suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz> {
        return quizDao.getQuizzesByCampaign(campaignId).map { QuizMapper.toDomain(it) }
    }

    override suspend fun getQuizById(quizId: Long): Quiz? {
        return quizDao.getQuizById(quizId)?.let { QuizMapper.toDomain(it) }
    }

    override suspend fun getQuizDetail(quizId: Long): QuizDetail? {
        val quizEntity = quizDao.getQuizById(quizId) ?: return null
        val questionEntities = quizDao.getQuestionsForQuiz(quizId)
        val attemptEntities = quizDao.getAttemptsForQuiz(quizId)

        return QuizDetail(
            quiz = QuizMapper.toDomain(quizEntity),
            questions = questionEntities.map { QuizMapper.toDomain(it) },
            attempts = attemptEntities.map { QuizMapper.toDomain(it) }
        )
    }

    override suspend fun saveAttempt(attempt: QuizAttempt): Long {
        val attemptEntity = QuizMapper.toEntity(attempt)
        return quizDao.insertAttempt(attemptEntity)
    }

    override suspend fun getAttemptById(attemptId: Long): QuizAttempt? {
        return quizDao.getAttemptById(attemptId)?.let { QuizMapper.toDomain(it) }
    }

    override suspend fun updateBestScore(quizId: Long, bestScore: Float) {
        quizDao.updateBestScore(quizId, bestScore)
    }

    override suspend fun deleteQuiz(quizId: Long) {
        quizDao.deleteQuiz(quizId)
    }
}
