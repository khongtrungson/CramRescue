package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.repository.ChatMessageRepository
import kotlinx.coroutines.flow.Flow

class GetChatMessagesUseCase(
    private val repository: ChatMessageRepository
) {
    operator fun invoke(campaignId: Long): Flow<List<ChatMessage>> {
        return repository.getMessagesFlow(campaignId)
    }
}
