package com.example.cramrescue.presentation.recall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.recall.RecallPrompt
import com.example.cramrescue.domain.recall.RecallPromptBuilder
import com.example.cramrescue.domain.recall.RecallSession
import com.example.cramrescue.domain.recall.answers
import com.example.cramrescue.domain.recall.segments
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.domain.usecase.recall.RecordAssessmentResult
import com.example.cramrescue.domain.usecase.recall.RecordRecallAssessmentUseCase
import com.example.cramrescue.presentation.microcard.CardSourceResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one Active Recall session. Local only: it depends on no AI client and no key.
 * State changes happen only through [reveal] / [assess] / [restart] events, never through
 * recomposition. The in-progress session is in memory; saved attempts are what persist.
 */
class ActiveRecallViewModel(
    private val campaignId: Long,
    private val getMicroCards: GetMicroCardsUseCase,
    private val recordAssessment: RecordRecallAssessmentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ActiveRecallUiState>(ActiveRecallUiState.Loading)
    val uiState: StateFlow<ActiveRecallUiState> = _uiState.asStateFlow()

    private var session: RecallSession? = null
    private var cardsStale = false
    private var resolver: CardSourceResolver? = null
    private var errorMessage: String? = null

    init {
        load()
    }

    /** "Ôn lại": a fresh session over the campaign's current cards; earlier attempts stay. */
    fun restart() {
        if (_uiState.value !is ActiveRecallUiState.Completed) return
        load()
    }

    fun reveal() {
        val current = session ?: return
        val next = current.reveal()
        if (next === current) return // not in QUESTION phase
        session = next
        publish()
    }

    /** Accepted once per card: a second tap while saving (or before reveal) is ignored. */
    fun assess(assessment: RecallAssessment) {
        val current = session ?: return
        val saving = current.beginAssessment()
        if (saving === current) return
        session = saving
        errorMessage = null
        publish()

        val card = saving.currentCard ?: return
        viewModelScope.launch {
            val result = recordAssessment(campaignId, card, assessment)
            val afterSave = session ?: return@launch
            session = if (result is RecordAssessmentResult.Recorded) {
                afterSave.assessmentSaved(assessment)
            } else {
                errorMessage = "Không lưu được kết quả. Thẻ này chưa được tính — hãy chọn lại."
                afterSave.assessmentFailed()
            }
            publish()
        }
    }

    private fun load() {
        _uiState.value = ActiveRecallUiState.Loading
        viewModelScope.launch {
            val state = getMicroCards(campaignId)
            cardsStale = state.cardsStale
            resolver = CardSourceResolver(state.source)
            errorMessage = null
            session = RecallSession.start(state.cards)
            if (state.cards.isEmpty()) {
                session = null
                _uiState.value = ActiveRecallUiState.NoCards(
                    "Chưa có thẻ ghi nhớ để ôn tập. Hãy tạo thẻ từ phân tích AI trước."
                )
                return@launch
            }
            publish()
        }
    }

    private fun publish() {
        val current = session ?: return
        if (current.phase == RecallSession.Phase.COMPLETED) {
            _uiState.value = ActiveRecallUiState.Completed(current.total, current.summary())
            return
        }
        val card = current.currentCard ?: return
        val prompt = RecallPromptBuilder.build(card)
        val question = when (prompt) {
            is RecallPrompt.Cloze -> QuestionUi.Cloze(prompt.segments(card.bullets[prompt.bulletIndex]))
            RecallPrompt.General -> QuestionUi.General(card.bullets.size)
        }
        val revealed = if (current.phase == RecallSession.Phase.QUESTION) {
            null
        } else {
            RevealedUi(
                answers = (prompt as? RecallPrompt.Cloze)?.answers(card.bullets[prompt.bulletIndex]).orEmpty(),
                bullets = card.bullets,
                notes = card.notes,
                sourceLabels = resolver?.lines(card.sourceRefs).orEmpty()
                    .groupBy { it.location }
                    .map { (location, lines) ->
                        "$location (${lines.size} khối)" + if (lines.any { it.snippet == null }) " — đã thay đổi" else ""
                    }
            )
        }
        _uiState.value = ActiveRecallUiState.Studying(
            position = current.position,
            total = current.total,
            cardsStale = cardsStale,
            triage = card.triage,
            title = card.title,
            question = question,
            revealed = revealed,
            isSaving = current.phase == RecallSession.Phase.SAVING,
            errorMessage = errorMessage
        )
    }
}

class ActiveRecallViewModelFactory(
    private val campaignId: Long,
    private val getMicroCards: GetMicroCardsUseCase,
    private val recordAssessment: RecordRecallAssessmentUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ActiveRecallViewModel(campaignId, getMicroCards, recordAssessment) as T
    }
}
