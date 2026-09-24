package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.StudyPriority
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant

// SM-C1: fewer items is better; 35–45 cards for 20 pages is the ceiling.
const val MAX_ANALYSIS_PRIORITIES = 40

sealed interface ValidationResult {
    data class Valid(val analysis: StudyAnalysis) : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

/**
 * Semantic validation of model output. Structured output guarantees JSON shape, not correctness,
 * so this is the gate between the model and persistence. Any violation rejects the whole
 * response — a partially trusted analysis is never stored.
 */
object StudyAnalysisValidator {

    fun validate(
        draft: AnalysisDraft,
        source: AnalysisSource,
        modelId: String,
        generatedAt: Instant
    ): ValidationResult {
        if (draft.summary.isBlank()) return ValidationResult.Invalid("blank summary")
        if (draft.priorities.size > MAX_ANALYSIS_PRIORITIES) {
            return ValidationResult.Invalid("too many priorities: ${draft.priorities.size}")
        }

        val refMap = source.refMap
        val kept = mutableListOf<StudyPriority>()
        var greenCount = 0

        draft.priorities.forEachIndexed { index, item ->
            val triage = TriageLevel.entries.firstOrNull { it.name == item.triage }
                ?: return ValidationResult.Invalid("unknown triage at $index")
            if (item.title.isBlank()) return ValidationResult.Invalid("blank title at $index")

            // Every returned reference must be one this request actually supplied — that is also
            // what guarantees it belongs to this campaign.
            val refIds = item.sourceBlockIds.map { it.trim() }.distinct()
            if (refIds.any { it !in refMap }) {
                return ValidationResult.Invalid("unknown source refs at $index")
            }

            if (triage == TriageLevel.CODE_GREEN) {
                greenCount++
                return@forEachIndexed
            }

            // Constraint 6.1: every study item must be grounded in at least one source block.
            if (refIds.isEmpty()) return ValidationResult.Invalid("ungrounded item at $index")
            val budget = ThreeBulletBudget.enforce(item.bullets)
            if (budget.bullets.isEmpty()) return ValidationResult.Invalid("no bullets at $index")

            kept += StudyPriority(
                orderIndex = kept.size,
                title = item.title.trim(),
                triage = triage,
                bullets = budget.bullets,
                notes = budget.notes,
                sourceRefs = refIds.map { refMap.getValue(it) }
            )
        }

        if (kept.isEmpty()) return ValidationResult.Invalid("no Code Red or Code Yellow items")

        // Code Red first, then Code Yellow; model order is kept within each level.
        val ordered = kept.sortedBy { it.triage.ordinal }.mapIndexed { i, p -> p.copy(orderIndex = i) }

        return ValidationResult.Valid(
            StudyAnalysis(
                campaignId = source.campaignId,
                summary = draft.summary.trim(),
                priorities = ordered,
                discardedGreenCount = greenCount,
                analyzedPageCount = source.analyzedPageCount,
                lowTextPageCount = source.lowTextPageCount,
                sourceFingerprint = source.fingerprint,
                modelId = modelId,
                generatedAt = generatedAt
            )
        )
    }
}
