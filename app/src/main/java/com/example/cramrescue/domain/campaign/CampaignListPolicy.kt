package com.example.cramrescue.domain.campaign

import com.example.cramrescue.domain.model.Campaign
import java.time.Instant

/** Campaigns split for the Home list; derived from the exam time and [now], never stored. */
data class CampaignSections(
    /** Exam still ahead — nearest exam first. */
    val upcoming: List<Campaign>,
    /** Exam time reached — most recent exam first. Kept until the user deletes them. */
    val past: List<Campaign>
)

/**
 * M14 list order (requirements.md specifies none): upcoming Campaigns by exam time ascending, then
 * Campaigns whose exam has been reached by exam time descending. Ties break on id, so the order is
 * fully deterministic.
 */
object CampaignListPolicy {
    fun sections(campaigns: List<Campaign>, now: Instant): CampaignSections {
        val (upcoming, past) = campaigns.partition { it.examDateTime.isAfter(now) }
        return CampaignSections(
            upcoming = upcoming.sortedWith(compareBy<Campaign> { it.examDateTime }.thenBy { it.id }),
            past = past.sortedWith(compareByDescending<Campaign> { it.examDateTime }.thenByDescending { it.id })
        )
    }
}
