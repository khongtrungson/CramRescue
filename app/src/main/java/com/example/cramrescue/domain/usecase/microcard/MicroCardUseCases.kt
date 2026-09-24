package com.example.cramrescue.domain.usecase.microcard

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.microcard.MicroCardBuildResult
import com.example.cramrescue.domain.microcard.MicroCardBuilder
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

data class MicroCardsState(
    val cards: List<MicroCard>,
    /**
     * The cards don't reflect the campaign's current analysis: they came from an older analysis,
     * or the analysis itself is stale (material added, deleted or re-OCR'd since).
     */
    val cardsStale: Boolean,
    val analysis: StudyAnalysis?,
    val analysisStale: Boolean,
    /** The campaign's current material; also resolves card source refs to block text. */
    val source: AnalysisSource
) {
    val hasMaterial: Boolean
        get() = source.pages.isNotEmpty() || source.documentsWithoutOcr > 0 || source.emptyPageCount > 0

    /** New cards can be built right now: a current analysis exists. */
    val canCreateCards: Boolean get() = analysis != null && !analysisStale
}

/** Reads persisted cards (offline, no AI call) and decides whether they are current. */
class GetMicroCardsUseCase(
    private val microCardRepository: MicroCardRepository,
    private val getCampaignAnalysis: GetCampaignAnalysisUseCase
) {
    suspend operator fun invoke(campaignId: Long): MicroCardsState {
        val analysisState = getCampaignAnalysis(campaignId)
        val cards = microCardRepository.getCardsForCampaign(campaignId)
        val analysis = analysisState.analysis
        val cardsStale = cards.isNotEmpty() && (
            analysis == null ||
                analysisState.isStale ||
                cards.any { it.analysisGeneratedAt != analysis.generatedAt }
            )
        return MicroCardsState(
            cards = cards,
            cardsStale = cardsStale,
            analysis = analysis,
            analysisStale = analysisState.isStale,
            source = analysisState.source
        )
    }
}

sealed interface CreateMicroCardsResult {
    data class Created(val count: Int) : CreateMicroCardsResult
    data object NoAnalysis : CreateMicroCardsResult

    /** Material changed since the analysis; it must be regenerated (an explicit AI action) first. */
    data object AnalysisStale : CreateMicroCardsResult
    data class Invalid(val reason: String) : CreateMicroCardsResult
    data object PersistenceFailed : CreateMicroCardsResult
}

/**
 * Explicit "Tạo thẻ ghi nhớ" action: builds the card set locally from the current analysis and
 * replaces the previous set only once the new one is complete and valid. Never calls Gemini.
 */
class CreateMicroCardsUseCase(
    private val getCampaignAnalysis: GetCampaignAnalysisUseCase,
    private val microCardRepository: MicroCardRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(campaignId: Long): CreateMicroCardsResult {
        val state = getCampaignAnalysis(campaignId)
        val analysis = state.analysis ?: return CreateMicroCardsResult.NoAnalysis
        if (state.isStale) return CreateMicroCardsResult.AnalysisStale

        val cards = when (val result = MicroCardBuilder.build(analysis, state.source, Instant.now(clock))) {
            is MicroCardBuildResult.Invalid -> return CreateMicroCardsResult.Invalid(result.reason)
            is MicroCardBuildResult.Valid -> result.cards
        }

        return try {
            microCardRepository.replaceCards(campaignId, cards)
            CreateMicroCardsResult.Created(cards.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CreateMicroCardsResult.PersistenceFailed
        }
    }
}
