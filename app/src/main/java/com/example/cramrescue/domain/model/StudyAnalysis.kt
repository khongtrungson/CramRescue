package com.example.cramrescue.domain.model

import java.time.Instant

// requirements.md glossary / FR-6 "3-Bullet Budget".
const val MAX_BULLETS = 3
const val MAX_WORDS_PER_BULLET = 20

/**
 * requirements.md glossary "ER Triage". Domain values, not UI colors — the UI maps them to the
 * semantic CodeRed/CodeYellow/CodeGreen theme tokens.
 */
enum class TriageLevel { CODE_RED, CODE_YELLOW, CODE_GREEN }

/**
 * Points an analysis item back to one OCR block. (documentId, pageIndex, blockOrderIndex) is the
 * block's stable identity — OCR block row ids are regenerated when a document is reprocessed, so
 * they are deliberately not used here. The block's bounding box stays local in Room.
 */
data class SourceRef(
    val documentId: Long,
    /** 0-based, same convention as [OcrPageResult.pageIndex]. */
    val pageIndex: Int,
    val blockOrderIndex: Int
)

/** One Pareto-prioritized knowledge unit. Only [TriageLevel.CODE_RED]/[TriageLevel.CODE_YELLOW] are kept (FR-5). */
data class StudyPriority(
    val orderIndex: Int,
    val title: String,
    val triage: TriageLevel,
    /** At most [MAX_BULLETS] bullets of at most [MAX_WORDS_PER_BULLET] words (3-Bullet Budget, FR-6). */
    val bullets: List<String>,
    /** Overflow moved out of [bullets] by the client-side budget (Constraint 6.2 "Ghi chú mở rộng"). */
    val notes: String?,
    val sourceRefs: List<SourceRef>
)

/** The current AI study analysis for exactly one Campaign. */
data class StudyAnalysis(
    val campaignId: Long,
    val summary: String,
    val priorities: List<StudyPriority>,
    /** FR-5: Code Green items are dropped from the study list; only their count is kept. */
    val discardedGreenCount: Int,
    val analyzedPageCount: Int,
    val lowTextPageCount: Int,
    /** Identity of the campaign material this analysis was generated from — see [AnalysisSource.fingerprint]. */
    val sourceFingerprint: String,
    val modelId: String,
    val generatedAt: Instant
)
