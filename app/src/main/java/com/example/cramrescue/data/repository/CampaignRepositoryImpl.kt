package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.CampaignDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.repository.CampaignRepository

class CampaignRepositoryImpl(
    private val campaignDao: CampaignDao
) : CampaignRepository {

    override suspend fun createCampaign(campaign: Campaign): Long {
        return campaignDao.insert(campaign.toEntity())
    }

    override suspend fun getCampaignById(id: Long): Campaign? {
        return campaignDao.getById(id)?.toDomain()
    }

    override suspend fun getLatestCampaign(): Campaign? {
        return campaignDao.getLatest()?.toDomain()
    }
}
