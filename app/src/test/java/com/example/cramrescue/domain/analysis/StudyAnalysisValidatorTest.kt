package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.TriageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnalysisValidatorTest {

    // Campaign 1 has blocks B1..B3 (doc 10: p0 b0,b1; p1 b0).
    private val source = AnalysisSource.assemble(
        1,
        listOf(document(10, 1, pages = 2)),
        mapOf(10L to ocr(10, mapOf(0 to listOf(LONG_TEXT, LONG_TEXT), 1 to listOf(LONG_TEXT))))
    )

    private fun validate(draft: AnalysisDraft) = StudyAnalysisValidator.validate(draft, source, "m", NOW)

    private fun validAnalysis(draft: AnalysisDraft) =
        (validate(draft) as ValidationResult.Valid).analysis

    @Test
    fun validDraftMapsToDomainWithResolvedRefsAndRedBeforeYellow() {
        val analysis = validAnalysis(
            draft(
                item(title = "Yellow topic", triage = "CODE_YELLOW", refs = listOf("B3")),
                item(title = "Red topic", triage = "CODE_RED", refs = listOf("B1", "B2")),
                item(title = "Green topic", triage = "CODE_GREEN", bullets = emptyList(), refs = listOf("B2"))
            )
        )

        assertEquals(1L, analysis.campaignId)
        assertEquals(listOf("Red topic", "Yellow topic"), analysis.priorities.map { it.title })
        assertEquals(listOf(TriageLevel.CODE_RED, TriageLevel.CODE_YELLOW), analysis.priorities.map { it.triage })
        assertEquals(listOf(0, 1), analysis.priorities.map { it.orderIndex })
        assertEquals(listOf(SourceRef(10, 0, 0), SourceRef(10, 0, 1)), analysis.priorities[0].sourceRefs)
        assertEquals(listOf(SourceRef(10, 1, 0)), analysis.priorities[1].sourceRefs)
        // FR-5: Green is counted, never stored as a study item.
        assertEquals(1, analysis.discardedGreenCount)
        assertEquals(source.fingerprint, analysis.sourceFingerprint)
    }

    @Test
    fun unknownSourceReferenceRejectsTheWholeResponse() {
        val result = validate(draft(item(refs = listOf("B1")), item(refs = listOf("B99"))))
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun referenceToABlockOfAnotherCampaignIsRejected() {
        // B4 would exist in a different request, never in this campaign's map.
        assertTrue(validate(draft(item(refs = listOf("B4")))) is ValidationResult.Invalid)
    }

    @Test
    fun blankSummaryOrTitleIsRejected() {
        assertTrue(validate(draft(item(), summary = "  ")) is ValidationResult.Invalid)
        assertTrue(validate(draft(item(title = " "))) is ValidationResult.Invalid)
    }

    @Test
    fun unknownTriageCannotEnterTheDomain() {
        assertTrue(validate(draft(item(triage = "CODE_BLUE"))) is ValidationResult.Invalid)
        assertTrue(validate(draft(item(triage = "red"))) is ValidationResult.Invalid)
    }

    @Test
    fun studyItemWithoutSourceOrBulletsIsRejected() {
        assertTrue(validate(draft(item(refs = emptyList()))) is ValidationResult.Invalid)
        assertTrue(validate(draft(item(bullets = listOf(" ")))) is ValidationResult.Invalid)
    }

    @Test
    fun onlyGreenItemsIsRejected() {
        assertTrue(validate(draft(item(triage = "CODE_GREEN"))) is ValidationResult.Invalid)
    }

    @Test
    fun tooManyPrioritiesIsRejected() {
        val items = List(MAX_ANALYSIS_PRIORITIES + 1) { item(title = "T$it") }
        assertTrue(validate(draft(*items.toTypedArray())) is ValidationResult.Invalid)
    }

    @Test
    fun duplicateReferencesAreCollapsed() {
        val analysis = validAnalysis(draft(item(refs = listOf("B1", " B1", "B1"))))
        assertEquals(listOf(SourceRef(10, 0, 0)), analysis.priorities[0].sourceRefs)
    }

    @Test
    fun bulletBudgetIsEnforcedAfterParsing() {
        val longBullet = (1..25).joinToString(" ") { "w$it" }
        val analysis = validAnalysis(draft(item(bullets = listOf("one", longBullet, "three", "four", "five"))))
        val priority = analysis.priorities.single()

        assertEquals(3, priority.bullets.size)
        assertTrue(priority.bullets.all { ThreeBulletBudget.wordCount(it) <= 20 })
        assertEquals((1..20).joinToString(" ") { "w$it" } + "…", priority.bullets[1])
        // Constraint 6.2: overflow is moved to notes, not silently dropped.
        assertEquals(listOf(longBullet, "four", "five"), priority.notes?.split("\n"))
    }

    @Test
    fun bulletsWithinBudgetHaveNoNotes() {
        val result = ThreeBulletBudget.enforce(listOf("a  b", "c"))
        assertEquals(listOf("a b", "c"), result.bullets)
        assertNull(result.notes)
    }
}
