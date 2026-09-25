package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.repository.CampaignRepository
import java.time.Clock
import java.time.Instant

sealed class CreateCampaignResult {
    data class Success(val campaign: Campaign) : CreateCampaignResult()
    data class Invalid(val errors: List<CampaignValidationError>) : CreateCampaignResult()
}

class CreateCampaignUseCase(
    private val repository: CampaignRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        name: String,
        subjectArchetype: SubjectArchetype?,
        examDateTime: Instant?
    ): CreateCampaignResult {
        val now = Instant.now(clock)
        val errors = listOfNotNull(
            CampaignValidator.validateName(name),
            if (subjectArchetype == null) CampaignValidationError.MissingArchetype else null,
            CampaignValidator.validateExamDateTime(examDateTime, now)
        )

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
