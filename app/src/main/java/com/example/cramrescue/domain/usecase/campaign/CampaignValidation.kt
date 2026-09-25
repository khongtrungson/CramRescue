package com.example.cramrescue.domain.usecase.campaign

import java.time.Duration
import java.time.Instant

sealed class CampaignValidationError {
    data object BlankName : CampaignValidationError()
    data object MissingArchetype : CampaignValidationError()
    data object MissingExamDateTime : CampaignValidationError()
    data object ExamDateInPast : CampaignValidationError()
    data object ExamDateTooFar : CampaignValidationError()
}

// requirements.md FR-1 [ASSUMPTION]: an exam at most 7 days away.
private val MAX_CAMPAIGN_HORIZON: Duration = Duration.ofDays(7)

/** FR-1 field rules, shared by creating and editing a Campaign. */
object CampaignValidator {

    fun validateName(name: String): CampaignValidationError? =
        if (name.isBlank()) CampaignValidationError.BlankName else null

    /** The exam must be in the future and within the 7-day horizon, both relative to [now]. */
    fun validateExamDateTime(examDateTime: Instant?, now: Instant): CampaignValidationError? = when {
        examDateTime == null -> CampaignValidationError.MissingExamDateTime
        !examDateTime.isAfter(now) -> CampaignValidationError.ExamDateInPast
        examDateTime.isAfter(now.plus(MAX_CAMPAIGN_HORIZON)) -> CampaignValidationError.ExamDateTooFar
        else -> null
    }
}
