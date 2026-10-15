package com.example.cramrescue.data.remote

import com.example.cramrescue.domain.chat.ChatPrompt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GeminiChatJson {

    fun createStreamChatRequest(prompt: ChatPrompt): JSONObject {
        val root = JSONObject()

        val systemInstructionObj = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", prompt.systemInstruction) })
            })
        }
        root.put("system_instruction", systemInstructionObj)

        val contentsArray = JSONArray()
        prompt.history.forEach { msg ->
            contentsArray.put(JSONObject().apply {
                put("role", msg.role)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", msg.text) })
                })
            })
        }
        root.put("contents", contentsArray)

        root.put("generationConfig", JSONObject().apply {
            put("temperature", 0.2)
        })

        return root
    }

    /**
     * Parses a single line from an SSE stream.
     * Returns the text delta if the line is a data event with candidate text parts, or null if it
     * is an empty line, comment, [DONE] marker, or non-data line.
     */
    fun parseSseLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null

        return try {
            val obj = JSONObject(payload)
            val candidates = obj.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i)
                val text = part?.optString("text")
                if (!text.isNullOrEmpty()) {
                    sb.append(text)
                }
            }
            sb.toString()
        } catch (_: JSONException) {
            null
        }
    }
}
