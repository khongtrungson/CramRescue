package com.example.cramrescue.presentation.microcard.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsResult
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.MicroCardsState
import com.example.cramrescue.presentation.microcard.CardSourceResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MicroCardsViewModel(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val getMicroCards: GetMicroCardsUseCase,
    private val createMicroCards: CreateMicroCardsUseCase,
    private val studyAttemptRepository: StudyAttemptRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MicroCardsUiState())
    val uiState: StateFlow<MicroCardsUiState> = _uiState.asStateFlow()

    /** Reads only Room; opening or returning to this screen never calls Gemini. */
    fun refresh() {
        viewModelScope.launch {
            val campaign = campaignRepository.getCampaignById(campaignId)
            val state = getMicroCards(campaignId)
            val latest = latestAssessments()
            _uiState.update { it.withState(campaign?.name.orEmpty(), state, latest) }
        }
    }

    /** Explicit, local-only card creation from the current analysis. */
    fun createCards() {
        if (_uiState.value.isCreating || !_uiState.value.canCreateCards) return
        _uiState.update { it.copy(isCreating = true, message = null) }
        viewModelScope.launch {
            val result = createMicroCards(campaignId)
            val (message, isError) = when (result) {
                is CreateMicroCardsResult.Created -> "Đã tạo ${result.count} thẻ ghi nhớ từ phân tích hiện tại." to false
                CreateMicroCardsResult.NoAnalysis -> "Chưa có phân tích AI cho chiến dịch này." to true
                CreateMicroCardsResult.AnalysisStale ->
                    "Tài liệu đã thay đổi sau lần phân tích. Hãy phân tích lại trước khi tạo thẻ." to true
                is CreateMicroCardsResult.Invalid ->
                    "Không thể tạo thẻ: một số nội dung không khớp với tài liệu hiện tại. Thẻ cũ được giữ nguyên." to true
                CreateMicroCardsResult.PersistenceFailed -> "Không thể lưu thẻ. Thẻ cũ được giữ nguyên." to true
            }
            val campaign = campaignRepository.getCampaignById(campaignId)
            val state = getMicroCards(campaignId)
            val latest = latestAssessments()
            _uiState.update {
                it.withState(campaign?.name.orEmpty(), state, latest)
                    .copy(isCreating = false, message = message, messageIsError = isError)
            }
        }
    }

    /** Latest attempt per card id (attempts come oldest first). Rebuilt cards have new ids, so no inheritance. */
    private suspend fun latestAssessments(): Map<Long, RecallAssessment> =
        studyAttemptRepository.getAttemptsForCampaign(campaignId)
            .filter { it.microCardId != null }
            .associate { it.microCardId!! to it.assessment }

    private fun MicroCardsUiState.withState(
        campaignName: String,
        state: MicroCardsState,
        latest: Map<Long, RecallAssessment>
    ): MicroCardsUiState {
        val resolver = CardSourceResolver(state.source)
        val items = state.cards.map { card ->
            MicroCardItemUi(
                id = card.id,
                triage = card.triage,
                title = card.title,
                bullets = card.bullets,
                sourceLabel = resolver.summary(card.sourceRefs),
                sourcesAvailable = card.sourceRefs.isNotEmpty() && card.sourceRefs.all(resolver::resolved),
                latestAssessment = latest[card.id]
            )
        }
        val blocker = when {
            state.cards.isNotEmpty() -> null
            !state.hasMaterial -> MicroCardsBlocker.NO_MATERIAL
            !state.source.hasUsableText -> MicroCardsBlocker.NO_OCR
            state.analysis == null -> MicroCardsBlocker.NO_ANALYSIS
            else -> null
        }
        return copy(
            isLoading = false,
            campaignName = campaignName,
            blocker = blocker,
            analysisStale = state.analysisStale,
            canCreateCards = state.canCreateCards,
            cardsStale = state.cardsStale,
            redCards = items.filter { it.triage == TriageLevel.CODE_RED },
            yellowCards = items.filter { it.triage == TriageLevel.CODE_YELLOW },
            discardedGreenCount = state.analysis?.discardedGreenCount ?: 0
        )
    }
}

class MicroCardsViewModelFactory(
    private val campaignId: Long,
    private val campaignRepository: CampaignRepository,
    private val getMicroCards: GetMicroCardsUseCase,
    private val createMicroCards: CreateMicroCardsUseCase,
    private val studyAttemptRepository: StudyAttemptRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MicroCardsViewModel(campaignId, campaignRepository, getMicroCards, createMicroCards, studyAttemptRepository) as T
    }
}
