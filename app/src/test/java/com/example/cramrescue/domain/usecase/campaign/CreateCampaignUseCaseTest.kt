package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.repository.CampaignRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateCampaignUseCaseTest {

    private val fixedNow = Instant.parse("2026-09-21T00:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val repository = InMemoryCampaignRepository()
    private val useCase = CreateCampaignUseCase(repository, clock)

    @Test
    fun rejectsBlankName() = runBlocking {
        val result = useCase(
            name = "   ",
            subjectArchetype = SubjectArchetype.SOCIAL_THEORY,
            examDateTime = fixedNow.plusSeconds(3600)
        )

        val errors = (result as CreateCampaignResult.Invalid).errors
        assertTrue(errors.contains(CampaignValidationError.BlankName))
    }

    @Test
    fun rejectsExamTimeInThePast() = runBlocking {
        val result = useCase(
            name = "Kinh Te Vi Mo",
            subjectArchetype = SubjectArchetype.SOCIAL_THEORY,
            examDateTime = fixedNow.minusSeconds(60)
        )

        val errors = (result as CreateCampaignResult.Invalid).errors
        assertTrue(errors.contains(CampaignValidationError.ExamDateInPast))
    }

    @Test
    fun rejectsExamTimeMoreThanSevenDaysAway() = runBlocking {
        val result = useCase(
            name = "Kinh Te Vi Mo",
            subjectArchetype = SubjectArchetype.SOCIAL_THEORY,
            examDateTime = fixedNow.plus(Duration.ofDays(8))
        )

        val errors = (result as CreateCampaignResult.Invalid).errors
        assertTrue(errors.contains(CampaignValidationError.ExamDateTooFar))
    }

    @Test
    fun acceptsValidCampaignAndPersistsIt() = runBlocking {
        val result = useCase(
            name = "Kinh Te Vi Mo",
            subjectArchetype = SubjectArchetype.SOCIAL_THEORY,
            examDateTime = fixedNow.plus(Duration.ofDays(2))
        )

        val campaign = (result as CreateCampaignResult.Success).campaign
        assertEquals("Kinh Te Vi Mo", campaign.name)
        assertEquals(campaign, repository.getCampaignById(campaign.id))
    }
}
