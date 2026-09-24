package com.example.cramrescue.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.cramrescue.data.local.entity.AnalysisPriorityEntity
import com.example.cramrescue.data.local.entity.AnalysisSourceRefEntity
import com.example.cramrescue.data.local.entity.CampaignAnalysisEntity

@Dao
interface StudyAnalysisDao {

    @Insert
    suspend fun insertAnalysis(analysis: CampaignAnalysisEntity)

    @Insert
    suspend fun insertPriority(priority: AnalysisPriorityEntity): Long

    @Insert
    suspend fun insertSourceRefs(refs: List<AnalysisSourceRefEntity>)

    @Query(
        "DELETE FROM analysis_source_refs WHERE priorityId IN " +
            "(SELECT id FROM analysis_priorities WHERE campaignId = :campaignId)"
    )
    suspend fun deleteSourceRefsForCampaign(campaignId: Long)

    @Query("DELETE FROM analysis_priorities WHERE campaignId = :campaignId")
    suspend fun deletePrioritiesForCampaign(campaignId: Long)

    @Query("DELETE FROM campaign_analyses WHERE campaignId = :campaignId")
    suspend fun deleteAnalysisForCampaign(campaignId: Long)

    @Query("SELECT * FROM campaign_analyses WHERE campaignId = :campaignId")
    suspend fun getAnalysisForCampaign(campaignId: Long): CampaignAnalysisEntity?

    @Query("SELECT * FROM analysis_priorities WHERE campaignId = :campaignId ORDER BY orderIndex ASC")
    suspend fun getPrioritiesForCampaign(campaignId: Long): List<AnalysisPriorityEntity>

    @Query(
        "SELECT r.* FROM analysis_source_refs r INNER JOIN analysis_priorities p ON r.priorityId = p.id " +
            "WHERE p.campaignId = :campaignId ORDER BY r.id ASC"
    )
    suspend fun getSourceRefsForCampaign(campaignId: Long): List<AnalysisSourceRefEntity>

    /**
     * Replaces the campaign's current analysis in one transaction: either the complete new
     * analysis is visible, or (on any failure) the previous one is untouched. Children are
     * deleted explicitly rather than relying on CASCADE.
     */
    @Transaction
    suspend fun replaceAnalysis(
        analysis: CampaignAnalysisEntity,
        priorities: List<Pair<AnalysisPriorityEntity, List<AnalysisSourceRefEntity>>>
    ) {
        deleteSourceRefsForCampaign(analysis.campaignId)
        deletePrioritiesForCampaign(analysis.campaignId)
        deleteAnalysisForCampaign(analysis.campaignId)
        insertAnalysis(analysis)
        priorities.forEach { (priority, refs) ->
            val priorityId = insertPriority(priority.copy(id = 0L, campaignId = analysis.campaignId))
            insertSourceRefs(refs.map { it.copy(id = 0L, priorityId = priorityId) })
        }
    }
}
