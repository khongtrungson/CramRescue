package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.chat.GeminiChatClient
import com.example.cramrescue.domain.model.ChatMessage
import com.example.cramrescue.domain.model.ChatSender
import com.example.cramrescue.domain.repository.ChatMessageRepository
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import java.time.Clock

sealed interface SendChatResult {
    data class Success(val message: ChatMessage) : SendChatResult
    data object NoMaterials : SendChatResult
    data object MissingCredential : SendChatResult
    data class Failed(val failure: RemoteFailure) : SendChatResult
}

class SendChatMessageUseCase(
    private val getAnalysisSource: GetAnalysisSourceUseCase,
    private val geminiCredentialRepository: GeminiCredentialRepository,
    private val geminiChatClient: GeminiChatClient,
    private val chatMessageRepository: ChatMessageRepository,
    private val buildChatPrompt: BuildChatPromptUseCase = BuildChatPromptUseCase(),
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(
        campaignId: Long,
        campaignName: String,
        question: String,
        onDelta: suspend (String) -> Unit
    ): SendChatResult {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return SendChatResult.NoMaterials

        val source = getAnalysisSource(campaignId)
        if (!source.hasUsableText) {
            return SendChatResult.NoMaterials
        }

        val apiKey = geminiCredentialRepository.getCredential()?.trim()
        if (apiKey.isNullOrEmpty()) {
            return SendChatResult.MissingCredential
        }

        val previousMessages = chatMessageRepository.getMessages(campaignId)

        val userMessage = ChatMessage(
            id = 0L,
            campaignId = campaignId,
            sender = ChatSender.USER,
            content = trimmed,
            timestamp = clock.millis()
        )
        chatMessageRepository.saveMessage(userMessage)

        val prompt = buildChatPrompt(
            campaignName = campaignName,
            source = source,
            history = previousMessages,
            newQuestion = trimmed
        )

        val fullResponseBuilder = StringBuilder()
        try {
            geminiChatClient.streamChat(apiKey, prompt).collect { delta ->
                fullResponseBuilder.append(delta)
                onDelta(delta)
            }
        } catch (e: RemoteFailureException) {
            return SendChatResult.Failed(e.failure)
        } catch (e: Exception) {
            return SendChatResult.Failed(RemoteFailure.Network)
        }

        val fullResponse = fullResponseBuilder.toString().trim()
        val citations = ChatCitationParser.parseCitations(fullResponse, source)

        val assistantMessage = ChatMessage(
            id = 0L,
            campaignId = campaignId,
            sender = ChatSender.ASSISTANT,
            content = fullResponse,
            timestamp = clock.millis(),
            citations = citations
        )
        val savedId = chatMessageRepository.saveMessage(assistantMessage)
        return SendChatResult.Success(assistantMessage.copy(id = savedId))
    }
}
