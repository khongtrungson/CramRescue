package com.example.cramrescue.domain.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignListPolicyTest {

    private val now = Instant.parse("2026-09-25T10:00:00Z")

    private fun campaign(id: Long, examInHours: Long) = Campaign(
        id = id,
        name = "C$id",
        subjectArchetype = SubjectArchetype.SOCIAL_THEORY,
        examDateTime = now.plus(Duration.ofHours(examInHours)),
        createdAt = now.minusSeconds(id)
    )

    @Test
    fun noCampaignsGivesEmptySections() {
        val sections = CampaignListPolicy.sections(emptyList(), now)
        assertTrue(sections.upcoming.isEmpty() && sections.past.isEmpty())
    }

    @Test
    fun oneCampaignAppears() {
        assertEquals(listOf(7L), CampaignListPolicy.sections(listOf(campaign(7, 30)), now).upcoming.map { it.id })
    }

    @Test
    fun upcomingNearestFirstThenPastMostRecentFirstWithIdTieBreaks() {
        val campaigns = listOf(
            campaign(1, 48), campaign(2, 3), campaign(3, -2), campaign(4, 3), campaign(5, -30), campaign(6, -2), campaign(7, 100)
        )
        val sections = CampaignListPolicy.sections(campaigns, now)
        assertEquals(listOf(2L, 4L, 1L, 7L), sections.upcoming.map { it.id })
        assertEquals(listOf(6L, 3L, 5L), sections.past.map { it.id })
        // Every Campaign appears exactly once, with its own id.
        assertEquals(campaigns.map { it.id }.sorted(), (sections.upcoming + sections.past).map { it.id }.sorted())
    }

    @Test
    fun orderIsDeterministicRegardlessOfInputOrder() {
        val campaigns = listOf(campaign(1, 5), campaign(2, 5), campaign(3, -5), campaign(4, -5), campaign(5, 1))
        val expected = CampaignListPolicy.sections(campaigns, now)
        repeat(10) { seed ->
            assertEquals(expected, CampaignListPolicy.sections(campaigns.shuffled(java.util.Random(seed.toLong())), now))
        }
    }

    @Test
    fun examExactlyNowCountsAsReached() {
        val sections = CampaignListPolicy.sections(listOf(campaign(1, 0)), now)
        assertEquals(listOf(1L), sections.past.map { it.id })
    }
}
