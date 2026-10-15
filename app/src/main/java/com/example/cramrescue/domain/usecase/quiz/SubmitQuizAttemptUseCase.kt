package com.example.cramrescue.domain.usecase.quiz

import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizAttemptAnswer
import com.example.cramrescue.domain.quiz.QuizSessionEngine
import com.example.cramrescue.domain.repository.QuizRepository
import java.time.Instant

sealed interface SubmitQuizResult {
    data class Success(
        val attemptId: Long,
        val score: Float,
        val correctCount: Int,
        val totalQuestions: Int,
        val durationSeconds: Long
    ) : SubmitQuizResult

    data object QuizNotFound : SubmitQuizResult
}

class SubmitQuizAttemptUseCase(
    private val quizRepository: QuizRepository
) {
    suspend operator fun invoke(
        quizId: Long,
        userAnswers: Map<Long, Int?>,
        durationSeconds: Long
    ): SubmitQuizResult {
        val detail = quizRepository.getQuizDetail(quizId) ?: return SubmitQuizResult.QuizNotFound
        val quiz = detail.quiz
        val questions = detail.questions
        val attempts = detail.attempts

        var correctCount = 0
        val answersList = questions.map { question ->
            val selected = userAnswers[question.id]
            if (selected != null && selected == question.correctOptionIndex) {
                correctCount++
            }
            QuizAttemptAnswer(
                questionId = question.id,
                selectedOptionIndex = selected
            )
        }

        val totalQuestions = questions.size
        val score = QuizSessionEngine.calculateScore(correctCount, totalQuestions)
        val attemptNumber = attempts.size + 1

        val attempt = QuizAttempt(
            quizId = quizId,
            attemptNumber = attemptNumber,
            score = score,
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            durationSeconds = durationSeconds,
            completedAt = Instant.now(),
            answers = answersList
        )

        val attemptId = quizRepository.saveAttempt(attempt)

        val currentBest = quiz.bestScore
        if (currentBest == null || score > currentBest) {
            quizRepository.updateBestScore(quizId, score)
        }

        return SubmitQuizResult.Success(
            attemptId = attemptId,
            score = score,
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            durationSeconds = durationSeconds
        )
    }
}
