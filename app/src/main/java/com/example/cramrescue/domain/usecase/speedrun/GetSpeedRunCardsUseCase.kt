package com.example.cramrescue.domain.usecase.speedrun

import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.progress.latestAssessments
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import com.example.cramrescue.domain.speedrun.SpeedRunPolicy
import com.example.cramrescue.domain.speedrun.SpeedRunWindow
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import java.time.Clock
import java.time.Instant

sealed interface SpeedRunStart {
    /** Frozen for the session: the caller studies exactly these cards in this order. */
    data class Ready(val cards: List<MicroCard>, val cardsStale: Boolean) : SpeedRunStart
    data class Closed(val window: SpeedRunWindow) : SpeedRunStart

    /** Every current Code Red card is "Đã thuộc" (or there are none). */
    data object NothingToReview : SpeedRunStart
    data object CampaignNotFound : SpeedRunStart
}

/**
 * Selects an Emergency Speed-run at the moment the learner starts it: re-checks the emergency
 * window, then picks from current cards and their latest attempts. Read-only and local — no AI,
 * no key, no network. Not persisted: FR-13 has no pause/resume requirement.
 */
class GetSpeedRunCardsUseCase(
    private val campaignRepository: CampaignRepository,
    private val getMicroCards: GetMicroCardsUseCase,
    private val studyAttemptRepository: StudyAttemptRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(campaignId: Long): SpeedRunStart {
        val campaign = campaignRepository.getCampaignById(campaignId) ?: return SpeedRunStart.CampaignNotFound
        val window = SpeedRunPolicy.window(CampaignCountdown.from(campaign.examDateTime, Instant.now(clock)))
        if (window != SpeedRunWindow.OPEN) return SpeedRunStart.Closed(window)

        val state = getMicroCards(campaignId)
        val selection = SpeedRunPolicy.select(state.cards, latestAssessments(studyAttemptRepository.getAttemptsForCampaign(campaignId)))
        return if (selection.isEmpty()) SpeedRunStart.NothingToReview else SpeedRunStart.Ready(selection, state.cardsStale)
    }
}
