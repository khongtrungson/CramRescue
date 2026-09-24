package com.example.cramrescue.domain.progress

import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.RecallAssessment.MASTERED
import com.example.cramrescue.domain.model.RecallAssessment.NOT_REMEMBERED
import com.example.cramrescue.domain.model.RecallAssessment.PARTIAL
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.model.TriageLevel.CODE_RED
import com.example.cramrescue.domain.model.TriageLevel.CODE_YELLOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun testCard(id: Long, triage: TriageLevel, order: Int = id.toInt()) = MicroCard(
    id = id, campaignId = 1, analysisGeneratedAt = NOW, orderIndex = order, triage = triage,
    title = "Card $id", bullets = listOf("b"), notes = null, sourceRefs = listOf(SourceRef(1, 0, 0)), createdAt = NOW
)

fun testAttempt(id: Long, cardId: Long?, assessment: RecallAssessment, campaignId: Long = 1) = StudyAttempt(
    id = id, campaignId = campaignId, microCardId = cardId, triage = CODE_RED, cardTitle = "t",
    assessment = assessment, attemptedAt = NOW
)

class PassProbabilityPolicyTest {

    private val red = listOf(testCard(1, CODE_RED), testCard(2, CODE_RED))
    private val yellow = listOf(testCard(3, CODE_YELLOW), testCard(4, CODE_YELLOW))
    private val all = red + yellow

    private fun percent(cards: List<MicroCard>, latest: Map<Long, RecallAssessment>) =
        PassProbabilityPolicy.calculate(cards, latest)!!.percent

    @Test
    fun noCardsIsUnavailableNotZero() {
        assertNull(PassProbabilityPolicy.calculate(emptyList(), emptyMap()))
    }

    @Test
    fun neverStudiedIsZeroWithEveryCardUnstudied() {
        val snapshot = PassProbabilityPolicy.calculate(all, emptyMap())!!
        assertEquals(0, snapshot.percent)
        assertEquals(4, snapshot.unstudiedCount)
        assertEquals(2, snapshot.redTotal)
        assertEquals(2, snapshot.yellowTotal)
    }

    @Test
    fun onlyDaThuocCountsAsCompleted() {
        assertEquals(0, percent(all, all.associate { it.id to NOT_REMEMBERED }))
        // "Tạm ổn" = review in 2h (FR-12): no completion credit.
        assertEquals(0, percent(all, all.associate { it.id to PARTIAL }))
        assertEquals(100, percent(all, all.associate { it.id to MASTERED }))
    }

    @Test
    fun prdFormulaWeightsRedSeventyYellowThirty() {
        // 0.7 × 1/2 + 0.3 × 1/2 = 50
        assertEquals(50, percent(all, mapOf(1L to MASTERED, 3L to MASTERED)))
        // One Red card of two: 0.7 × 1/2 = 35. One Yellow card of two: 0.3 × 1/2 = 15.
        assertEquals(35, percent(all, mapOf(1L to MASTERED)))
        assertEquals(15, percent(all, mapOf(3L to MASTERED)))
        // All Red, no Yellow: 70.
        assertEquals(70, percent(all, mapOf(1L to MASTERED, 2L to MASTERED)))
    }

    @Test
    fun mixedSnapshotCountsEachState() {
        val snapshot = PassProbabilityPolicy.calculate(all, mapOf(1L to MASTERED, 2L to PARTIAL, 3L to NOT_REMEMBERED))!!
        assertEquals(1, snapshot.redMastered)
        assertEquals(0, snapshot.yellowMastered)
        assertEquals(1, snapshot.partialCount)
        assertEquals(1, snapshot.notRememberedCount)
        assertEquals(1, snapshot.unstudiedCount)
    }

    @Test
    fun missingTierIsRenormalizedSoFullMasteryIsStillHundred() {
        assertEquals(100, percent(red, red.associate { it.id to MASTERED }))
        assertEquals(100, percent(yellow, yellow.associate { it.id to MASTERED }))
        assertEquals(50, percent(red, mapOf(1L to MASTERED)))
    }

    @Test
    fun resultIsFlooredAndAlwaysWithinBounds() {
        val threeRed = (1L..3L).map { testCard(it, CODE_RED) }
        // 1/3 → 33.33… floored to 33; 3/3 must be exactly 100 despite floating-point.
        assertEquals(33, percent(threeRed, mapOf(1L to MASTERED)))
        assertEquals(100, percent(threeRed, threeRed.associate { it.id to MASTERED }))
        val sevenMixed = (1L..7L).map { testCard(it, if (it <= 3) CODE_RED else CODE_YELLOW) }
        for (mask in 0 until (1 shl 7)) {
            val latest = sevenMixed.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.associate { it.id to MASTERED }
            assertTrue(percent(sevenMixed, latest) in 0..100)
        }
    }

    @Test
    fun latestAttemptWinsAndOldFailuresDontOverrideNewerMastery() {
        val attempts = listOf(
            testAttempt(1, 1, NOT_REMEMBERED),
            testAttempt(2, 1, PARTIAL),
            testAttempt(3, 1, MASTERED),
            testAttempt(4, 2, MASTERED),
            testAttempt(5, 2, NOT_REMEMBERED)
        )
        val latest = latestAssessments(attempts.shuffled(java.util.Random(7)))
        assertEquals(MASTERED, latest[1L])
        assertEquals(NOT_REMEMBERED, latest[2L])
        // Three attempts on card 1 are still one card of progress.
        assertEquals(1, PassProbabilityPolicy.calculate(red, latest)!!.redMastered)
    }

    @Test
    fun attemptsOfReplacedCardsNeverCountForCurrentCards() {
        val attempts = listOf(
            testAttempt(1, null, MASTERED), // card deleted by a rebuild (SET NULL)
            testAttempt(2, 99, MASTERED) // an old card id not in the current set
        )
        assertEquals(0, percent(all, latestAssessments(attempts)))
    }

    @Test
    fun sameStateGivesSameResult() {
        val latest = mapOf(1L to MASTERED, 4L to PARTIAL)
        val first = PassProbabilityPolicy.calculate(all, latest)
        repeat(10) { assertEquals(first, PassProbabilityPolicy.calculate(all.reversed(), latest)) }
    }
}
