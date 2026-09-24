package com.example.cramrescue.presentation.microcard

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.model.SourceRef

/** One card source, resolved against the campaign's current OCR for display. */
data class SourceLineUi(
    /** e.g. "notes.pdf · Trang 2". */
    val location: String,
    /** The OCR block text (Constraint 6.1 source snippet), or null if the block no longer exists. */
    val snippet: String?
)

/** Resolves card source refs against the current material; never shows a stale ref as valid. */
internal class CardSourceResolver(source: AnalysisSource) {
    private val labels = source.pages.associate { it.documentId to it.documentLabel }
    private val textByRef = source.blocks.associate { it.sourceRef to it.text }

    fun resolved(ref: SourceRef): Boolean = ref in textByRef

    fun lines(refs: List<SourceRef>): List<SourceLineUi> = refs.map { ref ->
        SourceLineUi(
            location = "${labels[ref.documentId] ?: "Tài liệu #${ref.documentId}"} · Trang ${ref.pageIndex + 1}",
            snippet = textByRef[ref]
        )
    }

    /** Compact label for a list item, e.g. "notes.pdf · tr. 1, 2 (4 khối)". */
    fun summary(refs: List<SourceRef>): String {
        if (refs.isEmpty()) return "tài liệu nguồn đã bị xóa khỏi chiến dịch"
        return refs.groupBy { it.documentId }.entries.joinToString("; ") { (documentId, documentRefs) ->
            val pages = documentRefs.map { it.pageIndex + 1 }.distinct().sorted().joinToString(", ")
            "${labels[documentId] ?: "Tài liệu #$documentId"} · tr. $pages (${documentRefs.size} khối)"
        }
    }
}
