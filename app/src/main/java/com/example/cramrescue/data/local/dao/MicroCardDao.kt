package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.cramrescue.data.local.entity.MicroCardEntity
import com.example.cramrescue.data.local.entity.MicroCardSourceRefEntity

@Dao
interface MicroCardDao {

    @Insert
    suspend fun insertCard(card: MicroCardEntity): Long

    @Insert
    suspend fun insertSourceRefs(refs: List<MicroCardSourceRefEntity>)

    @Query(
        "DELETE FROM micro_card_source_refs WHERE microCardId IN " +
            "(SELECT id FROM micro_cards WHERE campaignId = :campaignId)"
    )
    suspend fun deleteSourceRefsForCampaign(campaignId: Long)

    @Query("DELETE FROM micro_cards WHERE campaignId = :campaignId")
    suspend fun deleteCardsForCampaign(campaignId: Long)

    @Query("SELECT * FROM micro_cards WHERE campaignId = :campaignId ORDER BY orderIndex ASC")
    suspend fun getCardsForCampaign(campaignId: Long): List<MicroCardEntity>

    @Query("SELECT * FROM micro_cards WHERE campaignId = :campaignId AND id = :cardId")
    suspend fun getCard(campaignId: Long, cardId: Long): MicroCardEntity?

    @Query(
        "SELECT r.* FROM micro_card_source_refs r INNER JOIN micro_cards c ON r.microCardId = c.id " +
            "WHERE c.campaignId = :campaignId ORDER BY r.id ASC"
    )
    suspend fun getSourceRefsForCampaign(campaignId: Long): List<MicroCardSourceRefEntity>

    /**
     * Replaces the campaign's whole card set in one transaction: the new set is visible completely
     * or (on any failure) the previous set is untouched. Children are deleted explicitly.
     */
    @Transaction
    suspend fun replaceCards(
        campaignId: Long,
        cards: List<Pair<MicroCardEntity, List<MicroCardSourceRefEntity>>>
    ) {
        deleteSourceRefsForCampaign(campaignId)
        deleteCardsForCampaign(campaignId)
        cards.forEach { (card, refs) ->
            val cardId = insertCard(card.copy(id = 0L, campaignId = campaignId))
            insertSourceRefs(refs.map { it.copy(id = 0L, microCardId = cardId) })
        }
    }
}
