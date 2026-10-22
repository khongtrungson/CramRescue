package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.cramrescue.data.local.entity.QuizAttemptEntity
import com.example.cramrescue.data.local.entity.QuizEntity
import com.example.cramrescue.data.local.entity.QuizQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity): Long

    @Update
    suspend fun updateQuiz(quiz: QuizEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(items: List<QuizQuestionEntity>)

    @Query("DELETE FROM quiz_questions WHERE quizId = :quizId")
    suspend fun deleteQuestionsByQuizId(quizId: Long)

    @Query("DELETE FROM quiz_questions WHERE quizId = :quizId AND id NOT IN (:keepIds)")
    suspend fun deleteQuestionsNotIn(quizId: Long, keepIds: List<Long>)

    @Transaction
    suspend fun updateQuizWithQuestions(
        quiz: QuizEntity,
        questions: List<QuizQuestionEntity>,
        retainedQuestionIds: List<Long>
    ) {
        updateQuiz(quiz)
        if (retainedQuestionIds.isNotEmpty()) {
            deleteQuestionsNotIn(quiz.id, retainedQuestionIds)
        } else {
            deleteQuestionsByQuizId(quiz.id)
        }
        insertQuestions(questions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Query("SELECT * FROM quizzes WHERE campaignId = :campaignId ORDER BY createdAtEpochMillis DESC")
    fun observeQuizzesByCampaign(campaignId: Long): Flow<List<QuizEntity>>

    @Query("SELECT * FROM quizzes WHERE campaignId = :campaignId ORDER BY createdAtEpochMillis DESC")
    suspend fun getQuizzesByCampaign(campaignId: Long): List<QuizEntity>

    @Query("SELECT * FROM quizzes WHERE id = :quizId")
    suspend fun getQuizById(quizId: Long): QuizEntity?

    @Query("SELECT * FROM quiz_questions WHERE quizId = :quizId ORDER BY questionOrder ASC")
    suspend fun getQuestionsForQuiz(quizId: Long): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_attempts WHERE quizId = :quizId ORDER BY attemptNumber DESC")
    suspend fun getAttemptsForQuiz(quizId: Long): List<QuizAttemptEntity>

    @Query("SELECT * FROM quiz_attempts WHERE id = :attemptId")
    suspend fun getAttemptById(attemptId: Long): QuizAttemptEntity?

    @Query("UPDATE quizzes SET bestScore = :bestScore WHERE id = :quizId")
    suspend fun updateBestScore(quizId: Long, bestScore: Float)

    @Query("DELETE FROM quizzes WHERE id = :quizId")
    suspend fun deleteQuiz(quizId: Long)
}
