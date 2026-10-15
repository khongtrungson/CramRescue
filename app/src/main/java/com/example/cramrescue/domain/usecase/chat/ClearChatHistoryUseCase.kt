package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.repository.ChatMessageRepository

class ClearChatHistoryUseCase(
    private val repository: ChatMessageRepository
) {
    suspend operator fun invoke(campaignId: Long) {
        repository.clearMessages(campaignId)
    }
}
