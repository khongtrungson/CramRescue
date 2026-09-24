package com.example.cramrescue.domain.usecase.recall

import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

sealed interface RecordAssessmentResult {
    data class Recorded(val attempt: StudyAttempt) : RecordAssessmentResult

    /** The card doesn't belong to the campaign being studied; nothing was written. */
    data object WrongCampaign : RecordAssessmentResult

    /** Not saved (e.g. the card was replaced meanwhile). Never treated as remembered. */
    data object Failed : RecordAssessmentResult
}

/** Persists one FR-12 self-assessment as a new attempt. Local only — no AI, no network. */
class RecordRecallAssessmentUseCase(
    private val studyAttemptRepository: StudyAttemptRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(campaignId: Long, card: MicroCard, assessment: RecallAssessment): RecordAssessmentResult {
        if (card.campaignId != campaignId) return RecordAssessmentResult.WrongCampaign
        val attempt = StudyAttempt(
            campaignId = campaignId,
            microCardId = card.id,
            triage = card.triage,
            cardTitle = card.title,
            assessment = assessment,
            attemptedAt = Instant.now(clock)
        )
        return try {
            RecordAssessmentResult.Recorded(attempt.copy(id = studyAttemptRepository.recordAttempt(attempt)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecordAssessmentResult.Failed
        }
    }
}
