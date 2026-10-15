package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.quiz.DraftQuizQuestion
import com.example.cramrescue.domain.quiz.QuizDraft
import com.example.cramrescue.domain.quiz.QuizPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiQuizJsonTest {

    @Test
    fun interactionRequestContainsStructuredSchemaAndCorrectSettings() {
        val prompt = QuizPrompt(
            systemInstruction = "Bạn là người ra đề thi trắc nghiệm.",
            input = "Tài liệu ôn tập [B1] Thuốc kháng sinh..."
        )
        val json = GeminiQuizJson.interactionRequest("gemini-3.5-flash", prompt)

        assertEquals("gemini-3.5-flash", json.getString("model"))
        assertEquals("Bạn là người ra đề thi trắc nghiệm.", json.getString("system_instruction"))
        assertEquals("Tài liệu ôn tập [B1] Thuốc kháng sinh...", json.getString("input"))
        assertEquals(false, json.getBoolean("store"))

        val responseFormat = json.getJSONObject("response_format")
        assertEquals("application/json", responseFormat.getString("mime_type"))

        val schema = responseFormat.getJSONObject("schema")
        assertEquals("object", schema.getString("type"))
        assertTrue(schema.getJSONObject("properties").has("title"))
        assertTrue(schema.getJSONObject("properties").has("questions"))
    }

    @Test
    fun parseQuizResponseValidJson() {
        val json = """
            {
              "status": "completed",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"title\":\"Đề thi Dược lý 1\",\"questions\":[{\"stem\":\"Thuốc nào trị sốt?\",\"options\":[\"Paracetamol\",\"Morphin\",\"Aspirin 500g\",\"Insulin\"],\"correctOptionIndex\":0,\"explanation\":\"Paracetamol là thuốc hạ sốt phổ biến.\",\"sourceBlockIds\":[\"B1\"]}]}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val draft: QuizDraft = GeminiQuizJson.parseInteractionResponse(json)
        assertEquals("Đề thi Dược lý 1", draft.title)
        assertEquals(1, draft.questions.size)

        val q = draft.questions.first()
        assertEquals("Thuốc nào trị sốt?", q.stem)
        assertEquals(4, q.options.size)
        assertEquals("Paracetamol", q.options[0])
        assertEquals(0, q.correctOptionIndex)
        assertEquals("Paracetamol là thuốc hạ sốt phổ biến.", q.explanation)
        assertEquals(listOf("B1"), q.sourceBlockIds)
    }

    @Test
    fun parseQuizResponseMalformedJsonThrowsException() {
        val json = """
            {
              "status": "completed",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"title\":\"Missing questions\"}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        try {
            GeminiQuizJson.parseInteractionResponse(json)
            fail("Expected RemoteFailureException")
        } catch (e: RemoteFailureException) {
            assertEquals(RemoteFailure.MalformedResponse, e.failure)
        }
    }
}
