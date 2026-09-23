package com.example.cramrescue.domain.usecase.ocr

import com.example.cramrescue.domain.model.LOW_TEXT_CHARACTER_THRESHOLD
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.OcrTextBlock
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import java.time.Clock
import java.time.Instant

sealed class ProcessDocumentOcrResult {
    data class Success(val result: OcrResult) : ProcessDocumentOcrResult()
    data object DocumentNotFound : ProcessDocumentOcrResult()
    data class Failure(val message: String) : ProcessDocumentOcrResult()
}

/**
 * Orchestrates OCR for exactly one [com.example.cramrescue.domain.model.SourceDocument]: loads it
 * by its own id (never "latest"), runs the on-device [TextRecognitionEngine], and persists a
 * replacement OCR result. Campaign ownership is preserved implicitly — a document can only be
 * loaded by an id that already carries its own campaignId, and the foreign-key relationship
 * guarantees it cannot reference a missing Campaign.
 */
class ProcessDocumentOcrUseCase(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    private val textRecognitionEngine: TextRecognitionEngine,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(documentId: Long): ProcessDocumentOcrResult {
        val document = sourceDocumentRepository.getDocumentById(documentId)
            ?: return ProcessDocumentOcrResult.DocumentNotFound

        return runCatching {
            val recognized = textRecognitionEngine.recognize(document.localPath)

            val blocks = recognized.blocks.mapIndexed { index, block ->
                OcrTextBlock(
                    documentId = document.id,
                    pageIndex = 0,
                    orderIndex = index,
                    text = block.text,
                    boundingBox = block.boundingBox
                )
            }
            val characterCount = blocks.sumOf { it.text.length }

            val result = OcrResult(
                documentId = document.id,
                sourceWidthPx = recognized.sourceWidthPx,
                sourceHeightPx = recognized.sourceHeightPx,
                characterCount = characterCount,
                isLowText = characterCount < LOW_TEXT_CHARACTER_THRESHOLD,
                processedAt = Instant.now(clock),
                blocks = blocks
            )
            ocrRepository.saveResult(result)
            result
        }.fold(
            onSuccess = { result -> ProcessDocumentOcrResult.Success(result) },
            onFailure = { error -> ProcessDocumentOcrResult.Failure(error.message ?: "Không thể xử lý OCR.") }
        )
    }
}
