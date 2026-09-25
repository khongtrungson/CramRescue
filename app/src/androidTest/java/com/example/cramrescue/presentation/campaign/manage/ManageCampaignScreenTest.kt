package com.example.cramrescue.presentation.campaign.manage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.repository.CampaignDeletion
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.usecase.campaign.DeleteCampaignUseCase
import com.example.cramrescue.domain.usecase.campaign.UpdateCampaignUseCase
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class RecordingCampaigns(campaign: Campaign) : CampaignRepository {
    val storage = mutableMapOf(campaign.id to campaign)
    val deleteCalls = mutableListOf<Long>()
    var undeletedFiles = 0
    override suspend fun createCampaign(campaign: Campaign): Long = error("unused")
    override suspend fun getCampaignById(id: Long) = storage[id]
    override suspend fun getCampaigns() = storage.values.toList()
    override suspend fun updateCampaign(campaign: Campaign): Boolean {
        storage[campaign.id] = campaign
        return true
    }
    override suspend fun deleteCampaign(campaignId: Long): CampaignDeletion? {
        deleteCalls += campaignId
        return storage.remove(campaignId)?.let { CampaignDeletion(undeletedFiles) }
    }
}

/** M14 Manage screen with the real ViewModel and use cases: delete needs explicit confirmation. */
@RunWith(AndroidJUnit4::class)
class ManageCampaignScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val campaign = Campaign(
        id = 42, name = "Dược lý", subjectArchetype = SubjectArchetype.SOCIAL_THEORY,
        examDateTime = Instant.now().plus(Duration.ofHours(30)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES), createdAt = Instant.now()
    )

    private fun show(repository: RecordingCampaigns, onDeleted: () -> Unit = {}) {
        val factory = ManageCampaignViewModelFactory(42, repository, UpdateCampaignUseCase(repository), DeleteCampaignUseCase(repository))
        compose.setContent { ManageCampaignScreen(viewModelFactory = factory, onBack = {}, onCampaignDeleted = onDeleted) }
    }

    @Test
    fun deleteAsksFirstCancelKeepsEverythingAndConfirmDeletesOnce() {
        val repository = RecordingCampaigns(campaign)
        var deletedCallbacks = 0
        show(repository) { deletedCallbacks++ }

        compose.onNodeWithText("Xóa chiến dịch").performClick()
        compose.onNodeWithText("Xóa chiến dịch \"Dược lý\"?").assertIsDisplayed()
        compose.onNodeWithText("sẽ bị xóa vĩnh viễn", substring = true).assertIsDisplayed()
        assertTrue("nothing is deleted before confirming", repository.deleteCalls.isEmpty())

        compose.onNodeWithText("Hủy").performClick()
        compose.waitForIdle()
        assertTrue(repository.deleteCalls.isEmpty())
        assertEquals(campaign, repository.storage[42])

        compose.onNodeWithText("Xóa chiến dịch").performClick()
        compose.onNodeWithText("Xóa vĩnh viễn").performClick()
        compose.waitUntil(5_000) { deletedCallbacks == 1 }
        assertEquals(listOf(42L), repository.deleteCalls)
    }

    @Test
    fun leftoverFilesAreReportedBeforeLeaving() {
        val repository = RecordingCampaigns(campaign).apply { undeletedFiles = 2 }
        var deletedCallbacks = 0
        show(repository) { deletedCallbacks++ }

        compose.onNodeWithText("Xóa chiến dịch").performClick()
        compose.onNodeWithText("Xóa vĩnh viễn").performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("2 tệp tài liệu", substring = true).assertExists() }.isSuccess }
        assertEquals(0, deletedCallbacks)
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, deletedCallbacks)
    }

    @Test
    fun renameSavesWithoutTouchingArchetypeOrExamTime() {
        val repository = RecordingCampaigns(campaign)
        show(repository)

        compose.onNodeWithText("Dược lý").performTextReplacement("Dược lý 2")
        compose.onNodeWithText("Lưu thay đổi").performClick()
        compose.waitUntil(5_000) { repository.storage[42]?.name == "Dược lý 2" }
        compose.onNodeWithText("Đã lưu thay đổi.").assertIsDisplayed()
        assertEquals(campaign.copy(name = "Dược lý 2"), repository.storage[42])
        assertTrue(repository.deleteCalls.isEmpty())
    }
}
