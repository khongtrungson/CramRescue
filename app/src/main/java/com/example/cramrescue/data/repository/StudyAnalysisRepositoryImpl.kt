package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.StudyAnalysisDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.repository.StudyAnalysisRepository

class StudyAnalysisRepositoryImpl(
    private val studyAnalysisDao: StudyAnalysisDao
) : StudyAnalysisRepository {

    override suspend fun replaceAnalysis(analysis: StudyAnalysis) {
        studyAnalysisDao.replaceAnalysis(
            analysis = analysis.toEntity(),
            priorities = analysis.priorities.map { it.toEntity(analysis.campaignId) }
        )
    }

    override suspend fun getAnalysisForCampaign(campaignId: Long): StudyAnalysis? {
        val analysis = studyAnalysisDao.getAnalysisForCampaign(campaignId) ?: return null
        val refsByPriority = studyAnalysisDao.getSourceRefsForCampaign(campaignId).groupBy { it.priorityId }
        val priorities = studyAnalysisDao.getPrioritiesForCampaign(campaignId).map { priority ->
            priority.toDomain(refsByPriority[priority.id].orEmpty())
        }
        return analysis.toDomain(priorities)
    }
}
