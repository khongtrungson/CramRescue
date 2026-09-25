package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.speedrun.SpeedRunPolicy
import com.example.cramrescue.domain.speedrun.SpeedRunWindow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageCampaignUseCasesTest {

    private val now = Instant.parse("2026-09-25T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = InMemoryCampaignRepository()
    private val update = UpdateCampaignUseCase(repository, clock)
    private val delete = DeleteCampaignUseCase(repository)

    private fun seed(name: String, examIn: Duration, archetype: SubjectArchetype = SubjectArchetype.NATURAL_COMPUTATION): Campaign = runBlocking {
        val campaign = Campaign(name = name, subjectArchetype = archetype, examDateTime = now.plus(examIn), createdAt = now.minusSeconds(3600))
        campaign.copy(id = repository.createCampaign(campaign))
    }

    // ---- create ----

    @Test
    fun creatingASecondCampaignLeavesTheFirstUntouched() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        val b = (CreateCampaignUseCase(repository, clock)("B", SubjectArchetype.LANGUAGE, now.plus(Duration.ofHours(20))) as CreateCampaignResult.Success).campaign
        assertTrue(a.id != b.id)
        assertEquals(a, repository.getCampaignById(a.id))
        assertEquals(2, repository.getCampaigns().size)
    }

    // ---- update ----

    @Test
    fun validNameEditChangesOnlyTheName() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        val b = seed("B", Duration.ofHours(40))
        val result = update(a.id, "  Giải tích 2  ", a.examDateTime) as UpdateCampaignResult.Success
        assertEquals(a.copy(name = "Giải tích 2"), result.campaign)
        assertEquals(a.copy(name = "Giải tích 2"), repository.getCampaignById(a.id))
        assertEquals(b, repository.getCampaignById(b.id))
    }

    @Test
    fun blankNameIsRejectedAndNothingSaved() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        assertEquals(UpdateCampaignResult.Invalid(listOf(CampaignValidationError.BlankName)), update(a.id, "   ", a.examDateTime))
        assertEquals(a, repository.getCampaignById(a.id))
    }

    @Test
    fun changedExamTimeFollowsTheSharedFr1Rules() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        assertEquals(UpdateCampaignResult.Invalid(listOf(CampaignValidationError.ExamDateInPast)), update(a.id, "A", now.minusSeconds(60)))
        assertEquals(UpdateCampaignResult.Invalid(listOf(CampaignValidationError.ExamDateInPast)), update(a.id, "A", now))
        assertEquals(UpdateCampaignResult.Invalid(listOf(CampaignValidationError.ExamDateTooFar)), update(a.id, "A", now.plus(Duration.ofDays(7)).plusSeconds(1)))
        assertEquals(UpdateCampaignResult.Invalid(listOf(CampaignValidationError.MissingExamDateTime)), update(a.id, "A", null))
        assertEquals(
            UpdateCampaignResult.Invalid(listOf(CampaignValidationError.BlankName, CampaignValidationError.ExamDateInPast)),
            update(a.id, "", now.minusSeconds(1))
        )
        assertEquals(a, repository.getCampaignById(a.id))
        assertTrue(update(a.id, "A", now.plus(Duration.ofDays(7))) is UpdateCampaignResult.Success)
    }

    @Test
    fun aPastCampaignCanBeRenamedWithoutMovingItsExamButOnlyMovedIntoTheFuture() = runBlocking {
        val past = seed("Old", Duration.ofHours(-5))
        val renamed = update(past.id, "Old (đã thi)", past.examDateTime) as UpdateCampaignResult.Success
        assertEquals(past.examDateTime, renamed.campaign.examDateTime)
        assertEquals(UpdateCampaignResult.Invalid(listOf(CampaignValidationError.ExamDateInPast)), update(past.id, "Old", past.examDateTime.plusSeconds(60)))
    }

    @Test
    fun archetypeAndCreationTimeCanNeverChange() = runBlocking {
        val a = seed("A", Duration.ofHours(30), archetype = SubjectArchetype.PROGRAMMING_TECH)
        val result = (update(a.id, "A2", now.plus(Duration.ofHours(10))) as UpdateCampaignResult.Success).campaign
        assertEquals(SubjectArchetype.PROGRAMMING_TECH, result.subjectArchetype)
        assertEquals(a.createdAt, result.createdAt)
        assertEquals(a.id, result.id)
    }

    @Test
    fun examEditsDriveCountdownEmergencyAndSpeedRunWindow() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        fun countdown() = runBlocking { CampaignCountdown.from(repository.getCampaignById(a.id)!!.examDateTime, now) }

        assertFalse(countdown().isEmergency)
        assertEquals(SpeedRunWindow.BEFORE_EMERGENCY, SpeedRunPolicy.window(countdown()))

        update(a.id, "A", now.plus(Duration.ofHours(5)))
        assertEquals(Duration.ofHours(5), countdown().remaining)
        assertTrue(countdown().isEmergency)
        assertEquals(SpeedRunWindow.OPEN, SpeedRunPolicy.window(countdown()))

        update(a.id, "A", now.plus(Duration.ofHours(6)).plusSeconds(1))
        assertFalse(countdown().isEmergency)
        assertEquals(SpeedRunWindow.BEFORE_EMERGENCY, SpeedRunPolicy.window(countdown()))

        // Exam reached follows the edited date: a date that is reached "later" (clock moves on).
        update(a.id, "A", now.plusSeconds(30))
        val later = CampaignCountdown.from(repository.getCampaignById(a.id)!!.examDateTime, now.plusSeconds(31))
        assertTrue(later.isExamReached)
        assertEquals(SpeedRunWindow.EXAM_REACHED, SpeedRunPolicy.window(later))
    }

    @Test
    fun unknownCampaignAndDatabaseFailureAreReported() = runBlocking {
        assertEquals(UpdateCampaignResult.NotFound, update(99, "X", now.plus(Duration.ofHours(1))))
        val a = seed("A", Duration.ofHours(30))
        repository.failWrites = true
        assertEquals(UpdateCampaignResult.Failed, update(a.id, "B", a.examDateTime))
    }

    // ---- delete ----

    @Test
    fun deleteRemovesOnlyTheGivenCampaign() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        val b = seed("B", Duration.ofHours(40))
        assertEquals(DeleteCampaignResult.Deleted(0), delete(a.id))
        assertEquals(null, repository.getCampaignById(a.id))
        assertEquals(b, repository.getCampaignById(b.id))
        assertEquals(listOf(a.id), repository.deleted)
    }

    @Test
    fun deletingTheLastCampaignLeavesNone() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        delete(a.id)
        assertTrue(repository.getCampaigns().isEmpty())
        assertEquals(DeleteCampaignResult.NotFound, delete(a.id))
    }

    @Test
    fun leftoverFilesAreReportedAndDatabaseFailureIsRetryable() = runBlocking {
        val a = seed("A", Duration.ofHours(30))
        repository.failWrites = true
        assertEquals(DeleteCampaignResult.Failed, delete(a.id))
        assertEquals(a, repository.getCampaignById(a.id))

        repository.failWrites = false
        repository.undeletedFiles = 2
        assertEquals(DeleteCampaignResult.Deleted(2), delete(a.id))
    }
}
