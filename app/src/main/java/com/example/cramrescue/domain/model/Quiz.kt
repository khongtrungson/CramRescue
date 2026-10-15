package com.example.cramrescue.domain.model

import java.time.Instant

data class Quiz(
    val id: Long = 0L,
    val campaignId: Long,
    val title: String,
    val questionCount: Int,
    val createdAt: Instant,
    val bestScore: Float? = null
)

data class QuizQuestion(
    val id: Long = 0L,
    val quizId: Long = 0L,
    val questionOrder: Int,
    val stem: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val sourceBlockIds: List<String>
)

data class QuizAttemptAnswer(
    val questionId: Long,
    val selectedOptionIndex: Int?
)

data class QuizAttempt(
    val id: Long = 0L,
    val quizId: Long,
    val attemptNumber: Int,
    val score: Float,
    val correctCount: Int,
    val totalQuestions: Int,
    val durationSeconds: Long,
    val completedAt: Instant,
    val answers: List<QuizAttemptAnswer>
)

data class QuizDetail(
    val quiz: Quiz,
    val questions: List<QuizQuestion>,
    val attempts: List<QuizAttempt> = emptyList()
)
