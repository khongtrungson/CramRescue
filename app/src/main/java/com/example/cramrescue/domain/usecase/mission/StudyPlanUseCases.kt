package com.example.cramrescue.domain.usecase.mission

import com.example.cramrescue.domain.mission.MicroMission
import com.example.cramrescue.domain.mission.MissionFocus
import com.example.cramrescue.domain.mission.MissionSelectionPolicy
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.progress.PassProbabilityPolicy
import com.example.cramrescue.domain.progress.PassProbabilitySnapshot
import com.example.cramrescue.domain.progress.latestAssessments
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.MicroMissionRepository
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import java.time.Clock
import java.time.Instant

/** Why no Pass Probability can be shown — "unavailable" is not "0%". */
enum class ReadinessUnavailableReason { NO_MATERIAL, NO_OCR, NO_ANALYSIS, NO_CARDS }

sealed interface ReadinessState {
    data class Unavailable(val reason: ReadinessUnavailableReason) : ReadinessState

    /** [isStale]: computed from cards of an older analysis / changed material. */
    data class Available(val snapshot: PassProbabilitySnapshot, val isStale: Boolean) : ReadinessState
}

sealed interface MissionPlan {
    /** No current cards to build a mission from. */
    data object NoCards : MissionPlan

    /** Every current Red/Yellow card's latest assessment is "Đã thuộc". */
    data object AllMastered : MissionPlan

    /** A started mission still has cards to study (FR-10 pause/resume). */
    data class InProgress(val remaining: List<MicroCard>, val total: Int) : MissionPlan

    /** What "Bắt đầu nhiệm vụ" would select now. */
    data class Next(val cards: List<MicroCard>, val focus: MissionFocus) : MissionPlan
}

data class StudyPlan(val readiness: ReadinessState, val mission: MissionPlan, val cardsStale: Boolean)

/** Local, read-only: derives Pass Probability and the mission plan from Room. No AI, no network. */
class GetStudyPlanUseCase(
    private val getMicroCards: GetMicroCardsUseCase,
    private val studyAttemptRepository: StudyAttemptRepository,
    private val microMissionRepository: MicroMissionRepository
) {
    suspend operator fun invoke(campaignId: Long): StudyPlan {
        val state = getMicroCards(campaignId)
        val attempts = studyAttemptRepository.getAttemptsForCampaign(campaignId)
        val latest = latestAssessments(attempts)
        val cards = state.cards

        val readiness = PassProbabilityPolicy.calculate(cards, latest)
            ?.let { ReadinessState.Available(it, isStale = state.cardsStale) }
            ?: ReadinessState.Unavailable(
                when {
                    !state.hasMaterial -> ReadinessUnavailableReason.NO_MATERIAL
                    !state.source.hasUsableText -> ReadinessUnavailableReason.NO_OCR
                    state.analysis == null -> ReadinessUnavailableReason.NO_ANALYSIS
                    else -> ReadinessUnavailableReason.NO_CARDS
                }
            )

        val active = microMissionRepository.getMission(campaignId)
        val remaining = active?.remainingCards(cards, attempts).orEmpty()
        val mission = when {
            cards.isEmpty() -> MissionPlan.NoCards
            active != null && remaining.isNotEmpty() -> MissionPlan.InProgress(remaining, active.liveCardCount(cards))
            else -> {
                val next = MissionSelectionPolicy.select(cards, latest)
                MissionSelectionPolicy.focusOf(next)?.let { MissionPlan.Next(next, it) } ?: MissionPlan.AllMastered
            }
        }
        return StudyPlan(readiness, mission, state.cardsStale)
    }
}

sealed interface StartMissionResult {
    data class Started(val cardCount: Int) : StartMissionResult
    data class Resumed(val remainingCount: Int) : StartMissionResult
    data object NothingToStudy : StartMissionResult
    data object ExamReached : StartMissionResult
    data object CampaignNotFound : StartMissionResult
}

/**
 * "Bắt đầu / Tiếp tục nhiệm vụ": resumes an unfinished mission, otherwise selects and freezes the
 * next one. Only called from an explicit user action.
 */
class StartMicroMissionUseCase(
    private val campaignRepository: CampaignRepository,
    private val getMicroCards: GetMicroCardsUseCase,
    private val studyAttemptRepository: StudyAttemptRepository,
    private val microMissionRepository: MicroMissionRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(campaignId: Long): StartMissionResult {
        val campaign = campaignRepository.getCampaignById(campaignId) ?: return StartMissionResult.CampaignNotFound
        val now = Instant.now(clock)
        if (CampaignCountdown.from(campaign.examDateTime, now).isExamReached) return StartMissionResult.ExamReached

        val cards = getMicroCards(campaignId).cards
        val attempts = studyAttemptRepository.getAttemptsForCampaign(campaignId)
        microMissionRepository.getMission(campaignId)?.remainingCards(cards, attempts)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return StartMissionResult.Resumed(it.size) }

        val selection = MissionSelectionPolicy.select(cards, latestAssessments(attempts))
        if (selection.isEmpty()) return StartMissionResult.NothingToStudy
        microMissionRepository.saveMission(
            MicroMission(
                campaignId = campaignId,
                cardIds = selection.map { it.id },
                baselineAttemptId = attempts.maxOfOrNull { it.id } ?: 0L,
                startedAt = now
            )
        )
        return StartMissionResult.Started(selection.size)
    }
}

/** The active mission's remaining cards, in their frozen mission order (for Active Recall). */
class GetMissionCardsUseCase(
    private val getMicroCards: GetMicroCardsUseCase,
    private val studyAttemptRepository: StudyAttemptRepository,
    private val microMissionRepository: MicroMissionRepository
) {
    suspend operator fun invoke(campaignId: Long): List<MicroCard> {
        val mission = microMissionRepository.getMission(campaignId) ?: return emptyList()
        return mission.remainingCards(getMicroCards(campaignId).cards, studyAttemptRepository.getAttemptsForCampaign(campaignId))
    }
}
