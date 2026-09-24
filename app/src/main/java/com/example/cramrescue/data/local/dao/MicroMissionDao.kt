package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cramrescue.data.local.entity.MicroMissionEntity

@Dao
interface MicroMissionDao {

    @Query("SELECT * FROM micro_missions WHERE campaignId = :campaignId")
    suspend fun getForCampaign(campaignId: Long): MicroMissionEntity?

    /** One mission per campaign: starting a new mission replaces the previous row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mission: MicroMissionEntity)
}
