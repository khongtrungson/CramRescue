package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.chat.ChatPrompt
import com.example.cramrescue.domain.chat.ChatPromptMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiChatSseTest {

    @Test
    fun createStreamChatRequestFormatsCorrectJson() {
        val prompt = ChatPrompt(
            systemInstruction = "Bạn là trợ lý ôn thi.",
            history = listOf(
                ChatPromptMessage(role = "user", text = "Xin chào"),
                ChatPromptMessage(role = "model", text = "Chào bạn! Cần tôi giúp gì?"),
                ChatPromptMessage(role = "user", text = "Công thức tính đạo hàm?")
            )
        )

        val json = GeminiChatJson.createStreamChatRequest(prompt)

        val sysInstruction = json.getJSONObject("system_instruction")
        val sysParts = sysInstruction.getJSONArray("parts")
        assertEquals("Bạn là trợ lý ôn thi.", sysParts.getJSONObject(0).getString("text"))

        val contents = json.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("Xin chào", contents.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("user", contents.getJSONObject(2).getString("role"))

        val genConfig = json.getJSONObject("generationConfig")
        assertNotNull(genConfig)
    }

    @Test
    fun parseSseLineExtractsTextDeltaFromValidDataLine() {
        val line = """data: {"candidates": [{"content": {"parts": [{"text": "Đạo hàm của x^2 là 2x."}]}}]}"""
        val delta = GeminiChatJson.parseSseLine(line)
        assertEquals("Đạo hàm của x^2 là 2x.", delta)
    }

    @Test
    fun parseSseLineExtractsMultiplePartsConcatenated() {
        val line = """data: {"candidates": [{"content": {"parts": [{"text": "Phần 1. "}, {"text": "Phần 2."}]}}]}"""
        val delta = GeminiChatJson.parseSseLine(line)
        assertEquals("Phần 1. Phần 2.", delta)
    }

    @Test
    fun parseSseLineIgnoresCommentsAndDoneAndEmptyLines() {
        assertNull(GeminiChatJson.parseSseLine(": heartbeat"))
        assertNull(GeminiChatJson.parseSseLine(""))
        assertNull(GeminiChatJson.parseSseLine("   "))
        assertNull(GeminiChatJson.parseSseLine("data: [DONE]"))
        assertNull(GeminiChatJson.parseSseLine("data:   "))
        assertNull(GeminiChatJson.parseSseLine("event: message"))
    }

    @Test
    fun parseSseLineHandlesMalformedOrEmptyCandidatesGracefully() {
        assertNull(GeminiChatJson.parseSseLine("data: {invalid json}"))
        assertNull(GeminiChatJson.parseSseLine("""data: {"candidates": []}"""))
        assertNull(GeminiChatJson.parseSseLine("""data: {"otherField": 123}"""))
        assertEquals("", GeminiChatJson.parseSseLine("""data: {"candidates": [{"content": {"parts": []}}]}"""))
    }
}
