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
        ocrDao.replaceOcrResult(result.toEntity(), result.blocks.map { it.toEntity() })
    }

    override suspend fun getResultForDocument(documentId: Long): OcrResult? {
        val resultEntity = ocrDao.getResult(documentId) ?: return null
        val blocks = ocrDao.getBlocksForDocument(documentId).map { it.toDomain() }
        return resultEntity.toDomain(blocks)
    }
}
