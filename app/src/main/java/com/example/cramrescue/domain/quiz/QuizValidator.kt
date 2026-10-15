package com.example.cramrescue.domain.quiz

sealed interface QuizValidationError {
    data object BlankTitle : QuizValidationError
    data object EmptyQuestions : QuizValidationError
    data class BlankStem(val questionIndex: Int) : QuizValidationError
    data class InvalidOptionCount(val questionIndex: Int, val actualCount: Int) : QuizValidationError
    data class BlankOption(val questionIndex: Int, val optionIndex: Int) : QuizValidationError
    data class InvalidCorrectOptionIndex(val questionIndex: Int, val index: Int) : QuizValidationError
    data class BlankExplanation(val questionIndex: Int) : QuizValidationError
    data class EmptySourceBlocks(val questionIndex: Int) : QuizValidationError
    data class UnknownSourceBlock(val questionIndex: Int, val blockId: String) : QuizValidationError
}

sealed interface QuizValidationResult {
    data object Valid : QuizValidationResult
    data class Invalid(val error: QuizValidationError) : QuizValidationResult
}

object QuizValidator {

    fun validate(draft: QuizDraft, validBlockIds: Set<String>? = null): QuizValidationResult {
        if (draft.title.isBlank()) {
            return QuizValidationResult.Invalid(QuizValidationError.BlankTitle)
        }
        if (draft.questions.isEmpty()) {
            return QuizValidationResult.Invalid(QuizValidationError.EmptyQuestions)
        }

        draft.questions.forEachIndexed { qIdx, question ->
            if (question.stem.isBlank()) {
                return QuizValidationResult.Invalid(QuizValidationError.BlankStem(qIdx))
            }
            if (question.options.size != 4) {
                return QuizValidationResult.Invalid(QuizValidationError.InvalidOptionCount(qIdx, question.options.size))
            }
            question.options.forEachIndexed { oIdx, opt ->
                if (opt.isBlank()) {
                    return QuizValidationResult.Invalid(QuizValidationError.BlankOption(qIdx, oIdx))
                }
            }
            if (question.correctOptionIndex !in 0..3) {
                return QuizValidationResult.Invalid(QuizValidationError.InvalidCorrectOptionIndex(qIdx, question.correctOptionIndex))
            }
            if (question.explanation.isBlank()) {
                return QuizValidationResult.Invalid(QuizValidationError.BlankExplanation(qIdx))
            }
            if (question.sourceBlockIds.isEmpty()) {
                return QuizValidationResult.Invalid(QuizValidationError.EmptySourceBlocks(qIdx))
            }
            if (validBlockIds != null) {
                for (rawId in question.sourceBlockIds) {
                    // Normalize "[B12]" -> "B12" if brackets present
                    val normalized = rawId.trim().removeSurrounding("[", "]")
                    val matchFound = validBlockIds.contains(rawId) || validBlockIds.contains(normalized) ||
                            validBlockIds.any { it.removeSurrounding("[", "]") == normalized }
                    if (!matchFound) {
                        return QuizValidationResult.Invalid(QuizValidationError.UnknownSourceBlock(qIdx, rawId))
                    }
                }
            }
        }

        return QuizValidationResult.Valid
    }
}
