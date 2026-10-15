package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.ChatMessageDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.repository.ChatMessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatMessageRepositoryImpl(
    private val chatMessageDao: ChatMessageDao
) : ChatMessageRepository {

    override fun getMessagesFlow(campaignId: Long): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesFlow(campaignId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMessages(campaignId: Long): List<ChatMessage> {
        return chatMessageDao.getMessages(campaignId).map { it.toDomain() }
    }

    override suspend fun saveMessage(message: ChatMessage): Long {
        return chatMessageDao.insertMessage(message.toEntity())
    }

    override suspend fun clearMessages(campaignId: Long) {
        chatMessageDao.clearMessages(campaignId)
    }
}
