package com.example.cramrescue.domain.chat

import kotlinx.coroutines.flow.Flow

data class ChatPromptMessage(
    val role: String, // "user" or "model"
    val text: String
)

data class ChatPrompt(
    val systemInstruction: String,
    val history: List<ChatPromptMessage>
)

interface GeminiChatClient {
    val modelId: String
    fun streamChat(apiKey: String, prompt: ChatPrompt): Flow<String>
}
