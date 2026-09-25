package com.example.cramrescue.domain.analysis

import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAnalysisSourceTest {

    private val pdf = document(1, 1, name = "slides.pdf", pages = 2).copy(sourceType = SourceType.PDF)
    private val word = wordDocument(2, 1, name = "notes.docx")
    private val pdfOcr = ocr(1, mapOf(0 to listOf(LONG_TEXT), 1 to listOf("$LONG_TEXT 2")))

    @Test
    fun wordParagraphsBecomeOneLogicalSectionWithExactParagraphRefs() {
        // Stored order 3 is blank text (kept by identity, skipped for analysis).
        val text = wordText(2, listOf("Chương 1", "Định nghĩa A", "Công thức B", " ", "Ví dụ C"),
            kinds = listOf(TextBlockKind.HEADING) + List(4) { TextBlockKind.PARAGRAPH })

        val source = AnalysisSource.assemble(1, listOf(pdf, word), mapOf(1L to pdfOcr), mapOf(2L to text))

        val section = source.pages.single { it.isWord }
        assertEquals(0, section.pageIndex)
        assertEquals(2L, section.documentId)
        assertEquals(listOf("B3", "B4", "B5", "B6"), section.blocks.map { it.refId }) // after the PDF's B1, B2
        assertEquals(listOf(0, 1, 2, 4).map { SourceRef(2, 0, it) }, section.blocks.map { it.sourceRef })
        assertEquals("Ví dụ C", section.blocks.last().text)

        assertEquals(2, source.analyzedPageCount) // PDF pages only; Word is not a page
        assertEquals(1, source.wordDocumentCount)
        assertEquals(0, source.documentsWithoutOcr)
        assertTrue(source.hasUsableText)
    }

    @Test
    fun pageEquivalentsUseFiveHundredWordsPerPageRoundedUp() {
        fun source(words: Int) = AnalysisSource.assemble(
            1, listOf(pdf, word), mapOf(1L to pdfOcr),
            mapOf(2L to wordText(2, listOf(List(words) { "từ" }.joinToString(" "))))
        )
        assertEquals(2 + 1, source(1).pageEquivalents)
        assertEquals(2 + 1, source(500).pageEquivalents)
        assertEquals(2 + 2, source(501).pageEquivalents)
        assertEquals(2 + 20, source(10_000).pageEquivalents)
    }

    @Test
    fun wordWithoutTextIsCountedAsUnprocessedNotAnalyzed() {
        val missing = AnalysisSource.assemble(1, listOf(word), emptyMap(), emptyMap())
        assertEquals(1, missing.documentsWithoutOcr)
        assertFalse(missing.hasUsableText)
        assertEquals(0, missing.wordDocumentCount)
    }

    @Test
    fun fingerprintForPhotoAndPdfCampaignsIsUnchangedAndWordUsesItsExtractionTime() {
        // Existing analyses of image/PDF campaigns must stay current after the upgrade.
        assertEquals(
            AnalysisSource.fingerprint(listOf(pdf), mapOf(1L to pdfOcr)),
            AnalysisSource.fingerprint(listOf(pdf), mapOf(1L to pdfOcr), emptyMap())
        )
        val text = wordText(2, listOf("Một"))
        val withWord = AnalysisSource.fingerprint(listOf(pdf, word), mapOf(1L to pdfOcr), mapOf(2L to text))
        assertTrue(withWord.endsWith("|2:w@${NOW.toEpochMilli()}"))
        assertEquals(AnalysisSource.fingerprint(listOf(pdf), mapOf(1L to pdfOcr)) + "|2:none",
            AnalysisSource.fingerprint(listOf(pdf, word), mapOf(1L to pdfOcr), emptyMap()))
        // Re-import (new extraction time) changes the fingerprint.
        assertNotEquals(withWord, AnalysisSource.fingerprint(listOf(pdf, word), mapOf(1L to pdfOcr),
            mapOf(2L to wordText(2, listOf("Một"), extractedAt = NOW.plusSeconds(5)))))
    }

    @Test
    fun useCaseLoadsWordTextWithoutTouchingOcr() = runBlocking {
        val ocrRepository = FakeOcrRepository(mutableMapOf(1L to pdfOcr, 2L to pdfOcr.copy(documentId = 2)))
        val texts = FakeDocumentTextRepository(mutableMapOf(2L to wordText(2, listOf("Đoạn Word"))))
        val source = GetAnalysisSourceUseCase(FakeSourceDocumentRepository(mutableListOf(pdf, word)), ocrRepository, texts)(1)

        // A stray OCR row for the Word id is ignored: Word text comes only from extraction.
        assertEquals(listOf("Đoạn Word"), source.pages.single { it.isWord }.blocks.map { it.text })
    }

    @Test
    fun promptLabelsWordAsParagraphTextNeverAsAPage() {
        val source = AnalysisSource.assemble(1, listOf(word), emptyMap(), mapOf(2L to wordText(2, listOf("Đoạn một"))))
        val input = StudyAnalysisPromptBuilder.build(campaign(1), source, NOW).input
        assertTrue(input.contains("# Tài liệu 1: notes.docx — văn bản Word (đoạn văn)"))
        assertFalse(input.contains("Trang"))
        assertTrue(input.contains("[B1] Đoạn một"))
    }
}
