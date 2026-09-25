package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.repository.CampaignRepository
import kotlinx.coroutines.CancellationException

sealed interface DeleteCampaignResult {
    /**
     * The Campaign and all its data are gone. [undeletedFileCount] app-owned material files could
     * not be removed; they are left, unreferenced, in app-private storage.
     */
    data class Deleted(val undeletedFileCount: Int) : DeleteCampaignResult
    data object NotFound : DeleteCampaignResult

    /** The database deletion failed; nothing (no row, no file) was deleted, so it can be retried. */
    data object Failed : DeleteCampaignResult
}

/**
 * Permanently deletes one Campaign by explicit id, with everything it owns (see
 * [CampaignRepository.deleteCampaign]). Never triggers OCR or AI. Callers must have obtained the
 * user's explicit confirmation first.
 */
class DeleteCampaignUseCase(
    private val repository: CampaignRepository
) {
    suspend operator fun invoke(campaignId: Long): DeleteCampaignResult = try {
        repository.deleteCampaign(campaignId)
            ?.let { DeleteCampaignResult.Deleted(it.undeletedFileCount) }
            ?: DeleteCampaignResult.NotFound
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DeleteCampaignResult.Failed
    }
}
