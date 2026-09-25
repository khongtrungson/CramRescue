package com.example.cramrescue.presentation.microcard

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.analysis.wordDocument
import com.example.cramrescue.domain.analysis.wordText
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Word sources are labelled by paragraph, never by a (non-existent) page. */
class CardSourceResolverWordTest {

    private val source = AnalysisSource.assemble(
        1,
        listOf(document(1, 1, name = "slides.pdf", pages = 2).copy(sourceType = SourceType.PDF), wordDocument(2, 1, name = "notes.docx")),
        mapOf(1L to ocr(1, mapOf(1 to listOf(LONG_TEXT)))),
        mapOf(2L to wordText(2, listOf("Chương 1", "Định nghĩa A", "Công thức B")))
    )
    private val resolver = CardSourceResolver(source)
    private val refs = listOf(SourceRef(1, 1, 0), SourceRef(2, 0, 1), SourceRef(2, 0, 2))

    @Test
    fun linesGroupsAndSummaryUseParagraphsForWord() {
        assertEquals(
            listOf(
                SourceLineUi("slides.pdf · Trang 2", LONG_TEXT),
                SourceLineUi("notes.docx · đoạn 2", "Định nghĩa A"),
                SourceLineUi("notes.docx · đoạn 3", "Công thức B")
            ),
            resolver.lines(refs)
        )
        val groups = resolver.groups(refs)
        assertEquals(listOf("slides.pdf · Trang 2", "notes.docx · văn bản Word"), groups.map { it.location })
        assertEquals(SourceGroupUi(2, 0, "notes.docx · văn bản Word", 2, listOf("Định nghĩa A", "Công thức B")), groups[1])
        assertEquals("slides.pdf · tr. 2 (1 khối); notes.docx · văn bản Word (2 đoạn)", resolver.summary(refs))
    }
}
