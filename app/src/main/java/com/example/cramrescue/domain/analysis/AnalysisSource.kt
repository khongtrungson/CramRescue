package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceRef

/** One OCR block as it is sent to Gemini: a request-local id plus its text. */
data class AnalysisSourceBlock(
    /** Request-local reference shown to the model, e.g. "B7". */
    val refId: String,
    val sourceRef: SourceRef,
    val text: String
)

/** One non-empty page included in the request, in order. */
data class AnalysisSourcePage(
    /** 1-based position of the document within this request (not a Room id). */
    val documentNumber: Int,
    val documentLabel: String,
    val documentId: Long,
    val pageIndex: Int,
    val isLowText: Boolean,
    val blocks: List<AnalysisSourceBlock>
)

/**
 * The OCR material of exactly one Campaign, flattened into a deterministic order with a
 * request-local reference for every block.
 *
 * OCR quality policy:
 *  - normal page: included.
 *  - low-text page (< 50 chars, FR-4): included but marked, so the model treats it as less
 *    reliable and the app can report how many were used.
 *  - empty page (no non-blank block): excluded — there is nothing to analyze.
 *  - document without OCR yet: excluded and counted, so the UI can say so. It is still part of
 *    [fingerprint], so running OCR on it later marks an existing analysis as stale.
 */
data class AnalysisSource(
    val campaignId: Long,
    val pages: List<AnalysisSourcePage>,
    val documentsWithoutOcr: Int,
    val emptyPageCount: Int,
    val fingerprint: String
) {
    val blocks: List<AnalysisSourceBlock> get() = pages.flatMap { it.blocks }
    val analyzedPageCount: Int get() = pages.size
    val lowTextPageCount: Int get() = pages.count { it.isLowText }
    val hasUsableText: Boolean get() = pages.isNotEmpty()

    /** Request-local id → stable OCR block identity; the only refs a response may use. */
    val refMap: Map<String, SourceRef> by lazy { blocks.associate { it.refId to it.sourceRef } }

    companion object {
        /**
         * @param documents the documents of [campaignId]. Anything belonging to another
         *   campaign is ignored, so a caller mistake can't leak material across campaigns.
         * @param ocrResults OCR per document id; a missing entry means "not processed yet".
         */
        fun assemble(
            campaignId: Long,
            documents: List<SourceDocument>,
            ocrResults: Map<Long, OcrResult>
        ): AnalysisSource {
            val ownedDocuments = documents.filter { it.campaignId == campaignId }.sortedBy { it.id }
            val pages = mutableListOf<AnalysisSourcePage>()
            var nextBlockNumber = 1
            var documentsWithoutOcr = 0
            var emptyPageCount = 0

            ownedDocuments.forEachIndexed { index, document ->
                val ocr = ocrResults[document.id]
                if (ocr == null) {
                    documentsWithoutOcr++
                    return@forEachIndexed
                }
                val label = document.displayName ?: "Tài liệu ${index + 1}"
                ocr.pages.sortedBy { it.pageIndex }.forEach { page ->
                    val usableBlocks = page.blocks
                        .sortedBy { it.orderIndex }
                        .filter { it.text.isNotBlank() }
                    if (usableBlocks.isEmpty()) {
                        emptyPageCount++
                        return@forEach
                    }
                    pages += AnalysisSourcePage(
                        documentNumber = index + 1,
                        documentLabel = label,
                        documentId = document.id,
                        pageIndex = page.pageIndex,
                        isLowText = page.isLowText,
                        blocks = usableBlocks.map { block ->
                            AnalysisSourceBlock(
                                refId = "B${nextBlockNumber++}",
                                sourceRef = SourceRef(document.id, page.pageIndex, block.orderIndex),
                                text = block.text
                            )
                        }
                    )
                }
            }

            return AnalysisSource(
                campaignId = campaignId,
                pages = pages,
                documentsWithoutOcr = documentsWithoutOcr,
                emptyPageCount = emptyPageCount,
                fingerprint = fingerprint(ownedDocuments, ocrResults)
            )
        }

        /**
         * Identity of a campaign's material: every document, and when each of its pages was
         * last OCR'd. Adding a document, OCR-ing one, or reprocessing one changes it. Deliberately
         * not a content hash — document text only changes through OCR, which bumps processedAt.
         */
        fun fingerprint(documents: List<SourceDocument>, ocrResults: Map<Long, OcrResult>): String =
            documents.sortedBy { it.id }.joinToString("|") { document ->
                val ocr = ocrResults[document.id]
                val pages = ocr?.pages?.sortedBy { it.pageIndex }
                    ?.joinToString(",") { "${it.pageIndex}@${it.processedAt.toEpochMilli()}" }
                "${document.id}:${pages ?: "none"}"
            }
    }
}
