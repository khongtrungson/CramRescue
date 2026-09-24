package com.example.cramrescue.domain.speedrun

import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.model.RecallAssessment.MASTERED
import com.example.cramrescue.domain.model.RecallAssessment.NOT_REMEMBERED
import com.example.cramrescue.domain.model.RecallAssessment.PARTIAL
import com.example.cramrescue.domain.model.TriageLevel.CODE_GREEN
import com.example.cramrescue.domain.model.TriageLevel.CODE_RED
import com.example.cramrescue.domain.model.TriageLevel.CODE_YELLOW
import com.example.cramrescue.domain.progress.latestAssessments
import com.example.cramrescue.domain.progress.testAttempt
import com.example.cramrescue.domain.progress.testCard
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedRunPolicyTest {

    private fun windowAt(remaining: Duration) = SpeedRunPolicy.window(CampaignCountdown.from(NOW.plus(remaining), NOW))

    @Test
    fun windowReusesTheCountdownEmergencyDefinitionAndSeparatesExamReached() {
        assertEquals(SpeedRunWindow.BEFORE_EMERGENCY, windowAt(Duration.ofHours(36)))
        assertEquals(SpeedRunWindow.BEFORE_EMERGENCY, windowAt(Duration.ofHours(6).plusSeconds(1)))
        // CampaignCountdown's existing boundary is "≤ 6h" (FR-8 says "dưới 6 tiếng"); reused as-is.
        assertEquals(SpeedRunWindow.OPEN, windowAt(Duration.ofHours(6)))
        assertEquals(SpeedRunWindow.OPEN, windowAt(Duration.ofHours(6).minusSeconds(1)))
        assertEquals(SpeedRunWindow.OPEN, windowAt(Duration.ofSeconds(1)))
        // Exam reached is not emergency study time, even though the countdown's isEmergency is true.
        assertEquals(SpeedRunWindow.EXAM_REACHED, windowAt(Duration.ZERO))
        assertEquals(SpeedRunWindow.EXAM_REACHED, windowAt(Duration.ofHours(-2)))
    }

    @Test
    fun onlyUnmasteredCodeRedIsSelectedYellowGreenAndMasteredSkipped() {
        val cards = listOf(
            testCard(1, CODE_RED), testCard(2, CODE_RED), testCard(3, CODE_YELLOW),
            testCard(4, CODE_GREEN), testCard(5, CODE_RED)
        )
        val selected = SpeedRunPolicy.select(cards, mapOf(2L to MASTERED, 3L to NOT_REMEMBERED))
        assertEquals(listOf(1L, 5L), selected.map { it.id })
    }

    @Test
    fun orderWithinRedIsChuaNhoThenNeverStudiedThenTamOnThenCardOrder() {
        val cards = (1L..6L).map { testCard(it, CODE_RED) }
        val latest = mapOf(1L to PARTIAL, 2L to MASTERED, 4L to NOT_REMEMBERED, 6L to NOT_REMEMBERED)
        assertEquals(listOf(4L, 6L, 3L, 5L, 1L), SpeedRunPolicy.select(cards, latest).map { it.id })
    }

    @Test
    fun allUnmasteredRedAreIncludedWithNoCapAndNoDuplicates() {
        val twelve = (1L..12L).map { testCard(it, CODE_RED) }
        val selected = SpeedRunPolicy.select(twelve, emptyMap())
        assertEquals(12, selected.size) // FR-13 "toàn bộ": no 5–7 mission cap
        assertEquals(selected.size, selected.distinctBy { it.id }.size)
        // Fewer than any target: just what exists.
        assertEquals(listOf(1L), SpeedRunPolicy.select(listOf(testCard(1, CODE_RED)), emptyMap()).map { it.id })
    }

    @Test
    fun yellowOnlyOrNoCardsOrAllRedMasteredGiveNothing() {
        assertTrue(SpeedRunPolicy.select(emptyList(), emptyMap()).isEmpty())
        assertTrue(SpeedRunPolicy.select(listOf(testCard(1, CODE_YELLOW)), emptyMap()).isEmpty())
        assertTrue(SpeedRunPolicy.select(listOf(testCard(1, CODE_RED), testCard(2, CODE_RED)), mapOf(1L to MASTERED, 2L to MASTERED)).isEmpty())
    }

    @Test
    fun latestAttemptWinsAndReplacedCardHistoryIsIgnored() {
        val cards = listOf(testCard(1, CODE_RED), testCard(2, CODE_RED))
        val attempts = listOf(
            testAttempt(1, 1, NOT_REMEMBERED),
            testAttempt(2, 1, MASTERED), // latest for card 1 → excluded
            testAttempt(3, 2, MASTERED),
            testAttempt(4, 2, NOT_REMEMBERED), // latest for card 2 → included, first
            testAttempt(5, null, MASTERED), // replaced card
            testAttempt(6, 99, NOT_REMEMBERED) // not a current card
        )
        assertEquals(listOf(2L), SpeedRunPolicy.select(cards, latestAssessments(attempts)).map { it.id })
    }

    @Test
    fun selectionIsDeterministicWithStableTieOrdering() {
        val cards = (1L..8L).map { testCard(it, CODE_RED, order = (9 - it).toInt()) }
        val first = SpeedRunPolicy.select(cards, emptyMap())
        // Ties broken by M8 card order (orderIndex), not by input order or time.
        assertEquals((8L downTo 1L).toList(), first.map { it.id })
        repeat(10) { assertEquals(first, SpeedRunPolicy.select(cards.shuffled(java.util.Random(it.toLong())), emptyMap())) }
    }
}
