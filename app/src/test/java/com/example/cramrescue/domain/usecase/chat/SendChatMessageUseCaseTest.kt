package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.analysis.FAKE_API_KEY
import com.example.cramrescue.domain.analysis.FakeCredentialRepository
import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.chat.ChatPrompt
import com.example.cramrescue.domain.chat.GeminiChatClient
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.model.ChatSender
import com.example.cramrescue.domain.repository.ChatMessageRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendChatMessageUseCaseTest {

    private val documents = FakeSourceDocumentRepository(
        mutableListOf(
            document(1, campaignId = 1, name = "Slide 1", pages = 5),
            document(2, campaignId = 1, name = "DeCuong", pages = 2)
        )
    )
    private val ocrRepository = FakeOcrRepository(
        mutableMapOf(
            1L to ocr(1, (0..4).associateWith { listOf("Slide 1 content on page $it") }),
            2L to ocr(2, mapOf(0 to listOf("De cuong noi dung quan trong")))
        )
    )
    private val credentials = FakeCredentialRepository()
    private val chatClient = FakeGeminiChatClient()
    private val chatRepository = FakeChatMessageRepository()
    private val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    private val useCase = SendChatMessageUseCase(
        getSource,
        credentials,
        chatClient,
        chatRepository,
        BuildChatPromptUseCase(),
        clock
    )

    @Test
    fun successfulChatStreamsDeltasAndPersistsBothUserAndAssistantMessages() = runBlocking {
        chatClient.deltasToEmit = listOf("Định nghĩa: ", "Kinh tế chính trị ", "là... [Slide 1 - Trang 5]")
        val receivedDeltas = mutableListOf<String>()

        val result = useCase(
            campaignId = 1L,
            campaignName = "Kinh Tế Chính Trị",
            question = "Kinh tế chính trị là gì?",
            onDelta = { receivedDeltas.add(it) }
        )

        assertTrue(result is SendChatResult.Success)
        assertEquals(3, receivedDeltas.size)
        assertEquals("Định nghĩa: Kinh tế chính trị là... [Slide 1 - Trang 5]", (result as SendChatResult.Success).message.content)

        // Verifies persisted messages: user message and assistant message
        val stored = chatRepository.messages
        assertEquals(2, stored.size)
        assertEquals(ChatSender.USER, stored[0].sender)
        assertEquals("Kinh tế chính trị là gì?", stored[0].content)
        assertEquals(ChatSender.ASSISTANT, stored[1].sender)
        assertEquals("Định nghĩa: Kinh tế chính trị là... [Slide 1 - Trang 5]", stored[1].content)

        // Citations should be parsed and linked
        assertEquals(1, stored[1].citations.size)
        assertEquals(1L, stored[1].citations[0].documentId)
        assertEquals("Slide 1", stored[1].citations[0].documentLabel)
        assertEquals(4, stored[1].citations[0].pageIndex) // 1-based page 5 -> pageIndex 4

        // Credentials used
        assertEquals(listOf(FAKE_API_KEY), chatClient.keysSent)
    }

    @Test
    fun rejectsWhenCampaignHasNoUsableDocuments() = runBlocking {
        ocrRepository.results.clear()
        val result = useCase(1L, "Triết", "Hỏi câu gì đó", {})
        assertTrue(result is SendChatResult.NoMaterials)
        assertTrue(chatRepository.messages.isEmpty())
        assertTrue(chatClient.promptsSent.isEmpty())
    }

    @Test
    fun rejectsWhenApiKeyIsMissing() = runBlocking {
        credentials.key = null
        val result = useCase(1L, "Triết", "Hỏi câu gì đó", {})
        assertTrue(result is SendChatResult.MissingCredential)
        assertTrue(chatRepository.messages.isEmpty())
        assertTrue(chatClient.promptsSent.isEmpty())
    }

    @Test
    fun networkFailureReturnsFailedResultWhilePersistingUserQuestionForRetry() = runBlocking {
        chatClient.nextFailure = RemoteFailure.Network
        val result = useCase(1L, "Triết", "Hỏi câu gì đó", {})

        assertEquals(SendChatResult.Failed(RemoteFailure.Network), result)
        // User message was recorded so user doesn't lose their input
        assertEquals(1, chatRepository.messages.size)
        assertEquals("Hỏi câu gì đó", chatRepository.messages[0].content)
    }

    @Test
    fun multiTurnHistoryIsPreservedInChatPrompt() = runBlocking {
        chatRepository.messages.add(
            ChatMessage(
                id = 1L,
                campaignId = 1L,
                sender = ChatSender.USER,
                content = "Q1",
                timestamp = 1000L
            )
        )
        chatRepository.messages.add(
            ChatMessage(
                id = 2L,
                campaignId = 1L,
                sender = ChatSender.ASSISTANT,
                content = "A1",
                timestamp = 2000L
            )
        )

        useCase(1L, "Kinh Tế", "Q2", {})

        val prompt = chatClient.promptsSent.single()
        assertEquals(3, prompt.history.size)
        assertEquals("user", prompt.history[0].role)
        assertEquals("Q1", prompt.history[0].text)
        assertEquals("model", prompt.history[1].role)
        assertEquals("A1", prompt.history[1].text)
        assertEquals("user", prompt.history[2].role)
        assertEquals("Q2", prompt.history[2].text)
    }

    private class FakeGeminiChatClient : GeminiChatClient {
        override val modelId: String = "fake-chat-model"
        var deltasToEmit = listOf("Phản hồi mẫu")
        var nextFailure: RemoteFailure? = null
        val promptsSent = mutableListOf<ChatPrompt>()
        val keysSent = mutableListOf<String>()

        override fun streamChat(apiKey: String, prompt: ChatPrompt): Flow<String> = flow {
            keysSent.add(apiKey)
            promptsSent.add(prompt)
            nextFailure?.let { throw RemoteFailureException(it) }
            for (delta in deltasToEmit) {
                emit(delta)
            }
        }
    }

    private class FakeChatMessageRepository : ChatMessageRepository {
        val messages = mutableListOf<ChatMessage>()
        private var nextId = 1L

        override fun getMessagesFlow(campaignId: Long): Flow<List<ChatMessage>> =
            flowOf(messages.filter { it.campaignId == campaignId })

        override suspend fun getMessages(campaignId: Long): List<ChatMessage> =
            messages.filter { it.campaignId == campaignId }

        override suspend fun saveMessage(message: ChatMessage): Long {
            val id = if (message.id == 0L) nextId++ else message.id
            messages.add(message.copy(id = id))
            return id
        }

        override suspend fun clearMessages(campaignId: Long) {
            messages.removeAll { it.campaignId == campaignId }
        }
    }
}
