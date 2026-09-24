package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.SourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSourceTest {

    @Test
    fun onlyTheRequestedCampaignsDocumentsAreIncluded() {
        val documents = listOf(document(1, campaignId = 1), document(2, campaignId = 2))
        val ocr = mapOf(1L to ocr(1, mapOf(0 to listOf("A text"))), 2L to ocr(2, mapOf(0 to listOf("B secret"))))

        val source = AnalysisSource.assemble(campaignId = 1, documents = documents, ocrResults = ocr)

        assertEquals(listOf("A text"), source.blocks.map { it.text })
        assertTrue(source.blocks.all { it.sourceRef.documentId == 1L })
    }

    @Test
    fun orderingIsDeterministicByDocumentThenPageThenBlockRegardlessOfInputOrder() {
        val documents = listOf(document(7, 1), document(3, 1))
        val ocr = mapOf(
            7L to ocr(7, mapOf(1 to listOf("d7p1b0"), 0 to listOf("d7p0b0", "d7p0b1"))),
            3L to ocr(3, mapOf(0 to listOf("d3p0b0")))
        )

        val first = AnalysisSource.assemble(1, documents, ocr)
        val second = AnalysisSource.assemble(1, documents.reversed(), ocr)

        assertEquals(listOf("d3p0b0", "d7p0b0", "d7p0b1", "d7p1b0"), first.blocks.map { it.text })
        assertEquals(first.refMap, second.refMap)
        assertEquals(first.blocks.map { it.refId }, second.blocks.map { it.refId })
    }

    @Test
    fun refMapResolvesEveryRequestLocalIdToDocumentPageAndBlockOrder() {
        val source = AnalysisSource.assemble(
            1,
            listOf(document(5, 1)),
            mapOf(5L to ocr(5, mapOf(0 to listOf("a", "b"), 1 to listOf("c"))))
        )

        assertEquals(
            mapOf(
                "B1" to SourceRef(5, 0, 0),
                "B2" to SourceRef(5, 0, 1),
                "B3" to SourceRef(5, 1, 0)
            ),
            source.refMap
        )
    }

    @Test
    fun emptyPagesAreExcludedLowTextPagesKeptAndMarkedAndUnprocessedDocumentsCounted() {
        val source = AnalysisSource.assemble(
            1,
            listOf(document(1, 1, pages = 3), document(2, 1)),
            mapOf(1L to ocr(1, mapOf(0 to listOf(LONG_TEXT), 1 to listOf("tiny"), 2 to listOf("  "))))
        )

        assertEquals(listOf(0, 1), source.pages.map { it.pageIndex })
        assertFalse(source.pages[0].isLowText)
        assertTrue(source.pages[1].isLowText)
        assertEquals(1, source.lowTextPageCount)
        assertEquals(1, source.emptyPageCount)
        assertEquals(1, source.documentsWithoutOcr)
    }

    @Test
    fun fingerprintChangesWhenADocumentIsAddedOrOcrIsRerun() {
        val docs = listOf(document(1, 1))
        val ocr = mapOf(1L to ocr(1, mapOf(0 to listOf(LONG_TEXT))))
        val base = AnalysisSource.assemble(1, docs, ocr).fingerprint

        val withNewDocument = AnalysisSource.assemble(1, docs + document(2, 1), ocr).fingerprint
        val reprocessed = AnalysisSource.assemble(
            1, docs, mapOf(1L to ocr(1, mapOf(0 to listOf(LONG_TEXT)), processedAt = NOW.plusSeconds(60)))
        ).fingerprint

        assertNotEquals(base, withNewDocument)
        assertNotEquals(base, reprocessed)
        assertEquals(base, AnalysisSource.assemble(1, docs, ocr).fingerprint)
    }
}
