package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.cramrescue.data.local.entity.CampaignEntity

@Dao
interface CampaignDao {
    @Insert
    suspend fun insert(entity: CampaignEntity): Long

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getById(id: Long): CampaignEntity?

    @Query("SELECT * FROM campaigns")
    suspend fun getAll(): List<CampaignEntity>

    @Update
    suspend fun update(entity: CampaignEntity): Int

    @Query("SELECT localPath FROM source_documents WHERE campaignId = :campaignId")
    suspend fun getMaterialPaths(campaignId: Long): List<String>

    @Query("DELETE FROM campaigns WHERE id = :campaignId")
    suspend fun deleteById(campaignId: Long): Int

    /**
     * Deletes the Campaign row; every campaign-owned table cascades from it (source_documents →
     * OCR pages/blocks, Word text, analysis/card source refs; campaign_analyses → priorities →
     * refs; micro_cards → card refs; study_attempts; micro_missions). Returns the deleted
     * documents' file paths, read in the same transaction, or null if the Campaign didn't exist.
     */
    @Transaction
    suspend fun deleteCampaignGraph(campaignId: Long): List<String>? {
        val paths = getMaterialPaths(campaignId)
        return if (deleteById(campaignId) == 0) null else paths
    }
}
