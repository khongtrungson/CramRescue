package com.example.cramrescue.domain.usecase.quiz

import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizQuestion
import com.example.cramrescue.domain.repository.QuizRepository

sealed interface UpdateQuizResult {
    data object Success : UpdateQuizResult
    data class ValidationError(val message: String) : UpdateQuizResult
}

class UpdateQuizUseCase(
    private val quizRepository: QuizRepository
) {
    suspend operator fun invoke(
        quiz: Quiz,
        questions: List<QuizQuestion>
    ): UpdateQuizResult {
        val trimmedTitle = quiz.title.trim()
        if (trimmedTitle.isEmpty()) {
            return UpdateQuizResult.ValidationError("Tiêu đề đề thi không được để trống.")
        }

        if (questions.isEmpty()) {
            return UpdateQuizResult.ValidationError("Đề thi phải có ít nhất 1 câu hỏi.")
        }

        val cleanedQuestions = mutableListOf<QuizQuestion>()
        for ((idx, q) in questions.withIndex()) {
            val qNum = idx + 1
            val trimmedStem = q.stem.trim()
            if (trimmedStem.isEmpty()) {
                return UpdateQuizResult.ValidationError("Nội dung câu hỏi $qNum không được để trống.")
            }

            if (q.options.size != 4) {
                return UpdateQuizResult.ValidationError("Câu hỏi $qNum phải có đúng 4 phương án lựa chọn.")
            }

            val cleanedOptions = mutableListOf<String>()
            val optionLetters = listOf("A", "B", "C", "D")
            for ((optIdx, opt) in q.options.withIndex()) {
                val trimmedOpt = opt.trim()
                if (trimmedOpt.isEmpty()) {
                    return UpdateQuizResult.ValidationError("Phương án ${optionLetters[optIdx]} ở câu $qNum không được để trống.")
                }
                cleanedOptions.add(trimmedOpt)
            }

            if (q.correctOptionIndex !in 0..3) {
                return UpdateQuizResult.ValidationError("Vui lòng chọn một đáp án đúng (A, B, C hoặc D) cho câu $qNum.")
            }

            cleanedQuestions.add(
                q.copy(
                    questionOrder = qNum,
                    stem = trimmedStem,
                    options = cleanedOptions,
                    explanation = q.explanation.trim()
                )
            )
        }

        val updatedQuiz = quiz.copy(
            title = trimmedTitle,
            questionCount = cleanedQuestions.size
        )

        quizRepository.updateQuiz(updatedQuiz, cleanedQuestions)
        return UpdateQuizResult.Success
    }
}
