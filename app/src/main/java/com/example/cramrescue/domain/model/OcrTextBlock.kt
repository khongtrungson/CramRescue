package com.example.cramrescue.domain.model

/**
 * One recognized block of text belonging to a single [SourceDocument]. Milestone 4 only ingests
 * standalone images, so [pageIndex] is always 0 today — it exists so a future multi-page source
 * (e.g. a PDF) doesn't require a schema change, without speculatively building page management now.
 *
 * [orderIndex] preserves ML Kit's original block order so the full page text can be deterministically
 * reconstructed later (see [OcrResult.reconstructedText]).
 */
data class OcrTextBlock(
    val id: Long = 0L,
    val documentId: Long,
    val pageIndex: Int,
    val orderIndex: Int,
    val text: String,
    val boundingBox: BoundingBox
)
