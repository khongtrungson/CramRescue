package com.example.cramrescue.presentation.microcard.list

import com.example.cramrescue.domain.model.TriageLevel

/** Why there is nothing to study yet, in the order the user has to fix it. */
enum class MicroCardsBlocker { NO_MATERIAL, NO_OCR, NO_ANALYSIS }

data class MicroCardItemUi(
    val id: Long,
    val triage: TriageLevel,
    val title: String,
    val bullets: List<String>,
    val sourceLabel: String,
    /** Every source ref still resolves to a current OCR block. */
    val sourcesAvailable: Boolean
)

data class MicroCardsUiState(
    val isLoading: Boolean = true,
    val campaignName: String = "",
    val blocker: MicroCardsBlocker? = null,
    val analysisStale: Boolean = false,
    /** A current analysis exists, so cards can be built (locally) now. */
    val canCreateCards: Boolean = false,
    val cardsStale: Boolean = false,
    val redCards: List<MicroCardItemUi> = emptyList(),
    val yellowCards: List<MicroCardItemUi> = emptyList(),
    val discardedGreenCount: Int = 0,
    val isCreating: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false
) {
    val hasCards: Boolean get() = redCards.isNotEmpty() || yellowCards.isNotEmpty()
}
