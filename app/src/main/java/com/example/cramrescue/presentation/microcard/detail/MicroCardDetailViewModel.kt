package com.example.cramrescue.presentation.microcard.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.presentation.microcard.CardSourceResolver
import com.example.cramrescue.presentation.microcard.SourceGroupUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MicroCardDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val triage: TriageLevel? = null,
    val title: String = "",
    val bullets: List<String> = emptyList(),
    val notes: String? = null,
    val sources: List<SourceGroupUi> = emptyList(),
    val isStale: Boolean = false
)

class MicroCardDetailViewModel(
    private val campaignId: Long,
    private val cardId: Long,
    private val getMicroCards: GetMicroCardsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MicroCardDetailUiState())
    val uiState: StateFlow<MicroCardDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Scoped by campaign: a card id from another campaign is simply not found.
            val state = getMicroCards(campaignId)
            val card = state.cards.firstOrNull { it.id == cardId }
            if (card == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    triage = card.triage,
                    title = card.title,
                    bullets = card.bullets,
                    notes = card.notes,
                    sources = CardSourceResolver(state.source).groups(card.sourceRefs),
                    isStale = state.cardsStale
                )
            }
        }
    }
}

class MicroCardDetailViewModelFactory(
    private val campaignId: Long,
    private val cardId: Long,
    private val getMicroCards: GetMicroCardsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MicroCardDetailViewModel(campaignId, cardId, getMicroCards) as T
    }
}
