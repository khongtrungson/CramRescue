package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.OcrDao
import com.example.cramrescue.data.mapper.toDomain
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.repository.OcrRepository

class OcrRepositoryImpl(
    private val ocrDao: OcrDao
) : OcrRepository {

    override suspend fun saveResult(result: OcrResult) {
        ocrDao.replaceOcrResult(
            documentId = result.documentId,
            pageResults = result.pages.map { it.toEntity() },
            blocks = result.pages.flatMap { page -> page.blocks.map { it.toEntity() } }
        )
    }

    override suspend fun getResultForDocument(documentId: Long): OcrResult? {
        val pageEntities = ocrDao.getPageResultsForDocument(documentId)
        if (pageEntities.isEmpty()) return null

        val blocksByPage = ocrDao.getBlocksForDocument(documentId)
            .map { it.toDomain() }
            .groupBy { it.pageIndex }

        return OcrResult(
            documentId = documentId,
            pages = pageEntities.map { page -> page.toDomain(blocksByPage[page.pageIndex].orEmpty()) }
        )
    }
}
