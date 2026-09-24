package com.example.cramrescue.domain.microcard

import com.example.cramrescue.domain.analysis.AnalysisDraft
import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ThreeBulletBudget
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.StudyPriority
import com.example.cramrescue.domain.model.TriageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicroCardBuilderTest {

    // Campaign 1: doc 10 (p0: B1 "Red fact", B2 LONG; p1: B3 "Yellow fact"). Campaign 2: doc 20 (B1 "B secret").
    private val ocrA = ocr(10, mapOf(0 to listOf("Red fact", LONG_TEXT), 1 to listOf("Yellow fact")))
    private val sourceA = AnalysisSource.assemble(1, listOf(document(10, 1, pages = 2)), mapOf(10L to ocrA))
    private val sourceB = AnalysisSource.assemble(2, listOf(document(20, 2)), mapOf(20L to ocr(20, mapOf(0 to listOf("B secret")))))

    private fun analysis(draft: AnalysisDraft, source: AnalysisSource = sourceA): StudyAnalysis =
        (StudyAnalysisValidator.validate(draft, source, "m", NOW) as ValidationResult.Valid).analysis

    private fun cards(analysis: StudyAnalysis, source: AnalysisSource = sourceA): List<MicroCard> =
        (MicroCardBuilder.build(analysis, source, NOW) as MicroCardBuildResult.Valid).cards

    @Test
    fun redAndYellowPrioritiesBecomeGroundedCardsRedFirstInAnalysisOrder() {
        val built = cards(
            analysis(
                draft(
                    item(title = "Yellow 1", triage = "CODE_YELLOW", refs = listOf("B3")),
                    item(title = "Red 1", triage = "CODE_RED", refs = listOf("B1", "B2")),
                    item(title = "Red 2", triage = "CODE_RED", refs = listOf("B2"))
                )
            )
        )

        assertEquals(listOf("Red 1", "Red 2", "Yellow 1"), built.map { it.title })
        assertEquals(listOf(TriageLevel.CODE_RED, TriageLevel.CODE_RED, TriageLevel.CODE_YELLOW), built.map { it.triage })
        assertEquals(listOf(0, 1, 2), built.map { it.orderIndex })
        assertTrue(built.all { it.campaignId == 1L && it.analysisGeneratedAt == NOW })
    }

    @Test
    fun sourceRefsMapToTheExactDocumentPageAndBlockAndItsBoundingBox() {
        val card = cards(analysis(draft(item(triage = "CODE_YELLOW", refs = listOf("B3"))))).single()

        assertEquals(listOf(SourceRef(10, 1, 0)), card.sourceRefs)
        val block = ocrA.blockAt(card.sourceRefs.single())!!
        assertEquals("Yellow fact", block.text)
        // Bounding-box identity is recoverable for future Source Grounding.
        assertEquals(BoundingBox(11, 22, 333, 444), block.boundingBox)
    }

    @Test
    fun greenNeverBecomesAStudyCard() {
        val base = analysis(draft(item(title = "Red", refs = listOf("B1"))))
        val withGreen = base.copy(
            priorities = base.priorities + StudyPriority(1, "Green", TriageLevel.CODE_GREEN, listOf("x"), null, listOf(SourceRef(10, 0, 1)))
        )

        assertEquals(listOf("Red"), cards(withGreen).map { it.title })
    }

    @Test
    fun anotherCampaignsMaterialCanNeverGroundACard() {
        // Analysis A pointing at campaign B's block, or built against B's source, is rejected.
        val analysisA = analysis(draft(item(refs = listOf("B1"))))
        val crossRef = analysisA.copy(
            priorities = analysisA.priorities.map { it.copy(sourceRefs = listOf(SourceRef(20, 0, 0))) }
        )

        assertTrue(MicroCardBuilder.build(crossRef, sourceA, NOW) is MicroCardBuildResult.Invalid)
        assertTrue(MicroCardBuilder.build(analysisA, sourceB, NOW) is MicroCardBuildResult.Invalid)
    }

    @Test
    fun sourceNotInTheCurrentMaterialRejectsTheWholeSet() {
        val base = analysis(draft(item(title = "Good", refs = listOf("B1")), item(title = "Other", refs = listOf("B2"))))
        val withMissing = base.copy(
            priorities = base.priorities.mapIndexed { i, p ->
                if (i == 1) p.copy(sourceRefs = listOf(SourceRef(10, 0, 99))) else p
            }
        )

        assertTrue(MicroCardBuilder.build(withMissing, sourceA, NOW) is MicroCardBuildResult.Invalid)
    }

    @Test
    fun blankTitleOrNoContentOrNoSourceIsRejected() {
        val base = analysis(draft(item()))
        fun withFirst(transform: (StudyPriority) -> StudyPriority) =
            base.copy(priorities = base.priorities.map(transform))

        assertTrue(MicroCardBuilder.build(withFirst { it.copy(title = "  ") }, sourceA, NOW) is MicroCardBuildResult.Invalid)
        assertTrue(MicroCardBuilder.build(withFirst { it.copy(bullets = listOf(" ")) }, sourceA, NOW) is MicroCardBuildResult.Invalid)
        assertTrue(MicroCardBuilder.build(withFirst { it.copy(sourceRefs = emptyList()) }, sourceA, NOW) is MicroCardBuildResult.Invalid)
    }

    @Test
    fun threeBulletBudgetIsReEnforcedAndOverflowKeptAsNotes() {
        val base = analysis(draft(item()))
        val long = (1..25).joinToString(" ") { "w$it" }
        val oversized = base.copy(priorities = base.priorities.map { it.copy(bullets = listOf("a", long, "c", "d"), notes = null) })

        val card = cards(oversized).single()

        assertEquals(3, card.bullets.size)
        assertTrue(card.bullets.all { ThreeBulletBudget.wordCount(it) <= 20 })
        assertEquals(listOf(long, "d"), card.notes?.split("\n"))
    }

    @Test
    fun exactDuplicateCardsAreStoredOnce() {
        val built = cards(
            analysis(
                draft(
                    item(title = "Binary search", bullets = listOf("Sorted input"), refs = listOf("B1")),
                    item(title = "binary  SEARCH ", bullets = listOf("sorted input"), refs = listOf("B2")),
                    item(title = "Binary search", bullets = listOf("Different content"), refs = listOf("B2"))
                )
            )
        )

        assertEquals(2, built.size)
        assertEquals(listOf(listOf("Sorted input"), listOf("Different content")), built.map { it.bullets })
    }
}
