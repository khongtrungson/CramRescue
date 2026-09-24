package com.example.cramrescue

import android.app.Application
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.ocr.MlKitTextRecognitionEngine
import com.example.cramrescue.data.pdf.AndroidPdfPageRenderer
import com.example.cramrescue.data.remote.GeminiStudyAnalysisClient
import com.example.cramrescue.data.repository.GeminiCredentialRepositoryImpl
import com.example.cramrescue.data.repository.MicroCardRepositoryImpl
import com.example.cramrescue.data.repository.CampaignRepositoryImpl
import com.example.cramrescue.data.repository.OcrRepositoryImpl
import com.example.cramrescue.data.repository.SourceDocumentRepositoryImpl
import com.example.cramrescue.data.repository.StudyAnalysisRepositoryImpl
import com.example.cramrescue.data.repository.StudyAttemptRepositoryImpl
import com.example.cramrescue.domain.analysis.StudyAnalysisClient
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.example.cramrescue.domain.pdf.PdfPageRenderer
import com.example.cramrescue.domain.repository.CampaignRepository
import com.example.cramrescue.domain.repository.GeminiCredentialRepository
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import com.example.cramrescue.domain.repository.StudyAnalysisRepository
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import com.example.cramrescue.domain.usecase.analysis.GenerateCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.material.DeleteSourceDocumentUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import com.example.cramrescue.domain.usecase.ocr.ProcessDocumentOcrUseCase
import com.example.cramrescue.domain.usecase.recall.RecordRecallAssessmentUseCase
import com.example.cramrescue.domain.usecase.settings.CheckStoredGeminiCredentialUseCase
import com.example.cramrescue.domain.usecase.settings.SaveGeminiCredentialUseCase

class CramRescueApplication : Application() {

    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val campaignRepository: CampaignRepository by lazy {
        CampaignRepositoryImpl(database.campaignDao())
    }

    val sourceDocumentRepository: SourceDocumentRepository by lazy {
        SourceDocumentRepositoryImpl(database.sourceDocumentDao(), materialFileStore)
    }

    val ocrRepository: OcrRepository by lazy {
        OcrRepositoryImpl(database.ocrDao())
    }

    val materialFileStore: MaterialFileStore by lazy { MaterialFileStore(this) }

    val textRecognitionEngine: TextRecognitionEngine by lazy { MlKitTextRecognitionEngine(this) }

    val pdfPageRenderer: PdfPageRenderer by lazy { AndroidPdfPageRenderer(materialFileStore) }

    val processDocumentOcrUseCase: ProcessDocumentOcrUseCase by lazy {
        ProcessDocumentOcrUseCase(
            sourceDocumentRepository,
            ocrRepository,
            textRecognitionEngine,
            pdfPageRenderer
        )
    }

    val deleteSourceDocumentUseCase: DeleteSourceDocumentUseCase by lazy {
        DeleteSourceDocumentUseCase(sourceDocumentRepository)
    }

    // Milestone 7: BYOK + Gemini study analysis.
    val studyAnalysisRepository: StudyAnalysisRepository by lazy {
        StudyAnalysisRepositoryImpl(database.studyAnalysisDao())
    }

    val geminiCredentialRepository: GeminiCredentialRepository by lazy { GeminiCredentialRepositoryImpl(this) }

    val studyAnalysisClient: StudyAnalysisClient by lazy { GeminiStudyAnalysisClient() }

    private val getAnalysisSourceUseCase: GetAnalysisSourceUseCase by lazy {
        GetAnalysisSourceUseCase(sourceDocumentRepository, ocrRepository)
    }

    val generateCampaignAnalysisUseCase: GenerateCampaignAnalysisUseCase by lazy {
        GenerateCampaignAnalysisUseCase(
            campaignRepository,
            getAnalysisSourceUseCase,
            geminiCredentialRepository,
            studyAnalysisClient,
            studyAnalysisRepository
        )
    }

    val getCampaignAnalysisUseCase: GetCampaignAnalysisUseCase by lazy {
        GetCampaignAnalysisUseCase(studyAnalysisRepository, getAnalysisSourceUseCase)
    }

    val saveGeminiCredentialUseCase: SaveGeminiCredentialUseCase by lazy {
        SaveGeminiCredentialUseCase(geminiCredentialRepository, studyAnalysisClient)
    }

    val checkStoredGeminiCredentialUseCase: CheckStoredGeminiCredentialUseCase by lazy {
        CheckStoredGeminiCredentialUseCase(geminiCredentialRepository, studyAnalysisClient)
    }

    // Milestone 8: MicroCards, built locally from the current analysis (no AI call).
    val microCardRepository: MicroCardRepository by lazy {
        MicroCardRepositoryImpl(database.microCardDao())
    }

    val getMicroCardsUseCase: GetMicroCardsUseCase by lazy {
        GetMicroCardsUseCase(microCardRepository, getCampaignAnalysisUseCase)
    }

    val createMicroCardsUseCase: CreateMicroCardsUseCase by lazy {
        CreateMicroCardsUseCase(getCampaignAnalysisUseCase, microCardRepository)
    }

    // Milestone 9: Active Recall. Fully local — no dependency on the Gemini client or the key.
    val studyAttemptRepository: StudyAttemptRepository by lazy {
        StudyAttemptRepositoryImpl(database.studyAttemptDao())
    }

    val recordRecallAssessmentUseCase: RecordRecallAssessmentUseCase by lazy {
        RecordRecallAssessmentUseCase(studyAttemptRepository)
    }
}
