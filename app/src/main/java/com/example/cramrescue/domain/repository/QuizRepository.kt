package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizDetail
import com.example.cramrescue.domain.model.QuizQuestion
import kotlinx.coroutines.flow.Flow

interface QuizRepository {
    suspend fun saveQuiz(quiz: Quiz, questions: List<QuizQuestion>): Long
    fun observeQuizzesByCampaign(campaignId: Long): Flow<List<Quiz>>
    suspend fun getQuizzesByCampaign(campaignId: Long): List<Quiz>
    suspend fun getQuizById(quizId: Long): Quiz?
    suspend fun getQuizDetail(quizId: Long): QuizDetail?
    suspend fun saveAttempt(attempt: QuizAttempt): Long
    suspend fun getAttemptById(attemptId: Long): QuizAttempt?
    suspend fun updateBestScore(quizId: Long, bestScore: Float)
    suspend fun deleteQuiz(quizId: Long)
}
