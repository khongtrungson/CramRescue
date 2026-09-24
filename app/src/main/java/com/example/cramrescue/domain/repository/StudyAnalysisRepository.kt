package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.StudyAnalysis

interface StudyAnalysisRepository {
    /**
     * Atomically replaces the current analysis of [StudyAnalysis.campaignId] (regeneration). Only
     * ever called with an already-validated analysis, so a failed regeneration never touches the
     * previous one.
     */
    suspend fun replaceAnalysis(analysis: StudyAnalysis)

    /** Only that campaign's analysis — never another campaign's, never a "latest" one. */
    suspend fun getAnalysisForCampaign(campaignId: Long): StudyAnalysis?
}
