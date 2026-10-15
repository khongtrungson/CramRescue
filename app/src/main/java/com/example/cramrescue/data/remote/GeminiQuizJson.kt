package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.quiz.DraftQuizQuestion
import com.example.cramrescue.domain.quiz.QuizDraft
import com.example.cramrescue.domain.quiz.QuizPrompt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GeminiQuizJson {

    fun quizSchema(): JSONObject = obj(
        "type" to "object",
        "properties" to obj(
            "title" to obj("type" to "string", "description" to "Tiêu đề bộ đề trắc nghiệm."),
            "questions" to obj(
                "type" to "array",
                "items" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "stem" to obj("type" to "string", "description" to "Nội dung câu hỏi trắc nghiệm."),
                        "options" to obj(
                            "type" to "array",
                            "items" to obj("type" to "string"),
                            "minItems" to 4,
                            "maxItems" to 4,
                            "description" to "Chính xác 4 phương án lựa chọn."
                        ),
                        "correctOptionIndex" to obj(
                            "type" to "integer",
                            "minimum" to 0,
                            "maximum" to 3,
                            "description" to "Chỉ số của đáp án đúng (0 cho A, 1 cho B, 2 cho C, 3 cho D)."
                        ),
                        "explanation" to obj("type" to "string", "description" to "Giải thích ngắn gọn lý do đáp án đúng."),
                        "sourceBlockIds" to obj(
                            "type" to "array",
                            "minItems" to 1,
                            "items" to obj("type" to "string", "description" to "Mã khối nguồn, ví dụ [B12].")
                        )
                    ),
                    "required" to JSONArray(listOf("stem", "options", "correctOptionIndex", "explanation", "sourceBlockIds"))
                )
            )
        ),
        "required" to JSONArray(listOf("title", "questions"))
    )

    fun interactionRequest(model: String, prompt: QuizPrompt): JSONObject = obj(
        "model" to model,
        "system_instruction" to prompt.systemInstruction,
        "input" to prompt.input,
        "response_format" to obj(
            "type" to "text",
            "mime_type" to "application/json",
            "schema" to quizSchema()
        ),
        "store" to false
    )

    fun parseInteractionResponse(body: String): QuizDraft = malformedOnError {
        val envelope = JSONObject(body)
        val status = envelope.optString("status", "completed")
        if (status != "completed") throw malformed()
        parseQuiz(modelText(envelope))
    }

    fun parseQuiz(json: String): QuizDraft = malformedOnError {
        val root = JSONObject(json)
        QuizDraft(
            title = root.requireString("title"),
            questions = root.requireArray("questions").objects().map { item ->
                DraftQuizQuestion(
                    stem = item.requireString("stem"),
                    options = item.requireArray("options").strings(),
                    correctOptionIndex = item.getInt("correctOptionIndex"),
                    explanation = item.requireString("explanation"),
                    sourceBlockIds = item.requireArray("sourceBlockIds").strings()
                )
            }
        )
    }

    private fun modelText(envelope: JSONObject): String {
        val texts = mutableListOf<String>()
        envelope.optJSONArray("steps")?.objects()
            ?.filter { it.optString("type") == "model_output" }
            ?.forEach { step -> step.optJSONArray("content")?.objects()?.textParts()?.let(texts::addAll) }
        if (texts.isEmpty()) envelope.optJSONArray("outputs")?.objects()?.textParts()?.let(texts::addAll)
        if (texts.isEmpty()) throw malformed()
        return texts.joinToString("")
    }

    private fun List<JSONObject>.textParts(): List<String> =
        filter { it.optString("type") == "text" }.mapNotNull { it.opt("text") as? String }

    private fun JSONObject.requireString(name: String): String = opt(name) as? String ?: throw malformed()
    private fun JSONObject.requireArray(name: String): JSONArray = opt(name) as? JSONArray ?: throw malformed()

    private fun JSONArray.strings(): List<String> =
        (0 until length()).map { opt(it) as? String ?: throw malformed() }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).map { opt(it) as? JSONObject ?: throw malformed() }

    private fun malformed() = RemoteFailureException(RemoteFailure.MalformedResponse)

    private inline fun <T> malformedOnError(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw malformed()
    }

    private fun obj(vararg pairs: Pair<String, Any>): JSONObject =
        JSONObject().apply { pairs.forEach { (key, value) -> put(key, value) } }
}
