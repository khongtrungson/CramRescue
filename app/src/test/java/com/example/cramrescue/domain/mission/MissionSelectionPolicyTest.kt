package com.example.cramrescue.domain.mission

import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.model.RecallAssessment.MASTERED
import com.example.cramrescue.domain.model.RecallAssessment.NOT_REMEMBERED
import com.example.cramrescue.domain.model.RecallAssessment.PARTIAL
import com.example.cramrescue.domain.model.TriageLevel.CODE_GREEN
import com.example.cramrescue.domain.model.TriageLevel.CODE_RED
import com.example.cramrescue.domain.model.TriageLevel.CODE_YELLOW
import com.example.cramrescue.domain.progress.testAttempt
import com.example.cramrescue.domain.progress.testCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionSelectionPolicyTest {

    @Test
    fun redBeforeYellowAndWithinTierChuaNhoThenUnstudiedThenTamOn() {
        val cards = listOf(
            testCard(1, CODE_RED), testCard(2, CODE_RED), testCard(3, CODE_RED),
            testCard(4, CODE_YELLOW), testCard(5, CODE_YELLOW)
        )
        val latest = mapOf(1L to PARTIAL, 3L to NOT_REMEMBERED, 4L to NOT_REMEMBERED)

        val selected = MissionSelectionPolicy.select(cards, latest)

        // Red: 3 (Chưa nhớ), 2 (never studied), 1 (Tạm ổn); then Yellow: 4 (Chưa nhớ), 5 (never).
        assertEquals(listOf(3L, 2L, 1L, 4L, 5L), selected.map { it.id })
        assertEquals(MissionFocus.CODE_RED, MissionSelectionPolicy.focusOf(selected))
    }

    @Test
    fun masteredCardsAreExcludedAndYellowFollowsOnceRedIsMastered() {
        val cards = listOf(testCard(1, CODE_RED), testCard(2, CODE_RED), testCard(3, CODE_YELLOW), testCard(4, CODE_YELLOW))
        val selected = MissionSelectionPolicy.select(cards, mapOf(1L to MASTERED, 2L to MASTERED, 4L to MASTERED))

        assertEquals(listOf(3L), selected.map { it.id })
        assertEquals(MissionFocus.CODE_YELLOW, MissionSelectionPolicy.focusOf(selected))
    }

    @Test
    fun greenIsNeverSelected() {
        val selected = MissionSelectionPolicy.select(listOf(testCard(1, CODE_GREEN), testCard(2, CODE_RED)), emptyMap())
        assertEquals(listOf(2L), selected.map { it.id })
    }

    @Test
    fun atMostSevenCardsAndFewerAreNeverPaddedOrDuplicated() {
        val twelve = (1L..12L).map { testCard(it, if (it <= 6) CODE_RED else CODE_YELLOW) }
        val big = MissionSelectionPolicy.select(twelve, emptyMap())
        assertEquals(MissionSelectionPolicy.MAX_MISSION_CARDS, big.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), big.map { it.id })

        val three = MissionSelectionPolicy.select(twelve.take(3), emptyMap())
        assertEquals(listOf(1L, 2L, 3L), three.map { it.id })
        assertEquals(three.size, three.distinct().size)
    }

    @Test
    fun everythingMasteredMeansNoMission() {
        val cards = listOf(testCard(1, CODE_RED), testCard(2, CODE_YELLOW))
        val selected = MissionSelectionPolicy.select(cards, mapOf(1L to MASTERED, 2L to MASTERED))
        assertTrue(selected.isEmpty())
        assertEquals(null, MissionSelectionPolicy.focusOf(selected))
    }

    @Test
    fun selectionIsDeterministic() {
        val cards = (1L..9L).map { testCard(it, if (it % 2 == 0L) CODE_RED else CODE_YELLOW) }
        val latest = mapOf(2L to PARTIAL, 5L to NOT_REMEMBERED)
        val first = MissionSelectionPolicy.select(cards, latest)
        repeat(10) { assertEquals(first, MissionSelectionPolicy.select(cards.shuffled(java.util.Random(it.toLong())), latest)) }
    }

    @Test
    fun missionOrderAndMembershipStayFrozenWhileStudying() {
        val cards = (1L..4L).map { testCard(it, CODE_RED) }
        val mission = MicroMission(campaignId = 1, cardIds = listOf(3L, 1L, 4L), baselineAttemptId = 10, startedAt = NOW)

        // Attempts before the mission (id ≤ baseline) don't count as mission progress.
        val before = listOf(testAttempt(9, 3L, MASTERED))
        assertEquals(listOf(3L, 1L, 4L), mission.remainingCards(cards, before).map { it.id })

        // Assessing the first mission card removes only it; the rest keep their order.
        val after = before + testAttempt(11, 3L, NOT_REMEMBERED)
        assertEquals(listOf(1L, 4L), mission.remainingCards(cards, after).map { it.id })

        // A card replaced by a rebuild simply drops out.
        assertEquals(listOf(1L), mission.remainingCards(cards.filter { it.id != 4L }, after).map { it.id })
    }
}
