package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.StudyAnalysisPrompt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class GeminiJsonTest {

    private val analysisJson =
        """{"summary":"Ôn tìm kiếm nhị phân","priorities":[{"title":"Binary search","triage":"CODE_RED","bullets":["Cần mảng đã sắp xếp"],"sourceBlockIds":["B1","B2"]}]}"""

    private fun envelope(text: String, status: String = "completed") = JSONObject()
        .put("id", "x")
        .put("status", status)
        .put(
            "steps",
            org.json.JSONArray()
                .put(JSONObject().put("type", "user_input"))
                .put(
                    JSONObject().put("type", "model_output").put(
                        "content",
                        org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text))
                    )
                )
        ).toString()

    private fun assertMalformed(block: () -> Unit) {
        try {
            block()
            fail("expected MalformedResponse")
        } catch (e: RemoteFailureException) {
            assertEquals(RemoteFailure.MalformedResponse, e.failure)
        }
    }

    @Test
    fun completedInteractionDecodesIntoTypedDraft() {
        val draft = GeminiJson.parseInteractionResponse(envelope(analysisJson))

        assertEquals("Ôn tìm kiếm nhị phân", draft.summary)
        val priority = draft.priorities.single()
        assertEquals("Binary search", priority.title)
        assertEquals("CODE_RED", priority.triage)
        assertEquals(listOf("Cần mảng đã sắp xếp"), priority.bullets)
        assertEquals(listOf("B1", "B2"), priority.sourceBlockIds)
    }

    @Test
    fun legacyOutputsShapeIsAlsoRead() {
        val body = JSONObject().put("status", "completed").put(
            "outputs",
            org.json.JSONArray().put(JSONObject().put("type", "text").put("text", analysisJson))
        ).toString()
        assertEquals(1, GeminiJson.parseInteractionResponse(body).priorities.size)
    }

    @Test
    fun malformedResponsesAreRejected() {
        assertMalformed { GeminiJson.parseInteractionResponse("not json") }
        assertMalformed { GeminiJson.parseInteractionResponse(envelope("## Heading\n- free-form markdown")) }
        assertMalformed { GeminiJson.parseInteractionResponse(envelope(analysisJson, status = "incomplete")) }
        assertMalformed { GeminiJson.parseInteractionResponse("""{"status":"completed","steps":[]}""") }
        // Wrong types are not silently coerced.
        assertMalformed { GeminiJson.parseAnalysis("""{"summary":5,"priorities":[]}""") }
        assertMalformed { GeminiJson.parseAnalysis("""{"summary":"s","priorities":[{"title":"t","triage":"CODE_RED","bullets":[1],"sourceBlockIds":[]}]}""") }
        assertMalformed { GeminiJson.parseAnalysis("""{"summary":"s"}""") }
    }

    @Test
    fun httpStatusesMapToDistinctCategories() {
        assertEquals(RemoteFailure.InvalidCredential, GeminiJson.failureForStatus(401, null, ""))
        assertEquals(RemoteFailure.InvalidCredential, GeminiJson.failureForStatus(403, null, ""))
        assertEquals(
            RemoteFailure.InvalidCredential,
            GeminiJson.failureForStatus(400, null, """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""")
        )
        assertEquals(RemoteFailure.RequestRejected, GeminiJson.failureForStatus(400, null, "{}"))
        assertEquals(RemoteFailure.QuotaExhausted, GeminiJson.failureForStatus(402, null, ""))
        assertEquals(RemoteFailure.RateLimited(17), GeminiJson.failureForStatus(429, " 17 ", ""))
        assertEquals(RemoteFailure.RateLimited(null), GeminiJson.failureForStatus(429, "Wed, 21 Oct 2026 07:28:00 GMT", ""))
        assertEquals(RemoteFailure.Service, GeminiJson.failureForStatus(500, null, ""))
        assertEquals(RemoteFailure.Service, GeminiJson.failureForStatus(503, null, ""))
        assertEquals(RemoteFailure.RequestRejected, GeminiJson.failureForStatus(404, null, ""))
    }

    @Test
    fun requestUsesStructuredOutputSendsOnlyTextAndIsNotStored() {
        val request = GeminiJson.interactionRequest("model-x", StudyAnalysisPrompt("SYS", "INPUT"))

        assertEquals("model-x", request.getString("model"))
        assertEquals("SYS", request.getString("system_instruction"))
        assertEquals("INPUT", request.getString("input"))
        assertFalse(request.getBoolean("store"))
        val format = request.getJSONObject("response_format")
        assertEquals("application/json", format.getString("mime_type"))
        val priorityItem = format.getJSONObject("schema").getJSONObject("properties")
            .getJSONObject("priorities").getJSONObject("items").getJSONObject("properties")
        assertEquals(
            listOf("CODE_RED", "CODE_YELLOW", "CODE_GREEN"),
            priorityItem.getJSONObject("triage").getJSONArray("enum").let { a -> (0 until a.length()).map(a::getString) }
        )
        assertEquals(3, priorityItem.getJSONObject("bullets").getInt("maxItems"))
    }

    @Test
    fun failureExceptionMessageContainsOnlyTheCategory() {
        val message = RemoteFailureException(RemoteFailure.InvalidCredential).message!!
        assertEquals("AI service call failed: InvalidCredential", message)
    }
}
