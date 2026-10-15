package com.example.cramrescue.data

import com.example.cramrescue.data.local.dao.ChatMessageDao
import com.example.cramrescue.data.local.entity.ChatMessageEntity
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.data.repository.ChatMessageRepositoryImpl
import com.example.cramrescue.domain.model.ChatCitation
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.model.ChatSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagePersistenceTest {

    @Test
    fun mapperRoundTripWithCitationsPreservesAllFields() {
        val citations = listOf(
            ChatCitation(documentId = 10L, documentLabel = "Slide 1", pageIndex = 4),
            ChatCitation(documentId = 12L, documentLabel = "DeCuong", pageIndex = 1)
        )
        val domain = ChatMessage(
            id = 5L,
            campaignId = 42L,
            sender = ChatSender.ASSISTANT,
            content = "Dưới đây là công thức...",
            timestamp = 1700000000L,
            citations = citations
        )

        val entity = domain.toEntity()
        assertEquals(5L, entity.id)
        assertEquals(42L, entity.campaignId)
        assertEquals("ASSISTANT", entity.sender)
        assertEquals("Dưới đây là công thức...", entity.content)
        assertEquals(1700000000L, entity.timestamp)
        assertTrue(entity.citationsJson!!.contains("Slide 1"))

        val backToDomain = entity.toDomain()
        assertEquals(domain.id, backToDomain.id)
        assertEquals(domain.campaignId, backToDomain.campaignId)
        assertEquals(domain.sender, backToDomain.sender)
        assertEquals(domain.content, backToDomain.content)
        assertEquals(domain.timestamp, backToDomain.timestamp)
        assertEquals(2, backToDomain.citations.size)
        assertEquals(10L, backToDomain.citations[0].documentId)
        assertEquals("Slide 1", backToDomain.citations[0].documentLabel)
        assertEquals(4, backToDomain.citations[0].pageIndex)
    }

    @Test
    fun mapperHandlesEmptyCitationsGracefully() {
        val domain = ChatMessage(
            id = 1L,
            campaignId = 2L,
            sender = ChatSender.USER,
            content = "Xin chào",
            timestamp = 1000L,
            citations = emptyList()
        )
        val entity = domain.toEntity()
        assertEquals(null, entity.citationsJson)

        val back = entity.toDomain()
        assertTrue(back.citations.isEmpty())
        assertEquals(ChatSender.USER, back.sender)
    }

    @Test
    fun repositorySavesAndRetrievesMessages() = runBlocking {
        val fakeDao = FakeChatMessageDao()
        val repository = ChatMessageRepositoryImpl(fakeDao)

        val msg1 = ChatMessage(
            id = 0L,
            campaignId = 1L,
            sender = ChatSender.USER,
            content = "Câu hỏi 1",
            timestamp = 100L
        )
        val id = repository.saveMessage(msg1)
        assertEquals(1L, id)

        val list = repository.getMessages(1L)
        assertEquals(1, list.size)
        assertEquals("Câu hỏi 1", list[0].content)

        val flowList = repository.getMessagesFlow(1L).first()
        assertEquals(1, flowList.size)

        repository.clearMessages(1L)
        assertTrue(repository.getMessages(1L).isEmpty())
    }

    private class FakeChatMessageDao : ChatMessageDao {
        private val messages = mutableListOf<ChatMessageEntity>()
        private val flow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insertMessage(message: ChatMessageEntity): Long {
            val assignedId = if (message.id == 0L) nextId++ else message.id
            val saved = message.copy(id = assignedId)
            messages.add(saved)
            flow.value = messages.toList()
            return assignedId
        }

        override fun getMessagesFlow(campaignId: Long): Flow<List<ChatMessageEntity>> = flow

        override suspend fun getMessages(campaignId: Long): List<ChatMessageEntity> {
            return messages.filter { it.campaignId == campaignId }.sortedBy { it.timestamp }
        }

        override suspend fun clearMessages(campaignId: Long): Int {
            val count = messages.count { it.campaignId == campaignId }
            messages.removeAll { it.campaignId == campaignId }
            flow.value = messages.toList()
            return count
        }
    }
}
