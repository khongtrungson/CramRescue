package com.example.cramrescue.domain.recall

import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment

/**
 * An in-memory Active Recall session as an immutable state machine:
 *
 * QUESTION --reveal--> REVEALED --beginAssessment--> SAVING --assessmentSaved--> next QUESTION | COMPLETED
 *                                                           \--assessmentFailed--> REVEALED
 *
 * Every transition is a no-op from any other phase, so assessing before reveal, or a second
 * tap while the first assessment is saving, cannot happen. Not persisted: only attempts are.
 */
data class RecallSession(
    val cards: List<MicroCard>,
    val index: Int,
    val phase: Phase,
    val results: List<RecallAssessment>
) {
    enum class Phase { QUESTION, REVEALED, SAVING, COMPLETED }

    val currentCard: MicroCard? get() = if (phase == Phase.COMPLETED) null else cards.getOrNull(index)
    val position: Int get() = (index + 1).coerceAtMost(cards.size)
    val total: Int get() = cards.size

    fun reveal(): RecallSession = if (phase == Phase.QUESTION) copy(phase = Phase.REVEALED) else this

    fun beginAssessment(): RecallSession = if (phase == Phase.REVEALED) copy(phase = Phase.SAVING) else this

    fun assessmentSaved(assessment: RecallAssessment): RecallSession {
        if (phase != Phase.SAVING) return this
        val updated = results + assessment
        return if (index + 1 >= cards.size) {
            copy(phase = Phase.COMPLETED, results = updated)
        } else {
            copy(index = index + 1, phase = Phase.QUESTION, results = updated)
        }
    }

    /** Persistence failed: stay on the same card, answer still revealed; nothing is counted. */
    fun assessmentFailed(): RecallSession = if (phase == Phase.SAVING) copy(phase = Phase.REVEALED) else this

    fun summary(): Map<RecallAssessment, Int> =
        RecallAssessment.entries.associateWith { level -> results.count { it == level } }

    companion object {
        /** Emergency priority: Code Red before Code Yellow, M8 card order within each. */
        fun start(cards: List<MicroCard>): RecallSession =
            startInOrder(cards.sortedWith(compareBy({ it.triage.ordinal }, { it.orderIndex })))

        /** Keeps the given order — used for a Micro-mission, whose order is frozen at its start. */
        fun startInOrder(cards: List<MicroCard>): RecallSession = RecallSession(
            cards = cards,
            index = 0,
            phase = if (cards.isEmpty()) Phase.COMPLETED else Phase.QUESTION,
            results = emptyList()
        )
    }
}
