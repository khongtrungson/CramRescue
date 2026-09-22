package com.example.cramrescue.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Remaining time to a Campaign's exam, derived fresh from [Campaign.examDateTime] and the
 * current instant every time it's computed — never from decrementing a stored value.
 */
data class CampaignCountdown(
    val remaining: Duration,
    val isExamReached: Boolean,
    val isEmergency: Boolean
) {
    companion object {
        // requirements.md FR-8: "under 6 hours remaining" enters Emergency High-alert.
        val EMERGENCY_THRESHOLD: Duration = Duration.ofHours(6)

        fun from(examDateTime: Instant, now: Instant): CampaignCountdown {
            val rawRemaining = Duration.between(now, examDateTime)
            val examReached = rawRemaining.isZero || rawRemaining.isNegative
            val remaining = if (examReached) Duration.ZERO else rawRemaining
            val isEmergency = remaining <= EMERGENCY_THRESHOLD
            return CampaignCountdown(remaining, examReached, isEmergency)
        }
    }
}
