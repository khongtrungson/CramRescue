package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatMessageRepository {
    fun getMessagesFlow(campaignId: Long): Flow<List<ChatMessage>>
    suspend fun getMessages(campaignId: Long): List<ChatMessage>
    suspend fun saveMessage(message: ChatMessage): Long
    suspend fun clearMessages(campaignId: Long)
}
