package com.example.cramrescue.domain.usecase.material

import com.example.cramrescue.domain.analysis.FakeCampaignRepository
import com.example.cramrescue.domain.analysis.FakeCredentialRepository
import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisClient
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisRepository
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.campaign
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.usecase.analysis.GenerateAnalysisResult
import com.example.cramrescue.domain.usecase.analysis.GenerateCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSourceDocumentUseCaseTest {

    // Campaign 1: gallery (1), camera (2), 3-page PDF (3). Campaign 2: gallery (4).
    private val ocrRepository = FakeOcrRepository(
        mutableMapOf(
            1L to ocr(1, mapOf(0 to listOf(LONG_TEXT))),
            2L to ocr(2, mapOf(0 to listOf(LONG_TEXT))),
            3L to ocr(3, mapOf(0 to listOf(LONG_TEXT), 1 to listOf(LONG_TEXT), 2 to listOf(LONG_TEXT))),
            4L to ocr(4, mapOf(0 to listOf("Campaign 2 material")))
        )
    )
    private val documents = FakeSourceDocumentRepository(
        mutableListOf(
            document(1, 1).copy(sourceType = SourceType.GALLERY),
            document(2, 1).copy(sourceType = SourceType.CAMERA),
            document(3, 1, name = "notes.pdf", pages = 3),
            document(4, 2)
        ),
        onDeleted = { ocrRepository.results.remove(it) }
    )
    private val campaigns = FakeCampaignRepository(listOf(campaign(1), campaign(2)))
    private val client = FakeStudyAnalysisClient()
    private val analyses = FakeStudyAnalysisRepository()
    private val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
    private val generate = GenerateCampaignAnalysisUseCase(
        campaigns, getSource, FakeCredentialRepository(), client, analyses, Clock.fixed(NOW, ZoneOffset.UTC)
    )
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)

    private val deleteDocument = DeleteSourceDocumentUseCase(documents)

    @Test
    fun deletesGalleryCameraAndPdfDocumentsOfTheirCampaign() = runBlocking {
        listOf(1L, 2L, 3L).forEach { id ->
            assertEquals(DeleteDocumentResult.Deleted, deleteDocument(campaignId = 1, documentId = id))
        }

        assertTrue(documents.getDocumentsForCampaign(1).isEmpty())
        // Only campaign 2's document and OCR remain.
        assertEquals(listOf(4L), documents.documents.map { it.id })
        assertEquals(setOf(4L), ocrRepository.results.keys)
        // The campaign itself is untouched.
        assertNotNull(campaigns.getCampaignById(1))
    }

    @Test
    fun deletingOneDocumentKeepsTheOthers() = runBlocking {
        assertEquals(DeleteDocumentResult.Deleted, deleteDocument(1, 3))
        assertEquals(listOf(1L, 2L), documents.getDocumentsForCampaign(1).map { it.id })
    }

    @Test
    fun campaignCannotDeleteAnotherCampaignsDocument() = runBlocking {
        assertEquals(DeleteDocumentResult.NotInCampaign, deleteDocument(campaignId = 1, documentId = 4))
        assertEquals(0, documents.deleteCalls)
        assertTrue(documents.documents.any { it.id == 4L })
    }

    @Test
    fun missingDocumentIsReportedNotDeleted() = runBlocking {
        assertEquals(DeleteDocumentResult.NotFound, deleteDocument(1, 999))
        assertEquals(0, documents.deleteCalls)
    }

    @Test
    fun databaseFailureIsReportedAndNothingIsRemoved() = runBlocking {
        documents.failDeletes = true
        assertEquals(DeleteDocumentResult.Failed, deleteDocument(1, 1))
        assertTrue(documents.documents.any { it.id == 1L })
    }

    @Test
    fun deletingAContributingDocumentMarksAnalysisStaleWithoutCallingGemini() = runBlocking {
        client.nextDraft = draft(item(refs = listOf("B1")))
        assertTrue(generate(1) is GenerateAnalysisResult.Success)
        assertFalse(getAnalysis(1).isStale)
        val requestsBefore = client.prompts.size

        deleteDocument(1, 3)

        val state = getAnalysis(1)
        assertTrue(state.isStale)
        // The analysis is kept (shown as stale), and deletion never contacts Gemini.
        assertNotNull(state.analysis)
        assertEquals(requestsBefore, client.prompts.size)
    }

    @Test
    fun emptyCampaignAfterDeletingEverythingCannotStartAnAnalysisRequest() = runBlocking {
        listOf(1L, 2L, 3L).forEach { deleteDocument(1, it) }

        assertEquals(GenerateAnalysisResult.NoUsableText, generate(1))
        assertTrue(client.prompts.isEmpty())
        assertNotNull(campaigns.getCampaignById(1))
    }
}
