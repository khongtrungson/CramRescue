package com.example.cramrescue.domain.usecase.ocr

import com.example.cramrescue.domain.model.LOW_TEXT_CHARACTER_THRESHOLD
import com.example.cramrescue.domain.model.MAX_DOCUMENT_PAGES
import com.example.cramrescue.domain.model.OcrPageResult
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.OcrTextBlock
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.ocr.RecognizedText
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.example.cramrescue.domain.pdf.PdfPageRenderer
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
 * Runs OCR over exactly one [SourceDocument], loaded by its own id (never "latest"). Images are
 * processed as a single page; PDFs are rendered locally one page at a time and fed through the
 * same [TextRecognitionEngine].
 *
 * Partial-failure policy: all pages are recognized first and persisted in one atomic replace, so
 * a failure on any page leaves the previously stored OCR untouched and reports an error. The app
 * never shows a partially processed document as successful.
 */
class ProcessDocumentOcrUseCase(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    private val textRecognitionEngine: TextRecognitionEngine,
    private val pdfPageRenderer: PdfPageRenderer,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    suspend operator fun invoke(
        documentId: Long,
        onProgress: (pagesDone: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): ProcessDocumentOcrResult {
        val document = sourceDocumentRepository.getDocumentById(documentId)
            ?: return ProcessDocumentOcrResult.DocumentNotFound
        // FR-4 OCR covers image/PDF pages only; Word text is extracted at import (FR-3).
        if (document.sourceType == SourceType.WORD) {
            return ProcessDocumentOcrResult.Failure("Tài liệu Word đã được trích xuất văn bản khi nhập, không cần OCR.")
        }

        return runCatching {
            val pages = when (document.sourceType) {
                SourceType.PDF -> recognizePdfPages(document, onProgress)
                SourceType.CAMERA, SourceType.GALLERY -> recognizeSinglePage(document, onProgress)
                SourceType.WORD -> error("unreachable: Word is rejected above")
            }
            val result = OcrResult(documentId = document.id, pages = pages)
            ocrRepository.saveResult(result)
            result
        }.fold(
            onSuccess = { ProcessDocumentOcrResult.Success(it) },
            onFailure = { error -> ProcessDocumentOcrResult.Failure(error.message ?: "Không thể xử lý OCR.") }
        )
    }

    private suspend fun recognizeSinglePage(
        document: SourceDocument,
        onProgress: (Int, Int) -> Unit
    ): List<OcrPageResult> {
        onProgress(0, 1)
        val recognized = textRecognitionEngine.recognize(document.localPath)
        val page = recognized.toPageResult(document.id, pageIndex = 0)
        onProgress(1, 1)
        return listOf(page)
    }

    private suspend fun recognizePdfPages(
        document: SourceDocument,
        onProgress: (Int, Int) -> Unit
    ): List<OcrPageResult> = pdfPageRenderer.withDocument(document.localPath) { session ->
        val totalPages = session.pageCount
        // Defensive: import already rejects oversized PDFs, so this only guards stale documents.
        require(totalPages <= MAX_DOCUMENT_PAGES) {
            "Tài liệu có $totalPages trang, vượt quá giới hạn $MAX_DOCUMENT_PAGES trang."
        }
        onProgress(0, totalPages)

        val pages = ArrayList<OcrPageResult>(totalPages)
        for (pageIndex in 0 until totalPages) {
            // One page's bitmap/temp file is held at a time and released before the next page.
            val rendered = session.renderPage(pageIndex)
            try {
                val recognized = textRecognitionEngine.recognize(rendered.imagePath)
                pages += recognized.toPageResult(document.id, pageIndex)
            } finally {
                session.deleteRenderedPage(rendered)
            }
            onProgress(pageIndex + 1, totalPages)
        }
        pages
    }

    private fun RecognizedText.toPageResult(documentId: Long, pageIndex: Int): OcrPageResult {
        val pageBlocks = blocks.mapIndexed { index, block ->
            OcrTextBlock(
                documentId = documentId,
                pageIndex = pageIndex,
                orderIndex = index,
                text = block.text,
                boundingBox = block.boundingBox
            )
        }
        val characterCount = pageBlocks.sumOf { it.text.length }

        return OcrPageResult(
            documentId = documentId,
            pageIndex = pageIndex,
            sourceWidthPx = sourceWidthPx,
            sourceHeightPx = sourceHeightPx,
            characterCount = characterCount,
            isLowText = characterCount < LOW_TEXT_CHARACTER_THRESHOLD,
            processedAt = Instant.now(clock),
            blocks = pageBlocks
        )
    }
}
