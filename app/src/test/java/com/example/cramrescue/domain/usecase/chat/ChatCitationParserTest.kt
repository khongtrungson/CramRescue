package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.ocr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCitationParserTest {

    private val source = AnalysisSource.assemble(
        campaignId = 1L,
        documents = listOf(
            document(10L, campaignId = 1L, name = "Slide Chuong 1", pages = 10),
            document(20L, campaignId = 1L, name = "De Cuong On Tap", pages = 5)
        ),
        ocrResults = mapOf(
            10L to ocr(10L, (0..9).associateWith { listOf("Content slide $it") }),
            20L to ocr(20L, (0..4).associateWith { listOf("Content decuong $it") })
        )
    )

    @Test
    fun parsesStandardCitationsCorrectly() {
        val text = """
            Theo nguyên lý thứ nhất [Slide Chuong 1 - Trang 4], các yếu tố sản xuất gồm...
            Ngoài ra, công thức tổng quát được nêu tại [De Cuong On Tap - Trang 2].
        """.trimIndent()

        val citations = ChatCitationParser.parseCitations(text, source)

        assertEquals(2, citations.size)
        assertEquals(10L, citations[0].documentId)
        assertEquals("Slide Chuong 1", citations[0].documentLabel)
        assertEquals(3, citations[0].pageIndex) // 1-based page 4 -> index 3

        assertEquals(20L, citations[1].documentId)
        assertEquals("De Cuong On Tap", citations[1].documentLabel)
        assertEquals(1, citations[1].pageIndex) // 1-based page 2 -> index 1
    }

    @Test
    fun ignoresUnmatchedBrackets() {
        val text = "Công thức [1] và ghi chú [A] không phải là trích dẫn tài liệu."
        val citations = ChatCitationParser.parseCitations(text, source)
        assertTrue(citations.isEmpty())
    }

    @Test
    fun dedupesIdenticalCitations() {
        val text = "Ý 1 [Slide Chuong 1 - Trang 4] và ý 2 cũng từ [Slide Chuong 1 - Trang 4]."
        val citations = ChatCitationParser.parseCitations(text, source)
        assertEquals(1, citations.size)
    }
}
