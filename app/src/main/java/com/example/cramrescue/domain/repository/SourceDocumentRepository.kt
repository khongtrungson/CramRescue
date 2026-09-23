package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.SourceDocument

interface SourceDocumentRepository {
    suspend fun saveDocument(document: SourceDocument): Long

    /** Only that campaign's documents — never another campaign's. */
    suspend fun getDocumentsForCampaign(campaignId: Long): List<SourceDocument>
}
