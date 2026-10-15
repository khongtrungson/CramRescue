package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.QuizAttemptEntity
import com.example.cramrescue.data.local.entity.QuizEntity
import com.example.cramrescue.data.local.entity.QuizQuestionEntity
import com.example.cramrescue.domain.model.Quiz
import com.example.cramrescue.domain.model.QuizAttempt
import com.example.cramrescue.domain.model.QuizAttemptAnswer
import com.example.cramrescue.domain.model.QuizQuestion
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

object QuizMapper {

    fun toEntity(quiz: Quiz): QuizEntity = QuizEntity(
        id = quiz.id,
        campaignId = quiz.campaignId,
        title = quiz.title,
        questionCount = quiz.questionCount,
        createdAtEpochMillis = quiz.createdAt.toEpochMilli(),
        bestScore = quiz.bestScore
    )

    fun toDomain(entity: QuizEntity): Quiz = Quiz(
        id = entity.id,
        campaignId = entity.campaignId,
        title = entity.title,
        questionCount = entity.questionCount,
        createdAt = Instant.ofEpochMilli(entity.createdAtEpochMillis),
        bestScore = entity.bestScore
    )

    fun toEntity(question: QuizQuestion, quizId: Long): QuizQuestionEntity {
        val options = question.options
        val optA = options.getOrElse(0) { "" }
        val optB = options.getOrElse(1) { "" }
        val optC = options.getOrElse(2) { "" }
        val optD = options.getOrElse(3) { "" }
        val blockJson = JSONArray(question.sourceBlockIds).toString()
        return QuizQuestionEntity(
            id = question.id,
            quizId = quizId,
            questionOrder = question.questionOrder,
            stem = question.stem,
            optionA = optA,
            optionB = optB,
            optionC = optC,
            optionD = optD,
            correctOptionIndex = question.correctOptionIndex,
            explanation = question.explanation,
            sourceBlockIdsJson = blockJson
        )
    }

    fun toDomain(entity: QuizQuestionEntity): QuizQuestion {
        val blockIds = mutableListOf<String>()
        try {
            val array = JSONArray(entity.sourceBlockIdsJson)
            for (i in 0 until array.length()) {
                blockIds.add(array.getString(i))
            }
        } catch (_: Exception) {}

        return QuizQuestion(
            id = entity.id,
            quizId = entity.quizId,
            questionOrder = entity.questionOrder,
            stem = entity.stem,
            options = listOf(entity.optionA, entity.optionB, entity.optionC, entity.optionD),
            correctOptionIndex = entity.correctOptionIndex,
            explanation = entity.explanation,
            sourceBlockIds = blockIds
        )
    }

    fun toEntity(attempt: QuizAttempt): QuizAttemptEntity {
        val answersArray = JSONArray()
        attempt.answers.forEach { ans ->
            val obj = JSONObject()
            obj.put("questionId", ans.questionId)
            if (ans.selectedOptionIndex != null) {
                obj.put("selectedOptionIndex", ans.selectedOptionIndex)
            } else {
                obj.put("selectedOptionIndex", JSONObject.NULL)
            }
            answersArray.put(obj)
        }
        return QuizAttemptEntity(
            id = attempt.id,
            quizId = attempt.quizId,
            attemptNumber = attempt.attemptNumber,
            score = attempt.score,
            correctCount = attempt.correctCount,
            totalQuestions = attempt.totalQuestions,
            durationSeconds = attempt.durationSeconds,
            completedAtEpochMillis = attempt.completedAt.toEpochMilli(),
            answersJson = answersArray.toString()
        )
    }

    fun toDomain(entity: QuizAttemptEntity): QuizAttempt {
        val answers = mutableListOf<QuizAttemptAnswer>()
        try {
            val array = JSONArray(entity.answersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val qId = obj.getLong("questionId")
                val sel = if (obj.isNull("selectedOptionIndex")) null else obj.getInt("selectedOptionIndex")
                answers.add(QuizAttemptAnswer(qId, sel))
            }
        } catch (_: Exception) {}

        return QuizAttempt(
            id = entity.id,
            quizId = entity.quizId,
            attemptNumber = entity.attemptNumber,
            score = entity.score,
            correctCount = entity.correctCount,
            totalQuestions = entity.totalQuestions,
            durationSeconds = entity.durationSeconds,
            completedAt = Instant.ofEpochMilli(entity.completedAtEpochMillis),
            answers = answers
        )
    }
}
