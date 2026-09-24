package com.example.cramrescue.domain.recall

import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.recall.RecallSession.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecallSessionTest {

    private fun card(id: Long, triage: TriageLevel, order: Int) = MicroCard(
        id = id, campaignId = 1, analysisGeneratedAt = NOW, orderIndex = order, triage = triage,
        title = "Card $id", bullets = listOf("b"), notes = null, sourceRefs = listOf(SourceRef(1, 0, 0)), createdAt = NOW
    )

    // Deliberately shuffled input: Yellow first, Red out of order.
    private val cards = listOf(
        card(4, TriageLevel.CODE_YELLOW, 3),
        card(2, TriageLevel.CODE_RED, 1),
        card(3, TriageLevel.CODE_YELLOW, 2),
        card(1, TriageLevel.CODE_RED, 0)
    )

    private fun RecallSession.answer(assessment: RecallAssessment) =
        reveal().beginAssessment().assessmentSaved(assessment)

    @Test
    fun codeRedComesBeforeCodeYellowInM8Order() {
        val session = RecallSession.start(cards)
        assertEquals(listOf(1L, 2L, 3L, 4L), session.cards.map { it.id })
        assertEquals(Phase.QUESTION, session.phase)
        assertEquals(1, session.position)
        assertEquals(4, session.total)
    }

    @Test
    fun revealMovesQuestionToRevealedOnlyOnce() {
        val revealed = RecallSession.start(cards).reveal()
        assertEquals(Phase.REVEALED, revealed.phase)
        assertSame(revealed, revealed.reveal())
    }

    @Test
    fun assessmentIsImpossibleBeforeReveal() {
        val start = RecallSession.start(cards)
        assertSame(start, start.beginAssessment())
        assertSame(start, start.assessmentSaved(RecallAssessment.MASTERED))
        assertEquals(emptyList<RecallAssessment>(), start.results)
    }

    @Test
    fun oneAssessmentAdvancesExactlyOneCard() {
        val next = RecallSession.start(cards).answer(RecallAssessment.PARTIAL)
        assertEquals(Phase.QUESTION, next.phase)
        assertEquals(2L, next.currentCard?.id)
        assertEquals(listOf(RecallAssessment.PARTIAL), next.results)
    }

    @Test
    fun aSecondTapWhileSavingIsIgnored() {
        val saving = RecallSession.start(cards).reveal().beginAssessment()
        assertEquals(Phase.SAVING, saving.phase)
        // A second tap: beginAssessment is a no-op, so only one save starts.
        assertSame(saving, saving.beginAssessment())
        val next = saving.assessmentSaved(RecallAssessment.MASTERED)
        // A stray second completion can't skip another card.
        assertSame(next, next.assessmentSaved(RecallAssessment.MASTERED))
        assertEquals(2L, next.currentCard?.id)
        assertEquals(1, next.results.size)
    }

    @Test
    fun failedSaveReturnsToTheSameRevealedCardWithoutCounting() {
        val failed = RecallSession.start(cards).reveal().beginAssessment().assessmentFailed()
        assertEquals(Phase.REVEALED, failed.phase)
        assertEquals(1L, failed.currentCard?.id)
        assertEquals(emptyList<RecallAssessment>(), failed.results)
    }

    @Test
    fun lastCardCompletesWithCorrectSummary() {
        val done = RecallSession.start(cards)
            .answer(RecallAssessment.NOT_REMEMBERED)
            .answer(RecallAssessment.PARTIAL)
            .answer(RecallAssessment.MASTERED)
            .answer(RecallAssessment.MASTERED)

        assertEquals(Phase.COMPLETED, done.phase)
        assertNull(done.currentCard)
        assertEquals(
            mapOf(RecallAssessment.NOT_REMEMBERED to 1, RecallAssessment.PARTIAL to 1, RecallAssessment.MASTERED to 2),
            done.summary()
        )
        assertSame(done, done.reveal())
    }

    @Test
    fun noCardsMeansAnImmediatelyCompletedEmptySession() {
        val empty = RecallSession.start(emptyList())
        assertEquals(Phase.COMPLETED, empty.phase)
        assertNull(empty.currentCard)
    }

    @Test
    fun onlyYellowCardsAreStillStudyable() {
        val session = RecallSession.start(cards.filter { it.triage == TriageLevel.CODE_YELLOW })
        assertEquals(listOf(3L, 4L), session.cards.map { it.id })
        assertEquals(Phase.QUESTION, session.phase)
    }
}
