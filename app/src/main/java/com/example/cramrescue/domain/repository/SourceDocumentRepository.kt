package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.SourceDocument

interface SourceDocumentRepository {
    suspend fun saveDocument(document: SourceDocument): Long

    /** Only that campaign's documents — never another campaign's. */
    suspend fun getDocumentsForCampaign(campaignId: Long): List<SourceDocument>

    suspend fun getDocumentById(documentId: Long): SourceDocument?

    /**
     * Deletes the document, its OCR and any analysis source refs to it, plus the app-owned file
     * copy. Scoped: nothing happens unless the document belongs to [campaignId].
     * @return true if a document was deleted.
     */
    suspend fun deleteDocument(campaignId: Long, documentId: Long): Boolean
}
