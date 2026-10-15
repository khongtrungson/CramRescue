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

fun Quiz.toEntity(): QuizEntity = QuizEntity(
    id = id,
    campaignId = campaignId,
    title = title,
    questionCount = questionCount,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    bestScore = bestScore
)

fun QuizEntity.toDomain(): Quiz = Quiz(
    id = id,
    campaignId = campaignId,
    title = title,
    questionCount = questionCount,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    bestScore = bestScore
)

fun QuizQuestion.toEntity(quizId: Long): QuizQuestionEntity {
    val optA = options.getOrElse(0) { "" }
    val optB = options.getOrElse(1) { "" }
    val optC = options.getOrElse(2) { "" }
    val optD = options.getOrElse(3) { "" }
    val blockJson = JSONArray(sourceBlockIds).toString()
    return QuizQuestionEntity(
        id = id,
        quizId = quizId,
        questionOrder = questionOrder,
        stem = stem,
        optionA = optA,
        optionB = optB,
        optionC = optC,
        optionD = optD,
        correctOptionIndex = correctOptionIndex,
        explanation = explanation,
        sourceBlockIdsJson = blockJson
    )
}

fun QuizQuestionEntity.toDomain(): QuizQuestion {
    val blockIds = mutableListOf<String>()
    try {
        val array = JSONArray(sourceBlockIdsJson)
        for (i in 0 until array.length()) {
            blockIds.add(array.getString(i))
        }
    } catch (_: Exception) {}

    return QuizQuestion(
        id = id,
        quizId = quizId,
        questionOrder = questionOrder,
        stem = stem,
        options = listOf(optionA, optionB, optionC, optionD),
        correctOptionIndex = correctOptionIndex,
        explanation = explanation,
        sourceBlockIds = blockIds
    )
}

fun QuizAttempt.toEntity(): QuizAttemptEntity {
    val answersArray = JSONArray()
    answers.forEach { ans ->
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
        id = id,
        quizId = quizId,
        attemptNumber = attemptNumber,
        score = score,
        correctCount = correctCount,
        totalQuestions = totalQuestions,
        durationSeconds = durationSeconds,
        completedAtEpochMillis = completedAt.toEpochMilli(),
        answersJson = answersArray.toString()
    )
}

fun QuizAttemptEntity.toDomain(): QuizAttempt {
    val answers = mutableListOf<QuizAttemptAnswer>()
    try {
        val array = JSONArray(answersJson)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val qId = obj.getLong("questionId")
            val sel = if (obj.isNull("selectedOptionIndex")) null else obj.getInt("selectedOptionIndex")
            answers.add(QuizAttemptAnswer(qId, sel))
        }
    } catch (_: Exception) {}

    return QuizAttempt(
        id = id,
        quizId = quizId,
        attemptNumber = attemptNumber,
        score = score,
        correctCount = correctCount,
        totalQuestions = totalQuestions,
        durationSeconds = durationSeconds,
        completedAt = Instant.ofEpochMilli(completedAtEpochMillis),
        answers = answers
    )
}

object QuizMapper {
    fun toEntity(quiz: Quiz): QuizEntity = quiz.toEntity()
    fun toDomain(entity: QuizEntity): Quiz = entity.toDomain()
    fun toEntity(question: QuizQuestion, quizId: Long): QuizQuestionEntity = question.toEntity(quizId)
    fun toDomain(entity: QuizQuestionEntity): QuizQuestion = entity.toDomain()
    fun toEntity(attempt: QuizAttempt): QuizAttemptEntity = attempt.toEntity()
    fun toDomain(entity: QuizAttemptEntity): QuizAttempt = entity.toDomain()
}
