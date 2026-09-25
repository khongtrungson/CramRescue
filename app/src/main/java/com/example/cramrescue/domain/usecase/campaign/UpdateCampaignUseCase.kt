package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.repository.CampaignRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

sealed interface UpdateCampaignResult {
    data class Success(val campaign: Campaign) : UpdateCampaignResult
    data class Invalid(val errors: List<CampaignValidationError>) : UpdateCampaignResult
    data object NotFound : UpdateCampaignResult
    data object Failed : UpdateCampaignResult
}

/**
 * Edits a Campaign's name and exam time (M14). The subject archetype is deliberately NOT editable:
 * an analysis's prompt depends on it (FR-7) and a stored analysis doesn't record which archetype it
 * was generated with, so changing it would leave that analysis looking current.
 *
 * Only the Campaign row changes — materials, OCR/Word text, analysis, cards, study history and
 * missions are untouched, and nothing is re-analysed. Countdown, emergency and Speed-run state are
 * derived from the new exam time wherever they're shown.
 *
 * The FR-1 exam-time rule (future, ≤ 7 days) applies only when the exam time actually changes,
 * so a Campaign whose exam has passed can still be renamed.
 */
class UpdateCampaignUseCase(
    private val repository: CampaignRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(campaignId: Long, name: String, examDateTime: Instant?): UpdateCampaignResult {
        val existing = repository.getCampaignById(campaignId) ?: return UpdateCampaignResult.NotFound

        val errors = listOfNotNull(
            CampaignValidator.validateName(name),
            if (examDateTime == existing.examDateTime) null else CampaignValidator.validateExamDateTime(examDateTime, Instant.now(clock))
        )
        if (errors.isNotEmpty()) return UpdateCampaignResult.Invalid(errors)

        val updated = existing.copy(name = name.trim(), examDateTime = examDateTime!!)
        return try {
            if (repository.updateCampaign(updated)) UpdateCampaignResult.Success(updated) else UpdateCampaignResult.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCampaignResult.Failed
        }
    }
}
