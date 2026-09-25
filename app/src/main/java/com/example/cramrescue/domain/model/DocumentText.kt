package com.example.cramrescue.domain.model

import java.time.Instant

// requirements.md FR-3: Word text is limited to 10,000 words, which the PRD equates to ~20 printed
// pages ([ASSUMPTION §4.1/FR-3]) — i.e. 500 words per page-equivalent for page ceilings.
const val MAX_WORD_DOCUMENT_WORDS = 10_000
const val WORDS_PER_PAGE_EQUIVALENT = 500

/**
 * Word documents have no physical pages (pagination depends on the renderer), so their text lives
 * in ONE logical section. Source refs into a Word document use this pageIndex; it is never shown
 * or treated as a page number (FR-14: Word sources are shown as a text snippet).
 */
const val WORD_LOGICAL_SECTION_INDEX = 0

enum class TextBlockKind { HEADING, PARAGRAPH, TABLE_ROW }

/** One paragraph-level unit of extracted Word text, in document reading order. */
data class DocumentTextBlock(
    val documentId: Long,
    /** 0-based position in reading order — the block's stable identity within the document. */
    val orderIndex: Int,
    val kind: TextBlockKind,
    val text: String
)

/**
 * Paragraph-segmented text extracted locally from a Word document (FR-3). No page numbers and no
 * bounding boxes exist for it — deliberately, rather than inventing them.
 */
data class DocumentText(
    val documentId: Long,
    val extractedAt: Instant,
    val blocks: List<DocumentTextBlock>
) {
    val wordCount: Int get() = blocks.sumOf { countWords(it.text) }
    val pageEquivalents: Int get() = (wordCount + WORDS_PER_PAGE_EQUIVALENT - 1) / WORDS_PER_PAGE_EQUIVALENT

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** Words = whitespace-separated tokens containing a letter or digit. */
        fun countWords(text: String): Int = text.split(WHITESPACE).count { token -> token.any(Char::isLetterOrDigit) }
    }
}
