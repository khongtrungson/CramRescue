package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.DocumentText
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.word.ExtractedTextBlock
import java.time.Instant

interface DocumentTextRepository {
    /**
     * Saves a Word [document] AND its extracted [blocks] in one transaction, so a failure can
     * never leave a document without its text (or text without its document). Returns the id.
     */
    suspend fun saveWordDocument(document: SourceDocument, blocks: List<ExtractedTextBlock>, extractedAt: Instant): Long

    /** The document's extracted text, or null if it has none (not a Word document). */
    suspend fun getText(documentId: Long): DocumentText?
}
