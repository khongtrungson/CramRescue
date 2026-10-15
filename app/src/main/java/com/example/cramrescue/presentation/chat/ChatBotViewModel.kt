package com.example.cramrescue.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.chat.ClearChatHistoryUseCase
import com.example.cramrescue.domain.usecase.chat.GetChatMessagesUseCase
import com.example.cramrescue.domain.usecase.chat.SendChatMessageUseCase
import com.example.cramrescue.domain.usecase.chat.SendChatResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatBotViewModel(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val getAnalysisSource: GetAnalysisSourceUseCase,
    private val getChatMessages: GetChatMessagesUseCase,
    private val sendChatMessage: SendChatMessageUseCase,
    private val clearChatHistory: ClearChatHistoryUseCase,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = externalScope ?: viewModelScope
    private val _uiState = MutableStateFlow(ChatBotUiState())
    val uiState: StateFlow<ChatBotUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    init {
        loadCampaignAndMaterials()
        observeMessages()
    }

    private fun loadCampaignAndMaterials() {
        scope.launch {
            val campaign = campaignRepository.getCampaignById(campaignId)
            val source = getAnalysisSource(campaignId)
            _uiState.update {
                it.copy(
                    campaign = campaign,
                    isMaterialsEmpty = !source.hasUsableText
                )
            }
        }
    }

    private fun observeMessages() {
        scope.launch {
            getChatMessages(campaignId).collect { msgList ->
                _uiState.update { it.copy(messages = msgList) }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(customText: String? = null) {
        val question = (customText ?: _uiState.value.inputText).trim()
        if (question.isEmpty() || _uiState.value.isMaterialsEmpty || _uiState.value.isStreaming) return

        val campaignName = _uiState.value.campaign?.name ?: "Môn học"

        _uiState.update {
            it.copy(
                inputText = if (customText == null) "" else it.inputText,
                isStreaming = true,
                streamingText = "",
                error = null,
                isMissingCredential = false,
                lastFailedQuestion = null
            )
        }

        streamingJob?.cancel()
        streamingJob = scope.launch {
            val result = sendChatMessage(
                campaignId = campaignId,
                campaignName = campaignName,
                question = question,
                onDelta = { delta ->
                    _uiState.update { it.copy(streamingText = it.streamingText + delta) }
                }
            )

            when (result) {
                is SendChatResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            error = null,
                            lastFailedQuestion = null
                        )
                    }
                }
                is SendChatResult.MissingCredential -> {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            isMissingCredential = true,
                            lastFailedQuestion = question
                        )
                    }
                }
                is SendChatResult.NoMaterials -> {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            isMaterialsEmpty = true,
                            lastFailedQuestion = question
                        )
                    }
                }
                is SendChatResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            error = result.failure,
                            isMissingCredential = result.failure == RemoteFailure.InvalidCredential,
                            lastFailedQuestion = question
                        )
                    }
                }
            }
        }
    }

    fun retryLastMessage() {
        val failed = _uiState.value.lastFailedQuestion
        if (!failed.isNullOrBlank()) {
            sendMessage(failed)
        }
    }

    fun clearChat() {
        scope.launch {
            clearChatHistory(campaignId)
            _uiState.update {
                it.copy(
                    streamingText = "",
                    isStreaming = false,
                    error = null,
                    lastFailedQuestion = null
                )
            }
        }
    }
}

class ChatBotViewModelFactory(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val getAnalysisSource: GetAnalysisSourceUseCase,
    private val getChatMessages: GetChatMessagesUseCase,
    private val sendChatMessage: SendChatMessageUseCase,
    private val clearChatHistory: ClearChatHistoryUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatBotViewModel(
            campaignId,
            campaignRepository,
            getAnalysisSource,
            getChatMessages,
            sendChatMessage,
            clearChatHistory
        ) as T
    }
}
