package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.AnalysisDraft
import com.example.cramrescue.domain.analysis.DraftPriority
import com.example.cramrescue.domain.analysis.MAX_ANALYSIS_PRIORITIES
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.StudyAnalysisPrompt
import com.example.cramrescue.domain.model.MAX_BULLETS
import com.example.cramrescue.domain.model.TriageLevel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Gemini wire format (Interactions API, v1beta), kept separate from the HTTP code so it can be
 * unit-tested. Based on ai.google.dev/gemini-api/docs/structured-output and
 * ai.google.dev/api/interactions-api as of September 2026.
 */
object GeminiJson {

    /** The structured-output JSON Schema. Field names here must match [parseAnalysis]. */
    fun analysisSchema(): JSONObject = obj(
        "type" to "object",
        "properties" to obj(
            "summary" to obj("type" to "string", "description" to "1–2 câu định hướng ôn tập."),
            "priorities" to obj(
                "type" to "array",
                "items" to obj(
                    "type" to "object",
                    "properties" to obj(
                        "title" to obj("type" to "string"),
                        "triage" to obj(
                            "type" to "string",
                            "enum" to JSONArray(TriageLevel.entries.map { it.name })
                        ),
                        "bullets" to obj(
                            "type" to "array",
                            "maxItems" to MAX_BULLETS,
                            "items" to obj("type" to "string")
                        ),
                        "sourceBlockIds" to obj(
                            "type" to "array",
                            "minItems" to 1,
                            "items" to obj("type" to "string", "description" to "Mã khối nguồn, ví dụ B12.")
                        )
                    ),
                    "required" to JSONArray(listOf("title", "triage", "bullets", "sourceBlockIds"))
                )
            )
        ),
        "required" to JSONArray(listOf("summary", "priorities"))
    )

    fun interactionRequest(model: String, prompt: StudyAnalysisPrompt): JSONObject = obj(
        "model" to model,
        "system_instruction" to prompt.systemInstruction,
        "input" to prompt.input,
        "response_format" to obj(
            "type" to "text",
            "mime_type" to "application/json",
            "schema" to analysisSchema()
        ),
//         No thinking_level: it is a Gemini 3 control; Gemini 2.5 Flash uses its default thinking.
//         Study material must not be retained server-side for later retrieval.
        "store" to false
    )

    /**
     * Interaction envelope → model text → typed [AnalysisDraft].
     * Throws [RemoteFailureException] with [RemoteFailure.MalformedResponse] on any shape problem.
     */
    fun parseInteractionResponse(body: String): AnalysisDraft = malformedOnError {
        val envelope = JSONObject(body)
        val status = envelope.optString("status", "completed")
        if (status != "completed") throw malformed()
        parseAnalysis(modelText(envelope))
    }

    /** Decodes the structured analysis JSON with strict types (no silent coercion). */
    fun parseAnalysis(json: String): AnalysisDraft = malformedOnError {
        val root = JSONObject(json)
        AnalysisDraft(
            summary = root.requireString("summary"),
            priorities = root.requireArray("priorities").objects().map { item ->
                DraftPriority(
                    title = item.requireString("title"),
                    triage = item.requireString("triage"),
                    bullets = item.optionalStrings("bullets"),
                    sourceBlockIds = item.optionalStrings("sourceBlockIds")
                )
            }
        )
    }

    /**
     * Maps a non-2xx response to a category. [errorBody] is inspected only for the legacy
     * API_KEY_INVALID marker and is never logged or surfaced.
     */
    fun failureForStatus(code: Int, retryAfterHeader: String?, errorBody: String): RemoteFailure = when {
        code == 401 || code == 403 -> RemoteFailure.InvalidCredential
        code == 400 && errorBody.contains("API_KEY_INVALID") -> RemoteFailure.InvalidCredential
        code == 402 -> RemoteFailure.QuotaExhausted
        code == 429 -> RemoteFailure.RateLimited(retryAfterHeader?.trim()?.toLongOrNull()?.takeIf { it >= 0 })
        code in 500..599 -> RemoteFailure.Service
        else -> RemoteFailure.RequestRejected
    }

    private fun modelText(envelope: JSONObject): String {
        val texts = mutableListOf<String>()
        envelope.optJSONArray("steps")?.objects()
            ?.filter { it.optString("type") == "model_output" }
            ?.forEach { step -> step.optJSONArray("content")?.objects()?.textParts()?.let(texts::addAll) }
        // Older Interactions responses put text parts directly in "outputs".
        if (texts.isEmpty()) envelope.optJSONArray("outputs")?.objects()?.textParts()?.let(texts::addAll)
        if (texts.isEmpty()) throw malformed()
        return texts.joinToString("")
    }

    private fun List<JSONObject>.textParts(): List<String> =
        filter { it.optString("type") == "text" }.mapNotNull { it.opt("text") as? String }

    private fun JSONObject.requireString(name: String): String = opt(name) as? String ?: throw malformed()
    private fun JSONObject.requireArray(name: String): JSONArray = opt(name) as? JSONArray ?: throw malformed()

    private fun JSONObject.optionalStrings(name: String): List<String> {
        val value = opt(name) ?: return emptyList()
        val array = value as? JSONArray ?: throw malformed()
        return (0 until array.length()).map { array.opt(it) as? String ?: throw malformed() }
    }

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
