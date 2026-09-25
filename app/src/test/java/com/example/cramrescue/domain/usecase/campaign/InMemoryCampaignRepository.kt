package com.example.cramrescue.domain.usecase.campaign

import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.repository.CampaignDeletion
import com.example.cramrescue.domain.repository.CampaignRepository

/** In-memory [CampaignRepository] for campaign use-case tests. */
class InMemoryCampaignRepository : CampaignRepository {
    val storage = linkedMapOf<Long, Campaign>()
    private var nextId = 1L
    var failWrites = false
    var undeletedFiles = 0
    val deleted = mutableListOf<Long>()

    override suspend fun createCampaign(campaign: Campaign): Long {
        val id = nextId++
        storage[id] = campaign.copy(id = id)
        return id
    }

    override suspend fun getCampaignById(id: Long): Campaign? = storage[id]

    override suspend fun getCampaigns(): List<Campaign> = storage.values.toList()

    override suspend fun updateCampaign(campaign: Campaign): Boolean {
        if (failWrites) throw IllegalStateException("simulated database failure")
        if (campaign.id !in storage) return false
        storage[campaign.id] = campaign
        return true
    }

    override suspend fun deleteCampaign(campaignId: Long): CampaignDeletion? {
        if (failWrites) throw IllegalStateException("simulated database failure")
        storage.remove(campaignId) ?: return null
        deleted += campaignId
        return CampaignDeletion(undeletedFiles)
    }
}
