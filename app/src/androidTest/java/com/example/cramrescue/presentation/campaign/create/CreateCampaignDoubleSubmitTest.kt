package com.example.cramrescue.presentation.campaign.create

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import com.example.cramrescue.domain.repository.CampaignDeletion
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.usecase.campaign.CreateCampaignUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Insert that stays in flight until released, so a second tap lands while the first is saving. */
private class SlowCampaigns : CampaignRepository {
    val release = CompletableDeferred<Unit>()
    var inserts = 0
    override suspend fun createCampaign(campaign: Campaign): Long {
        inserts++
        release.await()
        return inserts.toLong()
    }
    override suspend fun getCampaignById(id: Long): Campaign? = null
    override suspend fun getCampaigns(): List<Campaign> = emptyList()
    override suspend fun updateCampaign(campaign: Campaign) = false
    override suspend fun deleteCampaign(campaignId: Long): CampaignDeletion? = null
}

/** M15: a rapid double tap on "Tạo chiến dịch" must create exactly one Campaign. */
@RunWith(AndroidJUnit4::class)
class CreateCampaignDoubleSubmitTest {

    @Test
    fun secondTapWhileSavingOrAfterSuccessIsIgnored() {
        val repository = SlowCampaigns()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var viewModel: CreateCampaignViewModel
        instrumentation.runOnMainSync {
            viewModel = CreateCampaignViewModel(CreateCampaignUseCase(repository))
            viewModel.onNameChange("Giải tích 2")
            viewModel.onArchetypeSelected(SubjectArchetype.NATURAL_COMPUTATION)
            viewModel.onDateSelected(LocalDate.now().plusDays(2))
            viewModel.onTimeSelected(LocalTime.of(9, 0))
            // Two taps in the same frame, before the button could re-render as disabled.
            viewModel.createCampaign()
            viewModel.createCampaign()
        }
        instrumentation.waitForIdleSync()
        assertTrue(viewModel.uiState.value.isSaving)

        runBlocking { repository.release.complete(Unit) }
        val created = CountDownLatch(1)
        instrumentation.runOnMainSync {
            if (viewModel.uiState.value.createdCampaignId != null) created.countDown()
        }
        // Poll briefly for the save to complete on the main dispatcher.
        repeat(50) {
            if (created.count == 0L) return@repeat
            Thread.sleep(20)
            instrumentation.runOnMainSync { if (viewModel.uiState.value.createdCampaignId != null) created.countDown() }
        }
        assertTrue(created.await(1, TimeUnit.SECONDS))

        // A third tap after success (before navigation consumed the id) is ignored too.
        instrumentation.runOnMainSync { viewModel.createCampaign() }
        instrumentation.waitForIdleSync()
        assertEquals(1, repository.inserts)
    }
}
