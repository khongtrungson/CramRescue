package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.cramrescue.data.local.entity.CampaignEntity

@Dao
interface CampaignDao {
    @Insert
    suspend fun insert(entity: CampaignEntity): Long

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun getById(id: Long): CampaignEntity?

    // Assumption (Milestone 3): the schema has no "active" flag and requirements.md doesn't
    // define multi-campaign behavior, so the most recently created campaign is treated as
    // the active one for the Survival Cockpit.
    @Query("SELECT * FROM campaigns ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): CampaignEntity?
}
