package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.dao.SourceDocumentDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.repository.SourceDocumentRepository

class SourceDocumentRepositoryImpl(
    private val sourceDocumentDao: SourceDocumentDao,
    private val materialFileStore: MaterialFileStore
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

    // Consistency policy: the Room transaction runs first; the file is removed only after it has
    // committed. A DB failure therefore leaves both row and file intact, and a file-cleanup
    // failure can at worst leave an unreferenced file in app-private storage — never a row that
    // points at a missing file.
    override suspend fun deleteDocument(campaignId: Long, documentId: Long): Boolean {
        val deleted = sourceDocumentDao.deleteDocumentGraph(campaignId, documentId) ?: return false
        materialFileStore.deleteOwnedFile(deleted.localPath)
        return true
    }
}
