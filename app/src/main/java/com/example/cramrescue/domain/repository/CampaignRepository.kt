package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.Campaign

/** Outcome of [CampaignRepository.deleteCampaign]. */
data class CampaignDeletion(
    /** App-owned material files that could not be removed after the database deletion committed. */
    val undeletedFileCount: Int
)

interface CampaignRepository {
    suspend fun createCampaign(campaign: Campaign): Long
    suspend fun getCampaignById(id: Long): Campaign?

    /** Every persisted Campaign, unordered (presentation order is `CampaignListPolicy`). */
    suspend fun getCampaigns(): List<Campaign>

    /** Updates the row with [campaign]'s id. False if no such Campaign exists. */
    suspend fun updateCampaign(campaign: Campaign): Boolean

    /**
     * Deletes the Campaign and everything it owns: all database rows in one transaction, then its
     * app-owned material files. Null if no such Campaign exists. Throws if the database deletion
     * fails, in which case nothing was deleted.
     */
    suspend fun deleteCampaign(campaignId: Long): CampaignDeletion?
}
