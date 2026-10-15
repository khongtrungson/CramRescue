package com.example.cramrescue.presentation.chat

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.ChatMessage

data class ChatBotUiState(
    val campaign: Campaign? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isMaterialsEmpty: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val inputText: String = "",
    val error: RemoteFailure? = null,
    val isMissingCredential: Boolean = false,
    val lastFailedQuestion: String? = null
)
