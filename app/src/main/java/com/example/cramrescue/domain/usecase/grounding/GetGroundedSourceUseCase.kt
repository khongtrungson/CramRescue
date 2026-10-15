package com.example.cramrescue.domain.usecase.grounding

import com.example.cramrescue.domain.grounding.GroundedBlock
import com.example.cramrescue.domain.grounding.GroundedSource
import com.example.cramrescue.domain.grounding.TextGroundedSource
import com.example.cramrescue.domain.grounding.GroundingResult
import com.example.cramrescue.domain.grounding.GroundingUnavailableReason
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.WORD_LOGICAL_SECTION_INDEX
import com.example.cramrescue.domain.repository.DocumentTextRepository
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase

/**
 * Resolves one MicroCard source group (document + page) to its exact OCR evidence (FR-14/FR-15).
 * Local and read-only: no OCR, no AI, no network, no file writes.
 *
 * Every step checks persisted identity, and every failure is a controlled "unavailable" — a
 * broken reference stays visibly broken rather than being grounded to guessed evidence. There is
 * deliberately no text-matching fallback.
 */
class GetGroundedSourceUseCase(
    private val getMicroCards: GetMicroCardsUseCase,
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    /** Word text (M13). Null only in tests without Word documents. */
    private val documentTextRepository: DocumentTextRepository? = null
) {
    suspend operator fun invoke(campaignId: Long, cardId: Long, documentId: Long, pageIndex: Int): GroundingResult {
        fun unavailable(reason: GroundingUnavailableReason) = GroundingResult.Unavailable(reason)

        // Support direct document page grounding (e.g. from Chatbot citation badges where cardId <= 0)
        if (cardId <= 0L) {
            val document = sourceDocumentRepository.getDocumentById(documentId)
                ?: return unavailable(GroundingUnavailableReason.SOURCE_DELETED)
            if (document.campaignId != campaignId) return unavailable(GroundingUnavailableReason.SOURCE_DELETED)

            if (document.sourceType == SourceType.WORD) {
                if (pageIndex != WORD_LOGICAL_SECTION_INDEX) return unavailable(GroundingUnavailableReason.PAGE_MISSING)
                val text = documentTextRepository?.getText(documentId)
                    ?: return unavailable(GroundingUnavailableReason.BLOCKS_MISSING)
                return GroundingResult.AvailableText(
                    TextGroundedSource(
                        campaignId = campaignId,
                        documentId = documentId,
                        displayName = document.displayName,
                        blocks = text.blocks,
                        citedOrderIndices = text.blocks.map { it.orderIndex }.toSet(),
                        missingBlockCount = 0,
                        cardStale = false
                    )
                )
            }

            val page = ocrRepository.getResultForDocument(documentId)?.pages?.firstOrNull { it.pageIndex == pageIndex }
                ?: return unavailable(GroundingUnavailableReason.PAGE_MISSING)
            if (page.sourceWidthPx <= 0 || page.sourceHeightPx <= 0) {
                return unavailable(GroundingUnavailableReason.INVALID_DIMENSIONS)
            }
            val resolved = page.blocks.map { GroundedBlock(it.orderIndex, it.text, it.boundingBox) }
            return GroundingResult.Available(
                GroundedSource(
                    campaignId = campaignId,
                    documentId = documentId,
                    displayName = document.displayName,
                    sourceType = document.sourceType,
                    localPath = document.localPath,
                    pageIndex = pageIndex,
                    pageCount = document.effectivePageCount,
                    sourceWidthPx = page.sourceWidthPx,
                    sourceHeightPx = page.sourceHeightPx,
                    blocks = resolved,
                    missingBlockCount = 0,
                    cardStale = false
                )
            )
        }

        // 1. The card, scoped to its campaign (a card id from another campaign isn't found).
        val cards = getMicroCards(campaignId)
        val card = cards.cards.firstOrNull { it.id == cardId } ?: return unavailable(GroundingUnavailableReason.CARD_NOT_FOUND)

        // 2. The card must actually cite this page; navigation arguments alone prove nothing.
        val refs = card.sourceRefs.filter { it.documentId == documentId && it.pageIndex == pageIndex }
        if (refs.isEmpty()) return unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD)

        // 3. The document still exists and belongs to the same campaign.
        val document = sourceDocumentRepository.getDocumentById(documentId)
        if (document == null || document.campaignId != campaignId) {
            return unavailable(GroundingUnavailableReason.SOURCE_DELETED)
        }

        // Word (M13): one logical section of paragraphs, grounded as a text snippet (FR-14) — no
        // page, no bounding box. Same identity rules: exact paragraph index, never text matching.
        if (document.sourceType == SourceType.WORD) {
            if (pageIndex != WORD_LOGICAL_SECTION_INDEX) return unavailable(GroundingUnavailableReason.PAGE_MISSING)
            val text = documentTextRepository?.getText(documentId)
                ?: return unavailable(GroundingUnavailableReason.BLOCKS_MISSING)
            if (text.extractedAt.isAfter(card.createdAt)) return unavailable(GroundingUnavailableReason.SOURCE_CHANGED)
            val existing = text.blocks.map { it.orderIndex }.toSet()
            val cited = refs.map { it.blockOrderIndex }.distinct()
            val resolved = cited.filter { it in existing }.toSet()
            if (resolved.isEmpty()) return unavailable(GroundingUnavailableReason.BLOCKS_MISSING)
            return GroundingResult.AvailableText(
                TextGroundedSource(
                    campaignId = campaignId,
                    documentId = documentId,
                    displayName = document.displayName,
                    blocks = text.blocks,
                    citedOrderIndices = resolved,
                    missingBlockCount = cited.size - resolved.size,
                    cardStale = cards.cardsStale
                )
            )
        }

        // 4. The OCR page with its canonical source dimensions.
        val page = ocrRepository.getResultForDocument(documentId)?.pages?.firstOrNull { it.pageIndex == pageIndex }
            ?: return unavailable(GroundingUnavailableReason.PAGE_MISSING)
        if (page.sourceWidthPx <= 0 || page.sourceHeightPx <= 0) {
            return unavailable(GroundingUnavailableReason.INVALID_DIMENSIONS)
        }

        // 5. Block identity is (document, page, OCR order). Re-running OCR on the page after the
        //    card was built renumbers blocks, so the reference could silently point at other text.
        if (page.processedAt.isAfter(card.createdAt)) return unavailable(GroundingUnavailableReason.SOURCE_CHANGED)

        // 6. Exact blocks by identity.
        val blocksByOrder = page.blocks.associateBy { it.orderIndex }
        val resolved = refs.map { it.blockOrderIndex }.distinct().mapNotNull { order ->
            blocksByOrder[order]?.let { GroundedBlock(it.orderIndex, it.text, it.boundingBox) }
        }
        if (resolved.isEmpty()) return unavailable(GroundingUnavailableReason.BLOCKS_MISSING)

        return GroundingResult.Available(
            GroundedSource(
                campaignId = campaignId,
                documentId = documentId,
                displayName = document.displayName,
                sourceType = document.sourceType,
                localPath = document.localPath,
                pageIndex = pageIndex,
                pageCount = document.effectivePageCount,
                sourceWidthPx = page.sourceWidthPx,
                sourceHeightPx = page.sourceHeightPx,
                blocks = resolved,
                missingBlockCount = refs.map { it.blockOrderIndex }.distinct().size - resolved.size,
                cardStale = cards.cardsStale
            )
        )
    }
}
