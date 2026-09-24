package com.example.cramrescue.domain.model

import java.time.Instant

// Page ceiling per document (PDF import/OCR) and per campaign (AI analysis). requirements.md
// FR-3 / Constraint 6.3 specify 20; raised to 50 by product-owner decision (2026-09-24).
const val MAX_DOCUMENT_PAGES = 50

enum class SourceType { CAMERA, GALLERY, PDF }

/**
 * One imported study material, owned by exactly one Campaign. A camera/gallery image is a
 * single-page document; a PDF has [pageCount] pages. Milestone 6 renders PDF pages locally and
 * feeds them through the same OCR pipeline images use.
 */
data class SourceDocument(
    val id: Long = 0L,
    val campaignId: Long,
    val sourceType: SourceType,
    val localPath: String,
    val createdAt: Instant,
    /** Pages in this document. Images are 1. Null for documents imported before Milestone 6. */
    val pageCount: Int? = null,
    /** Provider-supplied display name (e.g. the PDF filename). Never an identity — the id is. */
    val displayName: String? = null
) {
    /** Documents imported before Milestone 6 are single-page images. */
    val effectivePageCount: Int get() = pageCount ?: 1
}
