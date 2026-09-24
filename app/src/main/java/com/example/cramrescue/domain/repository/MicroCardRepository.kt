package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.MicroCard

interface MicroCardRepository {
    /**
     * Atomically replaces the campaign's card set. Only called with a fully built and validated
     * set, so a failed regeneration never touches the previous cards.
     */
    suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>)

    /** Only that campaign's cards, in order — never another campaign's. */
    suspend fun getCardsForCampaign(campaignId: Long): List<MicroCard>

    suspend fun getCard(campaignId: Long, cardId: Long): MicroCard?
}
