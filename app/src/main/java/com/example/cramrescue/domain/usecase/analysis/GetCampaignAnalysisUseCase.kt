package com.example.cramrescue.domain.usecase.analysis

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.repository.StudyAnalysisRepository

data class CampaignAnalysisState(
    val analysis: StudyAnalysis?,
    /** The campaign's material changed after [analysis] was generated. */
    val isStale: Boolean,
    /** The campaign's current material. */
    val source: AnalysisSource
)

/** Reads the persisted analysis (offline, no AI call) and checks it against the current material. */
class GetCampaignAnalysisUseCase(
    private val analysisRepository: StudyAnalysisRepository,
    private val getAnalysisSource: GetAnalysisSourceUseCase
) {
    suspend operator fun invoke(campaignId: Long): CampaignAnalysisState {
        val analysis = analysisRepository.getAnalysisForCampaign(campaignId)
        val source = getAnalysisSource(campaignId)
        return CampaignAnalysisState(
            analysis = analysis,
            isStale = analysis != null && analysis.sourceFingerprint != source.fingerprint,
            source = source
        )
    }
}
