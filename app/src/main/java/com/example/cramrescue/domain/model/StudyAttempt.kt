package com.example.cramrescue.domain.model

import java.time.Instant

/**
 * requirements.md FR-12 self-assessment levels. Persisted by name; UI labels ("Chưa nhớ",
 * "Tạm ổn", "Đã thuộc") belong to presentation.
 */
enum class RecallAssessment {
    /** "Chưa nhớ" (Redo soon). */
    NOT_REMEMBERED,

    /** "Tạm ổn" (Review in 2h). */
    PARTIAL,

    /** "Đã thuộc" (Mastered). */
    MASTERED
}

/**
 * One self-assessed recall of one MicroCard. Append-only history: a card's current mastery
 * (FR-12 `masteryLevel`) is its latest attempt, so no mutable progress field lives on the card.
 */
data class StudyAttempt(
    val id: Long = 0L,
    val campaignId: Long,
    /**
     * The card studied, or null once that card was replaced by a rebuild — history is kept, but a
     * new card never inherits an old card's results.
     */
    val microCardId: Long?,
    /** Snapshot of the card at study time, so history stays meaningful after a rebuild. */
    val triage: TriageLevel,
    val cardTitle: String,
    val assessment: RecallAssessment,
    val attemptedAt: Instant
)
