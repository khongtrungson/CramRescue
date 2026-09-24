package com.example.cramrescue.domain.microcard

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.analysis.MAX_ANALYSIS_PRIORITIES
import com.example.cramrescue.domain.analysis.ThreeBulletBudget
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant

sealed interface MicroCardBuildResult {
    data class Valid(val cards: List<MicroCard>) : MicroCardBuildResult
    data class Invalid(val reason: String) : MicroCardBuildResult
}

/**
 * Deterministically turns a campaign's current analysis into MicroCards — no AI call.
 *
 * Each Code Red / Code Yellow priority already is a card per the PRD (concept title + 3-Bullet
 * content + sources), so this re-checks rather than re-generates: the same all-or-nothing
 * philosophy as [com.example.cramrescue.domain.analysis.StudyAnalysisValidator]. Any card that
 * can't be grounded in the campaign's current OCR rejects the whole set.
 */
object MicroCardBuilder {

    private val WHITESPACE = Regex("\\s+")

    fun build(analysis: StudyAnalysis, source: AnalysisSource, now: Instant): MicroCardBuildResult {
        if (analysis.campaignId != source.campaignId) return MicroCardBuildResult.Invalid("campaign mismatch")

        // Only blocks of this campaign's current material can ground a card.
        val currentBlocks = source.refMap.values.toSet()
        val seen = mutableSetOf<String>()
        val cards = mutableListOf<MicroCard>()

        // FR-5: Code Green is never a study card. Red before Yellow; analysis order within each.
        val studyPriorities = analysis.priorities
            .filter { it.triage != TriageLevel.CODE_GREEN }
            .sortedWith(compareBy({ it.triage.ordinal }, { it.orderIndex }))
        if (studyPriorities.size > MAX_ANALYSIS_PRIORITIES) {
            return MicroCardBuildResult.Invalid("too many cards: ${studyPriorities.size}")
        }

        for (priority in studyPriorities) {
            val title = priority.title.normalize()
            if (title.isEmpty()) return MicroCardBuildResult.Invalid("blank title")

            // Re-apply the budget: analyses are persisted data and must not be trusted blindly.
            val budget = ThreeBulletBudget.enforce(priority.bullets)
            if (budget.bullets.isEmpty()) return MicroCardBuildResult.Invalid("no content for \"$title\"")

            val refs = priority.sourceRefs.distinct()
            if (refs.isEmpty()) return MicroCardBuildResult.Invalid("ungrounded card \"$title\"")
            if (refs.any { it !in currentBlocks }) {
                return MicroCardBuildResult.Invalid("source not in current material for \"$title\"")
            }

            // Exact-duplicate protection on normalized content; the first occurrence wins.
            val key = (listOf(title) + budget.bullets).joinToString("\n") { it.normalize().lowercase() }
            if (!seen.add(key)) continue

            val notes = listOfNotNull(priority.notes?.takeIf { it.isNotBlank() }, budget.notes)
                .distinct()
                .joinToString("\n")
                .takeIf { it.isNotEmpty() }

            cards += MicroCard(
                campaignId = analysis.campaignId,
                analysisGeneratedAt = analysis.generatedAt,
                orderIndex = cards.size,
                triage = priority.triage,
                title = title,
                bullets = budget.bullets,
                notes = notes,
                sourceRefs = refs,
                createdAt = now
            )
        }

        if (cards.isEmpty()) return MicroCardBuildResult.Invalid("no Code Red or Code Yellow material")
        return MicroCardBuildResult.Valid(cards)
    }

    private fun String.normalize(): String = trim().replace(WHITESPACE, " ")
}
