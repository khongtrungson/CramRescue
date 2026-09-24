package com.example.cramrescue.domain.usecase.analysis

import com.example.cramrescue.domain.analysis.FAKE_API_KEY
import com.example.cramrescue.domain.analysis.FakeCampaignRepository
import com.example.cramrescue.domain.analysis.FakeCredentialRepository
import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisClient
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisRepository
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.RemoteFailure
import com.example.cramrescue.domain.analysis.campaign
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.model.MAX_DOCUMENT_PAGES
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateCampaignAnalysisUseCaseTest {

    private val documents = FakeSourceDocumentRepository(
        mutableListOf(document(1, campaignId = 1, name = "a.pdf"), document(2, campaignId = 2, name = "b.pdf"))
    )
    private val ocrRepository = FakeOcrRepository(
        mutableMapOf(
            1L to ocr(1, mapOf(0 to listOf(LONG_TEXT, "Campaign A only fact"))),
            2L to ocr(2, mapOf(0 to listOf("Campaign B confidential fact")))
        )
    )
    private val credentials = FakeCredentialRepository()
    private val client = FakeStudyAnalysisClient()
    private val analyses = FakeStudyAnalysisRepository()
    private val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)

    private val useCase = GenerateCampaignAnalysisUseCase(
        FakeCampaignRepository(listOf(campaign(1), campaign(2))),
        getSource,
        credentials,
        client,
        analyses,
        Clock.fixed(NOW, ZoneOffset.UTC)
    )
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)

    @Test
    fun successValidatesAndPersistsForTheRequestedCampaign() = runBlocking {
        client.nextDraft = draft(item(refs = listOf("B1", "B2")))

        val result = useCase(1)

        assertTrue(result is GenerateAnalysisResult.Success)
        val stored = analyses.getAnalysisForCampaign(1)!!
        assertEquals(1L, stored.campaignId)
        assertEquals("fake-model", stored.modelId)
        assertNull(analyses.getAnalysisForCampaign(2))
        assertEquals(listOf(FAKE_API_KEY), client.keysUsed)
    }

    @Test
    fun campaignARequestNeverContainsCampaignBMaterial() = runBlocking {
        client.nextDraft = draft(item())

        useCase(1)

        val input = client.prompts.single().input
        assertTrue(input.contains("Campaign A only fact"))
        assertFalse(input.contains("Campaign B confidential fact"))
        assertFalse(input.contains("b.pdf"))
    }

    @Test
    fun regenerationReplacesRatherThanDuplicates() = runBlocking {
        client.nextDraft = draft(item(title = "First"))
        useCase(1)
        client.nextDraft = draft(item(title = "Second"))
        useCase(1)

        assertEquals(1, analyses.analyses.size)
        assertEquals(listOf("Second"), analyses.getAnalysisForCampaign(1)!!.priorities.map { it.title })
    }

    @Test
    fun failedRegenerationPreservesPreviousAnalysis() = runBlocking {
        client.nextDraft = draft(item(title = "Original"))
        useCase(1)
        val original = analyses.getAnalysisForCampaign(1)

        listOf(
            RemoteFailure.RateLimited(30),
            RemoteFailure.InvalidCredential,
            RemoteFailure.Network,
            RemoteFailure.Timeout,
            RemoteFailure.Service,
            RemoteFailure.MalformedResponse
        ).forEach { failure ->
            client.nextFailure = failure
            assertEquals(GenerateAnalysisResult.Failed(failure), useCase(1))
            assertSame(original, analyses.getAnalysisForCampaign(1))
        }
        assertEquals(1, analyses.replaceCalls)
    }

    @Test
    fun rateLimitIsReportedDistinctlyWithRetryAfter() = runBlocking {
        client.nextFailure = RemoteFailure.RateLimited(retryAfterSeconds = 42)
        assertEquals(GenerateAnalysisResult.Failed(RemoteFailure.RateLimited(42)), useCase(1))
    }

    @Test
    fun invalidAiOutputIsNotPersistedAndKeepsPreviousAnalysis() = runBlocking {
        client.nextDraft = draft(item(title = "Original"))
        useCase(1)
        val original = analyses.getAnalysisForCampaign(1)

        client.nextDraft = draft(item(refs = listOf("B99")))
        val result = useCase(1)

        assertTrue(result is GenerateAnalysisResult.InvalidAiOutput)
        assertSame(original, analyses.getAnalysisForCampaign(1))
    }

    @Test
    fun missingCredentialStopsBeforeAnyNetworkCall() = runBlocking {
        credentials.key = null
        assertEquals(GenerateAnalysisResult.MissingCredential, useCase(1))
        assertTrue(client.prompts.isEmpty())
    }

    @Test
    fun campaignWithoutOcrTextIsNotSent() = runBlocking {
        ocrRepository.results.remove(1L)
        assertEquals(GenerateAnalysisResult.NoUsableText, useCase(1))
        assertTrue(client.prompts.isEmpty())
    }

    @Test
    fun campaignPageCeilingIsEnforced() = runBlocking {
        ocrRepository.results[1L] = ocr(1, (0..MAX_DOCUMENT_PAGES).associateWith { listOf(LONG_TEXT) })
        assertEquals(GenerateAnalysisResult.TooManyPages(MAX_DOCUMENT_PAGES + 1), useCase(1))
        assertTrue(client.prompts.isEmpty())
    }

    @Test
    fun analysisBecomesStaleWhenMaterialChanges() = runBlocking {
        client.nextDraft = draft(item())
        useCase(1)
        assertFalse(getAnalysis(1).isStale)

        documents.documents += document(3, campaignId = 1)
        assertTrue(getAnalysis(1).isStale)

        client.nextDraft = draft(item())
        useCase(1)
        assertFalse(getAnalysis(1).isStale)

        ocrRepository.results[1L] = ocr(1, mapOf(0 to listOf(LONG_TEXT)), processedAt = NOW.plusSeconds(5))
        assertTrue(getAnalysis(1).isStale)
    }

    @Test
    fun changingAnotherCampaignDoesNotStaleThisOne() = runBlocking {
        client.nextDraft = draft(item())
        useCase(1)

        documents.documents += document(4, campaignId = 2)

        assertFalse(getAnalysis(1).isStale)
    }
}
