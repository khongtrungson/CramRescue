package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.OcrResult

interface OcrRepository {
    /** Replaces any previous OCR result for [OcrResult.documentId] atomically (reprocessing). */
    suspend fun saveResult(result: OcrResult)

    suspend fun getResultForDocument(documentId: Long): OcrResult?
}
