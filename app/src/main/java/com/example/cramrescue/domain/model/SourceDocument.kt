package com.example.cramrescue.domain.model

import java.time.Instant

// requirements.md FR-3 / Constraint 3: at most 20 pages may be ingested per document.
const val MAX_DOCUMENT_PAGES = 20

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
