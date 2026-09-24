package com.example.cramrescue.domain.grounding

import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.SourceType

/** One cited OCR block, resolved by exact persisted identity. */
data class GroundedBlock(
    val blockOrderIndex: Int,
    val text: String,
    /** In OCR source pixel space of [GroundedSource.sourceWidthPx] x [GroundedSource.sourceHeightPx]. */
    val boundingBox: BoundingBox
)

/**
 * The evidence for one MicroCard source group (one document page): what to display and which
 * blocks to highlight. Built only from persisted identity — never from text matching.
 */
data class GroundedSource(
    val campaignId: Long,
    val documentId: Long,
    val displayName: String?,
    val sourceType: SourceType,
    /** App-owned file (image or PDF). */
    val localPath: String,
    /** 0-based; the UI shows pageIndex + 1. */
    val pageIndex: Int,
    val pageCount: Int,
    /** The exact pixel space ML Kit processed (upright photo / rendered PDF page). */
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val blocks: List<GroundedBlock>,
    /** Cited blocks that no longer exist on the page (shown as a warning, never guessed). */
    val missingBlockCount: Int,
    /** The card belongs to an older analysis / changed material (M8 stale rule). */
    val cardStale: Boolean
)

enum class GroundingUnavailableReason {
    CARD_NOT_FOUND,

    /** The card doesn't cite this document page — ids from navigation aren't trusted. */
    NOT_CITED_BY_CARD,

    /** Deleted, or not owned by the card's campaign. */
    SOURCE_DELETED,
    PAGE_MISSING,
    INVALID_DIMENSIONS,

    /** OCR was re-run on this page after the card was made: block identity no longer holds. */
    SOURCE_CHANGED,
    BLOCKS_MISSING
}

sealed interface GroundingResult {
    data class Available(val source: GroundedSource) : GroundingResult
    data class Unavailable(val reason: GroundingUnavailableReason) : GroundingResult
}
