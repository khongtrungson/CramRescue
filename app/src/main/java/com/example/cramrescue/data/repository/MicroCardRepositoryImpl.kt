package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.MicroCardDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.repository.MicroCardRepository

class MicroCardRepositoryImpl(
    private val microCardDao: MicroCardDao
) : MicroCardRepository {

    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        require(cards.all { it.campaignId == campaignId }) { "cards must belong to campaign $campaignId" }
        microCardDao.replaceCards(campaignId, cards.map { it.toEntity() })
    }

    override suspend fun getCardsForCampaign(campaignId: Long): List<MicroCard> {
        val refsByCard = microCardDao.getSourceRefsForCampaign(campaignId).groupBy { it.microCardId }
        return microCardDao.getCardsForCampaign(campaignId).map { it.toDomain(refsByCard[it.id].orEmpty()) }
    }

    override suspend fun getCard(campaignId: Long, cardId: Long): MicroCard? {
        val card = microCardDao.getCard(campaignId, cardId) ?: return null
        val refs = microCardDao.getSourceRefsForCampaign(campaignId).filter { it.microCardId == cardId }
        return card.toDomain(refs)
    }
}
