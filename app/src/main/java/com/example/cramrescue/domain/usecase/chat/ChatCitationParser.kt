package com.example.cramrescue.domain.usecase.chat

import com.example.cramrescue.domain.analysis.AnalysisSource
import com.example.cramrescue.domain.model.ChatCitation

object ChatCitationParser {

    private val CITATION_REGEX = Regex("""\[([^\]\n]+?)(?:\s*-\s*(?:Trang|trang|Page|page|p\.)\s*(\d+))?\]""")

    fun parseCitations(text: String, source: AnalysisSource): List<ChatCitation> {
        val matches = CITATION_REGEX.findAll(text)
        val citations = mutableListOf<ChatCitation>()

        for (match in matches) {
            val labelCandidate = match.groupValues[1].trim()
            val pageStr = match.groupValues[2].trim()

            // Skip pure numbers or single character bracket references (e.g. [1], [A])
            if (labelCandidate.toIntOrNull() != null || labelCandidate.length < 2) {
                continue
            }

            // Find matching page / document in source
            val matchedPage = source.pages.firstOrNull { page ->
                page.documentLabel.equals(labelCandidate, ignoreCase = true) ||
                    (labelCandidate.length >= 4 && (page.documentLabel.contains(labelCandidate, ignoreCase = true) ||
                        labelCandidate.contains(page.documentLabel, ignoreCase = true)))
            }

            if (matchedPage != null) {
                val pageIndex = if (pageStr.isNotEmpty()) {
                    (pageStr.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                } else {
                    matchedPage.pageIndex.coerceAtLeast(0)
                }

                citations.add(
                    ChatCitation(
                        documentId = matchedPage.documentId,
                        documentLabel = matchedPage.documentLabel,
                        pageIndex = pageIndex
                    )
                )
            }
        }

        return citations.distinctBy { it.documentId to it.pageIndex }
    }
}
