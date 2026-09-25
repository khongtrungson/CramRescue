package com.example.cramrescue.domain.usecase.analysis

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.model.DocumentText
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.repository.DocumentTextRepository
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository

/** Loads the OCR material of exactly one campaign, by explicit id. */
class GetAnalysisSourceUseCase(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    /** Word text (M13). Null only in tests whose campaigns contain no Word documents. */
    private val documentTextRepository: DocumentTextRepository? = null
) {
    suspend operator fun invoke(campaignId: Long): AnalysisSource {
        val documents = sourceDocumentRepository.getDocumentsForCampaign(campaignId)
        val ocrResults = buildMap<Long, OcrResult> {
            documents.filter { it.sourceType != SourceType.WORD }.forEach { document ->
                ocrRepository.getResultForDocument(document.id)?.let { put(document.id, it) }
            }
        }
        val documentTexts = buildMap<Long, DocumentText> {
            documents.filter { it.sourceType == SourceType.WORD }.forEach { document ->
                documentTextRepository?.getText(document.id)?.let { put(document.id, it) }
            }
        }
        return AnalysisSource.assemble(campaignId, documents, ocrResults, documentTexts)
    }
}
