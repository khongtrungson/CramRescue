package com.example.cramrescue.domain.mission

import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.progress.CardMastery
import com.example.cramrescue.domain.progress.mastery
import java.time.Instant

/**
 * A Micro-mission (requirements.md FR-10): a short, fixed pack of current cards. Persisted so the
 * learner can pause and resume "without losing the current mission's progress". Progress is
 * derived, not stored: a mission card is done once it has an attempt newer than [baselineAttemptId].
 */
data class MicroMission(
    val campaignId: Long,
    /** Frozen at mission start: membership and order never change while it's in progress. */
    val cardIds: List<Long>,
    /** Highest study attempt id when the mission started (0 if none). */
    val baselineAttemptId: Long,
    val startedAt: Instant
) {
    /** Mission cards still to study, in mission order; ids no longer among [currentCards] are dropped. */
    fun remainingCards(currentCards: List<MicroCard>, attempts: List<StudyAttempt>): List<MicroCard> {
        val byId = currentCards.associateBy { it.id }
        val done = attempts.filter { it.id > baselineAttemptId }.mapNotNull { it.microCardId }.toSet()
        return cardIds.filter { it !in done }.mapNotNull { byId[it] }
    }

    fun liveCardCount(currentCards: List<MicroCard>): Int {
        val ids = currentCards.map { it.id }.toSet()
        return cardIds.count { it in ids }
    }
}

/** What a newly selected mission focuses on — shown to the learner as the reason. */
enum class MissionFocus { CODE_RED, CODE_YELLOW }

/**
 * Deterministic next-mission selection (FR-10: packs of 5–7 cards).
 *
 * Eligible: current Code Red / Code Yellow cards whose latest assessment isn't "Đã thuộc".
 * Order: Code Red before Code Yellow (ER triage); within a tier "Chưa nhớ" (Redo soon), then
 * never studied, then "Tạm ổn" (Review in 2h); then M8 card order. Up to [MAX_MISSION_CARDS]
 * cards; fewer eligible cards are used as they are, never padded or duplicated.
 *
 * Implementation assumptions (PRD silent): Yellow is selected once no Red card needs work;
 * emergency mode (≤ 6h) doesn't change selection; mastered cards are not repeated (Survival
 * Repetition scheduling is a later milestone).
 */
object MissionSelectionPolicy {
    const val MIN_MISSION_CARDS = 5
    const val MAX_MISSION_CARDS = 7

    /**
     * Within-tier study urgency, shared by Micro-missions and Emergency Speed-run: "Chưa nhớ"
     * (Redo soon) → never studied → "Tạm ổn" (Review in 2h) → "Đã thuộc".
     */
    fun priority(mastery: CardMastery): Int = when (mastery) {
        CardMastery.NOT_REMEMBERED -> 0
        CardMastery.UNSTUDIED -> 1
        CardMastery.PARTIAL -> 2
        CardMastery.MASTERED -> 3
    }

    fun select(cards: List<MicroCard>, latest: Map<Long, RecallAssessment>): List<MicroCard> =
        cards.asSequence()
            .filter { it.triage != TriageLevel.CODE_GREEN }
            .filter { it.mastery(latest) != CardMastery.MASTERED }
            .sortedWith(compareBy({ it.triage.ordinal }, { priority(it.mastery(latest)) }, { it.orderIndex }, { it.id }))
            .take(MAX_MISSION_CARDS)
            .toList()

    fun focusOf(selection: List<MicroCard>): MissionFocus? = when {
        selection.isEmpty() -> null
        selection.any { it.triage == TriageLevel.CODE_RED } -> MissionFocus.CODE_RED
        else -> MissionFocus.CODE_YELLOW
    }
}
