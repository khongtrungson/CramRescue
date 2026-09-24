package com.example.cramrescue.domain.model

import java.time.Instant

/**
 * One study card (requirements.md glossary "Thẻ vi mô"): a concept title plus content within the
 * 3-Bullet Budget, grounded in OCR source blocks. Built locally from a Code Red / Code Yellow
 * priority of the campaign's current [StudyAnalysis] — M8 makes no AI call of its own.
 *
 * Study state (mastery, scheduling, cloze keywords) belongs to later milestones.
 */
data class MicroCard(
    val id: Long = 0L,
    val campaignId: Long,
    /**
     * [StudyAnalysis.generatedAt] of the analysis this card was built from — the analysis
     * generation identity used to tell current cards from cards of an older analysis.
     */
    val analysisGeneratedAt: Instant,
    /** 0-based; Code Red cards first, then Code Yellow, each in analysis order. */
    val orderIndex: Int,
    val triage: TriageLevel,
    val title: String,
    val bullets: List<String>,
    val notes: String?,
    /** Same stable block identity as the analysis; resolves to an OCR block and its bounding box. */
    val sourceRefs: List<SourceRef>,
    val createdAt: Instant
)
