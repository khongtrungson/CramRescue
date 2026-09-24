package com.example.cramrescue.domain.model

import java.time.Instant

// requirements.md FR-4: fewer than 50 recognized characters on a page triggers a quality warning.
// This is evaluated per page, never across a whole multi-page document.
const val LOW_TEXT_CHARACTER_THRESHOLD = 50

/**
 * OCR outcome for a single page of a [SourceDocument].
 *
 * Coordinate convention (unchanged since Milestone 5): every [OcrTextBlock.boundingBox] in
 * [blocks] is in pixel coordinates relative to an image of size [sourceWidthPx] x
 * [sourceHeightPx] — the exact image ML Kit processed. For a camera/gallery image that is the
 * stored photo; for a PDF page it is the locally rendered page bitmap. A future Source Grounding
 * overlay must scale against these dimensions, not the on-screen size.
 */
data class OcrPageResult(
    val documentId: Long,
    /** 0-based internally; the UI displays it as page [pageIndex] + 1. */
    val pageIndex: Int,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val characterCount: Int,
    val isLowText: Boolean,
    val processedAt: Instant,
    val blocks: List<OcrTextBlock>
) {
    /** Deterministic page text, blocks joined in their original recognition order. */
    fun reconstructedText(): String =
        blocks.sortedBy { it.orderIndex }.joinToString("\n") { it.text }
}

/** The full OCR outcome for one [SourceDocument], one entry per processed page. */
data class OcrResult(
    val documentId: Long,
    val pages: List<OcrPageResult>
) {
    val characterCount: Int get() = pages.sumOf { it.characterCount }
    val blockCount: Int get() = pages.sumOf { it.blocks.size }
    val lowTextPageIndices: List<Int> get() = pages.filter { it.isLowText }.map { it.pageIndex }

    /**
     * The OCR block a [SourceRef] points to — including its bounding box, relative to that page's
     * [OcrPageResult.sourceWidthPx] x [OcrPageResult.sourceHeightPx] — or null if it isn't in
     * this result (another document, or OCR re-run since the ref was made).
     */
    fun blockAt(ref: SourceRef): OcrTextBlock? {
        if (ref.documentId != documentId) return null
        return pages.firstOrNull { it.pageIndex == ref.pageIndex }
            ?.blocks?.firstOrNull { it.orderIndex == ref.blockOrderIndex }
    }

    /** Deterministic whole-document text, pages in order, blank line between pages. */
    fun reconstructedText(): String =
        pages.sortedBy { it.pageIndex }.joinToString("\n\n") { it.reconstructedText() }
}
