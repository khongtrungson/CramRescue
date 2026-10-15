package com.example.cramrescue.domain.model

enum class ChatSender {
    USER,
    ASSISTANT
}

data class ChatCitation(
    val documentId: Long,
    val documentLabel: String,
    val pageIndex: Int
)

data class ChatMessage(
    val id: Long = 0L,
    val campaignId: Long,
    val sender: ChatSender,
    val content: String,
    val timestamp: Long,
    val citations: List<ChatCitation> = emptyList()
)
