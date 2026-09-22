package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.repository.CampaignRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed class CampaignValidationError {
    data object BlankName : CampaignValidationError()
    data object MissingArchetype : CampaignValidationError()
    data object MissingExamDateTime : CampaignValidationError()
    data object ExamDateInPast : CampaignValidationError()
    data object ExamDateTooFar : CampaignValidationError()
}

sealed class CreateCampaignResult {
    data class Success(val campaign: Campaign) : CreateCampaignResult()
    data class Invalid(val errors: List<CampaignValidationError>) : CreateCampaignResult()
}

private val MAX_CAMPAIGN_HORIZON: Duration = Duration.ofDays(7)

class CreateCampaignUseCase(
    private val repository: CampaignRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        name: String,
        subjectArchetype: SubjectArchetype?,
        examDateTime: Instant?
    ): CreateCampaignResult {
        val errors = mutableListOf<CampaignValidationError>()
        if (name.isBlank()) errors += CampaignValidationError.BlankName
        if (subjectArchetype == null) errors += CampaignValidationError.MissingArchetype

        val now = Instant.now(clock)
        when {
            examDateTime == null -> errors += CampaignValidationError.MissingExamDateTime
            !examDateTime.isAfter(now) -> errors += CampaignValidationError.ExamDateInPast
            examDateTime.isAfter(now.plus(MAX_CAMPAIGN_HORIZON)) -> errors += CampaignValidationError.ExamDateTooFar
        }

        if (errors.isNotEmpty()) return CreateCampaignResult.Invalid(errors)

        val campaign = Campaign(
            name = name.trim(),
            subjectArchetype = subjectArchetype!!,
            examDateTime = examDateTime!!,
            createdAt = now
        )
        val id = repository.createCampaign(campaign)
        return CreateCampaignResult.Success(campaign.copy(id = id))
    }
}
