package com.example.cramrescue.domain.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizValidatorTest {

    @Test
    fun validDraftPasses() {
        val draft = QuizDraft(
            title = "Đề thi mẫu",
            questions = listOf(
                DraftQuizQuestion(
                    stem = "Câu hỏi 1?",
                    options = listOf("A", "B", "C", "D"),
                    correctOptionIndex = 1,
                    explanation = "Vì B đúng.",
                    sourceBlockIds = listOf("B1", "B2")
                )
            )
        )

        val result = QuizValidator.validate(draft, validBlockIds = setOf("B1", "B2", "B3"))
        assertTrue(result is QuizValidationResult.Valid)
    }

    @Test
    fun emptyTitleRejected() {
        val draft = QuizDraft(
            title = "   ",
            questions = listOf(
                DraftQuizQuestion(
                    stem = "Câu hỏi?",
                    options = listOf("A", "B", "C", "D"),
                    correctOptionIndex = 0,
                    explanation = "Giải thích",
                    sourceBlockIds = listOf("B1")
                )
            )
        )
        val result = QuizValidator.validate(draft, validBlockIds = setOf("B1"))
        assertTrue(result is QuizValidationResult.Invalid)
        assertEquals(QuizValidationError.BlankTitle, (result as QuizValidationResult.Invalid).error)
    }

    @Test
    fun questionWithWrongOptionCountRejected() {
        val draft = QuizDraft(
            title = "Đề thi mẫu",
            questions = listOf(
                DraftQuizQuestion(
                    stem = "Câu hỏi chỉ có 3 đáp án?",
                    options = listOf("A", "B", "C"),
                    correctOptionIndex = 0,
                    explanation = "Giải thích",
                    sourceBlockIds = listOf("B1")
                )
            )
        )
        val result = QuizValidator.validate(draft, validBlockIds = setOf("B1"))
        assertTrue(result is QuizValidationResult.Invalid)
        assertEquals(QuizValidationError.InvalidOptionCount(0, 3), (result as QuizValidationResult.Invalid).error)
    }

    @Test
    fun questionWithOutOfRangeCorrectIndexRejected() {
        val draft = QuizDraft(
            title = "Đề thi mẫu",
            questions = listOf(
                DraftQuizQuestion(
                    stem = "Câu hỏi?",
                    options = listOf("A", "B", "C", "D"),
                    correctOptionIndex = 4,
                    explanation = "Giải thích",
                    sourceBlockIds = listOf("B1")
                )
            )
        )
        val result = QuizValidator.validate(draft, validBlockIds = setOf("B1"))
        assertTrue(result is QuizValidationResult.Invalid)
        assertEquals(QuizValidationError.InvalidCorrectOptionIndex(0, 4), (result as QuizValidationResult.Invalid).error)
    }

    @Test
    fun questionWithInvalidSourceBlockIdRejected() {
        val draft = QuizDraft(
            title = "Đề thi mẫu",
            questions = listOf(
                DraftQuizQuestion(
                    stem = "Câu hỏi?",
                    options = listOf("A", "B", "C", "D"),
                    correctOptionIndex = 2,
                    explanation = "Giải thích",
                    sourceBlockIds = listOf("B999")
                )
            )
        )
        val result = QuizValidator.validate(draft, validBlockIds = setOf("B1", "B2"))
        assertTrue(result is QuizValidationResult.Invalid)
        assertEquals(QuizValidationError.UnknownSourceBlock(0, "B999"), (result as QuizValidationResult.Invalid).error)
    }
}
