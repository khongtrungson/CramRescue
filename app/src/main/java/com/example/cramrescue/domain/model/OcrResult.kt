package com.example.cramrescue.domain.model

import java.time.Instant

// requirements.md FR-4: fewer than 50 recognized characters on a page triggers a quality warning.
const val LOW_TEXT_CHARACTER_THRESHOLD = 50

/**
 * The full OCR outcome for one [SourceDocument].
 *
 * Coordinate convention: every [OcrTextBlock.boundingBox] in [blocks] is in pixel coordinates
 * relative to a bitmap of size [sourceWidthPx] x [sourceHeightPx] — i.e. the same image ML Kit
 * actually processed. A future renderer must scale against these dimensions, not the on-screen
 * display size, to correctly reconstruct an overlay.
 */
data class OcrResult(
    val documentId: Long,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val characterCount: Int,
    val isLowText: Boolean,
    val processedAt: Instant,
    val blocks: List<OcrTextBlock>
) {
    /** Deterministic full-page text, blocks joined in their original recognition order. */
    fun reconstructedText(): String =
        blocks.sortedBy { it.orderIndex }.joinToString("\n") { it.text }
}
