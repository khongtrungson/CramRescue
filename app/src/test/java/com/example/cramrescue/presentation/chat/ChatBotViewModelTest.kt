package com.example.cramrescue.presentation.chat

import com.example.cramrescue.domain.analysis.FakeCredentialRepository
import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.campaign
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.chat.ChatPrompt
import com.example.cramrescue.domain.chat.GeminiChatClient
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.model.ChatSender
import com.example.cramrescue.domain.repository.ChatMessageRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.campaign.InMemoryCampaignRepository
import com.example.cramrescue.domain.usecase.chat.BuildChatPromptUseCase
import com.example.cramrescue.domain.usecase.chat.ClearChatHistoryUseCase
import com.example.cramrescue.domain.usecase.chat.GetChatMessagesUseCase
import com.example.cramrescue.domain.usecase.chat.SendChatMessageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBotViewModelTest {

    private val campaignRepo = InMemoryCampaignRepository().apply {
        runBlocking { createCampaign(campaign(1)) }
    }
    private val docRepo = FakeSourceDocumentRepository(mutableListOf(document(10, campaignId = 1, name = "Slide 1")))
    private val ocrRepo = FakeOcrRepository(mutableMapOf(10L to ocr(10L, mapOf(0 to listOf("Nội dung tài liệu ôn tập quan trọng")))))
    private val credRepo = FakeCredentialRepository()
    private val chatClient = FakeGeminiChatClient()
    private val chatRepo = FakeChatMessageRepository()

    private val getSource = GetAnalysisSourceUseCase(docRepo, ocrRepo)
    private val getMessages = GetChatMessagesUseCase(chatRepo)
    private val clearMessages = ClearChatHistoryUseCase(chatRepo)
    private val sendMessage = SendChatMessageUseCase(
        getSource,
        credRepo,
        chatClient,
        chatRepo,
        BuildChatPromptUseCase()
    )

    private fun createViewModel(campaignId: Long = 1L): ChatBotViewModel =
        ChatBotViewModel(
            campaignId = campaignId,
            campaignRepository = campaignRepo,
            getAnalysisSource = getSource,
            getChatMessages = getMessages,
            sendChatMessage = sendMessage,
            clearChatHistory = clearMessages,
            externalScope = CoroutineScope(Dispatchers.Unconfined)
        )

    @Test
    fun initLoadsCampaignAndDetectsAvailableMaterials() {
        val vm = createViewModel(1L)
        val state = vm.uiState.value
        assertEquals("Campaign 1", state.campaign?.name)
        assertFalse(state.isMaterialsEmpty)
    }

    @Test
    fun initDetectsEmptyMaterialsWhenNoDocuments() {
        ocrRepo.results.clear()
        val vm = createViewModel(1L)
        assertTrue(vm.uiState.value.isMaterialsEmpty)
    }

    @Test
    fun sendingMessageStreamsResponseAndAppendsAssistantMessage() {
        val vm = createViewModel(1L)
        chatClient.deltas = listOf("Đây là ", "câu trả lời [Slide 1 - Trang 1]")

        vm.onInputChanged("Định nghĩa A?")
        assertEquals("Định nghĩa A?", vm.uiState.value.inputText)

        vm.sendMessage()
        // Input text should be cleared immediately
        assertEquals("", vm.uiState.value.inputText)

        val state = vm.uiState.value
        assertFalse(state.isStreaming)
        assertEquals("", state.streamingText)
        assertEquals(2, state.messages.size)
        assertEquals(ChatSender.USER, state.messages[0].sender)
        assertEquals("Định nghĩa A?", state.messages[0].content)
        assertEquals(ChatSender.ASSISTANT, state.messages[1].sender)
        assertEquals("Đây là câu trả lời [Slide 1 - Trang 1]", state.messages[1].content)
        assertEquals(1, state.messages[1].citations.size)
    }

    @Test
    fun quickPromptChipDirectlySendsMessage() {
        val vm = createViewModel(1L)
        chatClient.deltas = listOf("Tóm tắt: Điểm 1, Điểm 2")
        vm.sendMessage("💡 Tóm tắt kiến thức quan trọng nhất")

        val state = vm.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("💡 Tóm tắt kiến thức quan trọng nhất", state.messages[0].content)
    }

    @Test
    fun missingCredentialSetsFlag() {
        credRepo.key = null
        val vm = createViewModel(1L)

        vm.sendMessage("Test")

        assertTrue(vm.uiState.value.isMissingCredential)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun networkFailureAllowsRetry() {
        chatClient.failure = RemoteFailure.Network
        val vm = createViewModel(1L)

        vm.sendMessage("Câu hỏi sẽ lỗi")

        val state = vm.uiState.value
        assertEquals(RemoteFailure.Network, state.error)
        assertEquals("Câu hỏi sẽ lỗi", state.lastFailedQuestion)

        // Retry after fixing network
        chatClient.failure = null
        chatClient.deltas = listOf("Đã trả lời thành công")
        vm.retryLastMessage()

        val retryState = vm.uiState.value
        assertNull(retryState.error)
        assertEquals(3, retryState.messages.size) // Q1, Q1 (retry), A1
    }

    @Test
    fun clearChatWipesMessages() {
        val vm = createViewModel(1L)
        vm.sendMessage("Q1")
        assertEquals(2, vm.uiState.value.messages.size)

        vm.clearChat()
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    private class FakeGeminiChatClient : GeminiChatClient {
        override val modelId: String = "fake-model"
        var deltas = listOf("Trả lời")
        var failure: RemoteFailure? = null

        override fun streamChat(apiKey: String, prompt: ChatPrompt): Flow<String> = flow {
            failure?.let { throw RemoteFailureException(it) }
            for (d in deltas) emit(d)
        }
    }

    private class FakeChatMessageRepository : ChatMessageRepository {
        val list = mutableListOf<ChatMessage>()
        val flow = MutableStateFlow<List<ChatMessage>>(emptyList())
        private var nextId = 1L

        override fun getMessagesFlow(campaignId: Long): Flow<List<ChatMessage>> = flow
        override suspend fun getMessages(campaignId: Long): List<ChatMessage> = list.filter { it.campaignId == campaignId }
        override suspend fun saveMessage(message: ChatMessage): Long {
            val id = if (message.id == 0L) nextId++ else message.id
            val saved = message.copy(id = id)
            list.add(saved)
            flow.value = list.toList()
            return id
        }
        override suspend fun clearMessages(campaignId: Long) {
            list.removeAll { it.campaignId == campaignId }
            flow.value = list.toList()
        }
    }
}
