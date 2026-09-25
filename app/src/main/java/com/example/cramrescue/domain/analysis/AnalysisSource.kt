package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.DocumentText
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.WORDS_PER_PAGE_EQUIVALENT
import com.example.cramrescue.domain.model.WORD_LOGICAL_SECTION_INDEX

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
    val blocks: List<AnalysisSourceBlock>,
    /**
     * A Word document's single logical section (FR-3): paragraph text with no physical page, so
     * it is never labelled or counted as a page.
     */
    val isWord: Boolean = false,
    /** Words in this section (Word only), for the page-equivalent ceiling. */
    val wordCount: Int = 0
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
 *  - Word document: its extracted paragraphs form one logical section (no page, no boxes).
 */
data class AnalysisSource(
    val campaignId: Long,
    val pages: List<AnalysisSourcePage>,
    val documentsWithoutOcr: Int,
    val emptyPageCount: Int,
    val fingerprint: String
) {
    val blocks: List<AnalysisSourceBlock> get() = pages.flatMap { it.blocks }
    /** OCR pages with text (image/PDF). Word sections are not pages and are counted separately. */
    val analyzedPageCount: Int get() = pages.count { !it.isWord }
    val wordDocumentCount: Int get() = pages.count { it.isWord }

    /**
     * For the per-campaign page ceiling: OCR pages (with or without text) plus Word text converted
     * with the PRD's own equivalence (FR-3 assumption: 10,000 words ≈ 20 pages).
     */
    val pageEquivalents: Int
        get() = analyzedPageCount + emptyPageCount +
            pages.filter { it.isWord }.sumOf { (it.wordCount + WORDS_PER_PAGE_EQUIVALENT - 1) / WORDS_PER_PAGE_EQUIVALENT }
    val lowTextPageCount: Int get() = pages.count { it.isLowText }
    val hasUsableText: Boolean get() = pages.isNotEmpty()

    /** Request-local id → stable OCR block identity; the only refs a response may use. */
    val refMap: Map<String, SourceRef> by lazy { blocks.associate { it.refId to it.sourceRef } }

    companion object {
        /**
         * @param documents the documents of [campaignId]. Anything belonging to another
         *   campaign is ignored, so a caller mistake can't leak material across campaigns.
         * @param ocrResults OCR per document id; a missing entry means "not processed yet".
         * @param documentTexts extracted text of Word documents, per document id.
         */
        fun assemble(
            campaignId: Long,
            documents: List<SourceDocument>,
            ocrResults: Map<Long, OcrResult>,
            documentTexts: Map<Long, DocumentText> = emptyMap()
        ): AnalysisSource {
            val ownedDocuments = documents.filter { it.campaignId == campaignId }.sortedBy { it.id }
            val pages = mutableListOf<AnalysisSourcePage>()
            var nextBlockNumber = 1
            var documentsWithoutOcr = 0
            var emptyPageCount = 0

            ownedDocuments.forEachIndexed { index, document ->
                if (document.sourceType == SourceType.WORD) {
                    val text = documentTexts[document.id]
                    val usable = text?.blocks?.sortedBy { it.orderIndex }?.filter { it.text.isNotBlank() }.orEmpty()
                    if (text == null || usable.isEmpty()) {
                        documentsWithoutOcr++
                        return@forEachIndexed
                    }
                    pages += AnalysisSourcePage(
                        documentNumber = index + 1,
                        documentLabel = document.displayName ?: "Tài liệu ${index + 1}",
                        documentId = document.id,
                        pageIndex = WORD_LOGICAL_SECTION_INDEX,
                        isLowText = false,
                        blocks = usable.map { block ->
                            AnalysisSourceBlock(
                                refId = "B${nextBlockNumber++}",
                                sourceRef = SourceRef(document.id, WORD_LOGICAL_SECTION_INDEX, block.orderIndex),
                                text = block.text
                            )
                        },
                        isWord = true,
                        wordCount = text.wordCount
                    )
                    return@forEachIndexed
                }
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
                fingerprint = fingerprint(ownedDocuments, ocrResults, documentTexts)
            )
        }

        /**
         * Identity of a campaign's material: every document, and when each of its pages was
         * last OCR'd. Adding a document, OCR-ing one, or reprocessing one changes it. Deliberately
         * not a content hash — document text only changes through OCR, which bumps processedAt.
         * A Word document contributes its extraction time instead ("id:w@millis"). The format for
         * image/PDF documents is unchanged, so existing analyses stay current.
         */
        fun fingerprint(
            documents: List<SourceDocument>,
            ocrResults: Map<Long, OcrResult>,
            documentTexts: Map<Long, DocumentText> = emptyMap()
        ): String =
            documents.sortedBy { it.id }.joinToString("|") { document ->
                if (document.sourceType == SourceType.WORD) {
                    val extractedAt = documentTexts[document.id]?.extractedAt?.toEpochMilli()
                    return@joinToString "${document.id}:" + (extractedAt?.let { "w@$it" } ?: "none")
                }
                val ocr = ocrResults[document.id]
                val pages = ocr?.pages?.sortedBy { it.pageIndex }
                    ?.joinToString(",") { "${it.pageIndex}@${it.processedAt.toEpochMilli()}" }
                "${document.id}:${pages ?: "none"}"
            }
    }
}
