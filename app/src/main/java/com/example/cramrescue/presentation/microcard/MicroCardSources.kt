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

/** One document page cited by a card: opens the Source Grounding viewer for exactly this page. */
data class SourceGroupUi(
    val documentId: Long,
    val pageIndex: Int,
    /** e.g. "1.pdf · Trang 2". */
    val location: String,
    val blockCount: Int,
    /** OCR text of the cited blocks (context only; null = block no longer in current OCR). */
    val snippets: List<String?>
)

/** Resolves card source refs against the current material; never shows a stale ref as valid. */
internal class CardSourceResolver(source: AnalysisSource) {
    private val labels = source.pages.associate { it.documentId to it.documentLabel }
    private val wordDocuments = source.pages.filter { it.isWord }.map { it.documentId }.toSet()
    private val textByRef = source.blocks.associate { it.sourceRef to it.text }

    fun resolved(ref: SourceRef): Boolean = ref in textByRef

    fun lines(refs: List<SourceRef>): List<SourceLineUi> = refs.map { ref ->
        SourceLineUi(
            location = "${labels[ref.documentId] ?: "Tài liệu #${ref.documentId}"} · " +
                if (ref.documentId in wordDocuments) "đoạn ${ref.blockOrderIndex + 1}" else "Trang ${ref.pageIndex + 1}",
            snippet = textByRef[ref]
        )
    }

    /** Refs grouped by document + page, in citation order. */
    fun groups(refs: List<SourceRef>): List<SourceGroupUi> =
        refs.groupBy { it.documentId to it.pageIndex }.map { (key, groupRefs) ->
            val (documentId, pageIndex) = key
            SourceGroupUi(
                documentId = documentId,
                pageIndex = pageIndex,
                location = "${labels[documentId] ?: "Tài liệu #$documentId"} · " +
                    if (documentId in wordDocuments) "văn bản Word" else "Trang ${pageIndex + 1}",
                blockCount = groupRefs.size,
                snippets = groupRefs.map { textByRef[it] }
            )
        }

    /** Compact label for a list item, e.g. "notes.pdf · tr. 1, 2 (4 khối)". */
    fun summary(refs: List<SourceRef>): String {
        if (refs.isEmpty()) return "tài liệu nguồn đã bị xóa khỏi chiến dịch"
        return refs.groupBy { it.documentId }.entries.joinToString("; ") { (documentId, documentRefs) ->
            val label = labels[documentId] ?: "Tài liệu #$documentId"
            if (documentId in wordDocuments) return@joinToString "$label · văn bản Word (${documentRefs.size} đoạn)"
            val pages = documentRefs.map { it.pageIndex + 1 }.distinct().sorted().joinToString(", ")
            "$label · tr. $pages (${documentRefs.size} khối)"
        }
    }
}
