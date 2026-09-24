package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.cramrescue.data.local.entity.StudyAttemptEntity

@Dao
interface StudyAttemptDao {

    @Insert
    suspend fun insert(attempt: StudyAttemptEntity): Long

    @Query("SELECT * FROM study_attempts WHERE campaignId = :campaignId ORDER BY attemptedAtEpochMillis ASC, id ASC")
    suspend fun getForCampaign(campaignId: Long): List<StudyAttemptEntity>

    @Query(
        "SELECT * FROM study_attempts WHERE campaignId = :campaignId AND microCardId = :microCardId " +
            "ORDER BY attemptedAtEpochMillis ASC, id ASC"
    )
    suspend fun getForCard(campaignId: Long, microCardId: Long): List<StudyAttemptEntity>
}
