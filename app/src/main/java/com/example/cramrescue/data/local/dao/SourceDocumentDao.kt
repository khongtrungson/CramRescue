package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.cramrescue.data.local.entity.SourceDocumentEntity

@Dao
interface SourceDocumentDao {
    @Insert
    suspend fun insert(entity: SourceDocumentEntity): Long

    @Query("SELECT * FROM source_documents WHERE campaignId = :campaignId ORDER BY id ASC")
    suspend fun getForCampaign(campaignId: Long): List<SourceDocumentEntity>
}
