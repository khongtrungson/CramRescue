package com.example.cramrescue.domain.usecase.analysis

import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.RemoteFailureException
import com.example.cramrescue.domain.analysis.StudyAnalysisClient
import com.example.cramrescue.domain.analysis.StudyAnalysisPromptBuilder
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.model.MAX_DOCUMENT_PAGES
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.repository.StudyAnalysisRepository
import java.time.Clock
import java.time.Instant

sealed interface GenerateAnalysisResult {
    data class Success(val analysis: StudyAnalysis) : GenerateAnalysisResult
    data object CampaignNotFound : GenerateAnalysisResult
    data object NoUsableText : GenerateAnalysisResult

    /** Constraint 6.3: at most 20 pages per campaign. */
    data class TooManyPages(val pageCount: Int) : GenerateAnalysisResult
    data object MissingCredential : GenerateAnalysisResult
    data class Failed(val failure: RemoteFailure) : GenerateAnalysisResult

    /** The model answered, but the answer failed semantic validation. Nothing was persisted. */
    data class InvalidAiOutput(val reason: String) : GenerateAnalysisResult
}

/**
 * Campaign OCR → prompt → AI → semantic validation → atomic replace in Room.
 *
 * Every failure returns before [StudyAnalysisRepository.replaceAnalysis], so an existing analysis
 * always survives a failed regeneration.
 */
class GenerateCampaignAnalysisUseCase(
    private val campaignRepository: CampaignRepository,
    private val getAnalysisSource: GetAnalysisSourceUseCase,
    private val credentialRepository: GeminiCredentialRepository,
    private val client: StudyAnalysisClient,
    private val analysisRepository: StudyAnalysisRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(campaignId: Long): GenerateAnalysisResult {
        val campaign = campaignRepository.getCampaignById(campaignId)
            ?: return GenerateAnalysisResult.CampaignNotFound

        val source = getAnalysisSource(campaignId)
        val processedPages = source.analyzedPageCount + source.emptyPageCount
        if (processedPages > MAX_DOCUMENT_PAGES) return GenerateAnalysisResult.TooManyPages(processedPages)
        if (!source.hasUsableText) return GenerateAnalysisResult.NoUsableText

        val apiKey = credentialRepository.getCredential()
            ?: return GenerateAnalysisResult.MissingCredential

        val now = Instant.now(clock)
        val prompt = StudyAnalysisPromptBuilder.build(campaign, source, now)

        val draft = try {
            client.analyze(apiKey, prompt)
        } catch (e: RemoteFailureException) {
            return GenerateAnalysisResult.Failed(e.failure)
        }

        return when (val result = StudyAnalysisValidator.validate(draft, source, client.modelId, now)) {
            is ValidationResult.Invalid -> GenerateAnalysisResult.InvalidAiOutput(result.reason)
            is ValidationResult.Valid -> {
                analysisRepository.replaceAnalysis(result.analysis)
                GenerateAnalysisResult.Success(result.analysis)
            }
        }
    }
}
