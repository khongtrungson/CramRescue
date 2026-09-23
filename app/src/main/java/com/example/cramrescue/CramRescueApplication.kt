package com.example.cramrescue

import android.app.Application
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.ocr.MlKitTextRecognitionEngine
import com.example.cramrescue.data.repository.CampaignRepositoryImpl
import com.example.cramrescue.data.repository.OcrRepositoryImpl
import com.example.cramrescue.data.repository.SourceDocumentRepositoryImpl
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import com.example.cramrescue.domain.usecase.ocr.ProcessDocumentOcrUseCase

class CramRescueApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val campaignRepository: CampaignRepository by lazy {
        CampaignRepositoryImpl(database.campaignDao())
    }

    val sourceDocumentRepository: SourceDocumentRepository by lazy {
        SourceDocumentRepositoryImpl(database.sourceDocumentDao())
    }

    val ocrRepository: OcrRepository by lazy {
        OcrRepositoryImpl(database.ocrDao())
    }

    val materialFileStore: MaterialFileStore by lazy { MaterialFileStore(this) }

    val textRecognitionEngine: TextRecognitionEngine by lazy { MlKitTextRecognitionEngine(this) }

    val processDocumentOcrUseCase: ProcessDocumentOcrUseCase by lazy {
        ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, textRecognitionEngine)
    }
}
