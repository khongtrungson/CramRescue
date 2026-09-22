package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.Campaign

interface CampaignRepository {
    suspend fun createCampaign(campaign: Campaign): Long
    suspend fun getCampaignById(id: Long): Campaign?

    /** The most recently created Campaign, or null if none exists. */
    suspend fun getLatestCampaign(): Campaign?
}
