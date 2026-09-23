package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.SourceDocumentDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.repository.SourceDocumentRepository

class SourceDocumentRepositoryImpl(
    private val sourceDocumentDao: SourceDocumentDao
) : SourceDocumentRepository {

    override suspend fun saveDocument(document: SourceDocument): Long {
        return sourceDocumentDao.insert(document.toEntity())
    }

    override suspend fun getDocumentsForCampaign(campaignId: Long): List<SourceDocument> {
        return sourceDocumentDao.getForCampaign(campaignId).map { it.toDomain() }
    }

    override suspend fun getDocumentById(documentId: Long): SourceDocument? {
        return sourceDocumentDao.getById(documentId)?.toDomain()
    }
}
