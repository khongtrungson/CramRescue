package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.dao.CampaignDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.repository.CampaignDeletion
import com.example.cramrescue.domain.repository.CampaignRepository

class CampaignRepositoryImpl(
    private val campaignDao: CampaignDao,
    private val materialFileStore: MaterialFileStore
) : CampaignRepository {

    override suspend fun createCampaign(campaign: Campaign): Long {
        return campaignDao.insert(campaign.toEntity())
    }

    override suspend fun getCampaignById(id: Long): Campaign? {
        return campaignDao.getById(id)?.toDomain()
    }

    override suspend fun getCampaigns(): List<Campaign> = campaignDao.getAll().map { it.toDomain() }

    override suspend fun updateCampaign(campaign: Campaign): Boolean = campaignDao.update(campaign.toEntity()) > 0

    // Same consistency policy as deleting one material: the Room transaction commits first, then
    // the files go. A DB failure leaves rows and files intact (retryable); a file that can't be
    // removed is left unreferenced in app-private storage and counted — never a row pointing at a
    // missing file. deleteOwnedFile only touches the store's own directory, so a user's original
    // file can't be deleted, and an already-missing file counts as deleted.
    override suspend fun deleteCampaign(campaignId: Long): CampaignDeletion? {
        val paths = campaignDao.deleteCampaignGraph(campaignId) ?: return null
        val undeleted = paths.count { !materialFileStore.deleteOwnedFile(it) }
        return CampaignDeletion(undeletedFileCount = undeleted)
    }
}
