package com.example.cramrescue.domain.progress

import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.model.TriageLevel

/** A current card's state from its latest attempt (FR-12), or never studied. */
enum class CardMastery { UNSTUDIED, NOT_REMEMBERED, PARTIAL, MASTERED }

/**
 * Latest assessment per card id. Attempts are append-only with increasing ids, so the highest id
 * per card is its current state; attempts of replaced cards (null id) are history only.
 */
fun latestAssessments(attempts: List<StudyAttempt>): Map<Long, RecallAssessment> =
    attempts.filter { it.microCardId != null }
        .sortedBy { it.id }
        .associate { it.microCardId!! to it.assessment }

fun MicroCard.mastery(latest: Map<Long, RecallAssessment>): CardMastery = when (latest[id]) {
    null -> CardMastery.UNSTUDIED
    RecallAssessment.NOT_REMEMBERED -> CardMastery.NOT_REMEMBERED
    RecallAssessment.PARTIAL -> CardMastery.PARTIAL
    RecallAssessment.MASTERED -> CardMastery.MASTERED
}

/**
 * "Xác suất an toàn" (requirements.md FR-9):
 *
 *     Pass Probability = 0.70 × Red completion rate + 0.30 × Yellow completion rate
 *
 * This is the PRD's deterministic readiness heuristic — not a statistically calibrated
 * probability of passing (the app has no outcome data to calibrate one).
 *
 * Implementation decisions where the PRD is silent:
 *  - a card is "completed" only when its latest assessment is "Đã thuộc" (MASTERED); "Tạm ổn"
 *    is "review in 2h" in FR-12, so it earns no credit;
 *  - if a tier has no cards, its weight is dropped and the other tier is renormalized, so a
 *    campaign without Yellow cards can still reach 100%;
 *  - the result is floored to an integer 0..100, so 100% only appears when everything counted
 *    is mastered;
 *  - remaining time does not enter the formula (FR-9 doesn't include it).
 */
object PassProbabilityPolicy {
    const val RED_WEIGHT = 0.70
    const val YELLOW_WEIGHT = 0.30

    fun completionCredit(mastery: CardMastery): Double = if (mastery == CardMastery.MASTERED) 1.0 else 0.0

    /** Null when there are no Red/Yellow cards — "unavailable", which is not 0%. */
    fun calculate(cards: List<MicroCard>, latest: Map<Long, RecallAssessment>): PassProbabilitySnapshot? {
        val red = cards.filter { it.triage == TriageLevel.CODE_RED }
        val yellow = cards.filter { it.triage == TriageLevel.CODE_YELLOW }
        if (red.isEmpty() && yellow.isEmpty()) return null

        fun rate(tier: List<MicroCard>) = tier.sumOf { completionCredit(it.mastery(latest)) } / tier.size
        var weighted = 0.0
        var weights = 0.0
        if (red.isNotEmpty()) {
            weighted += RED_WEIGHT * rate(red)
            weights += RED_WEIGHT
        }
        if (yellow.isNotEmpty()) {
            weighted += YELLOW_WEIGHT * rate(yellow)
            weights += YELLOW_WEIGHT
        }
        // Small epsilon so exact values like 0.7 * 1.0 / 0.7 aren't floored to 99 by FP error.
        val percent = kotlin.math.floor(100.0 * weighted / weights + 1e-9).toInt().coerceIn(0, 100)

        val studied = red + yellow
        return PassProbabilitySnapshot(
            percent = percent,
            redMastered = red.count { it.mastery(latest) == CardMastery.MASTERED },
            redTotal = red.size,
            yellowMastered = yellow.count { it.mastery(latest) == CardMastery.MASTERED },
            yellowTotal = yellow.size,
            partialCount = studied.count { it.mastery(latest) == CardMastery.PARTIAL },
            notRememberedCount = studied.count { it.mastery(latest) == CardMastery.NOT_REMEMBERED },
            unstudiedCount = studied.count { it.mastery(latest) == CardMastery.UNSTUDIED }
        )
    }
}

data class PassProbabilitySnapshot(
    /** Integer 0..100 (FR-9). */
    val percent: Int,
    val redMastered: Int,
    val redTotal: Int,
    val yellowMastered: Int,
    val yellowTotal: Int,
    val partialCount: Int,
    val notRememberedCount: Int,
    val unstudiedCount: Int
)
