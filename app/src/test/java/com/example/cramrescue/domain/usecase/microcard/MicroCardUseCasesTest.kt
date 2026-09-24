package com.example.cramrescue.domain.usecase.microcard

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
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.usecase.analysis.GenerateAnalysisResult
import com.example.cramrescue.domain.usecase.analysis.GenerateCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.material.DeleteSourceDocumentUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeMicroCardRepository : MicroCardRepository {
    val cards = mutableMapOf<Long, List<MicroCard>>()
    var replaceCalls = 0
    var failReplace = false
    private var nextId = 1L

    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        replaceCalls++
        if (failReplace) throw IllegalStateException("simulated database failure")
        this.cards[campaignId] = cards.map { it.copy(id = nextId++) }
    }

    override suspend fun getCardsForCampaign(campaignId: Long) = cards[campaignId].orEmpty()
    override suspend fun getCard(campaignId: Long, cardId: Long) = cards[campaignId]?.firstOrNull { it.id == cardId }
}

class MicroCardUseCasesTest {

    private val ocrRepository = FakeOcrRepository(
        mutableMapOf(
            1L to ocr(1, mapOf(0 to listOf(LONG_TEXT, "Campaign A fact"))),
            2L to ocr(2, mapOf(0 to listOf("Campaign B fact")))
        )
    )
    private val documents = FakeSourceDocumentRepository(
        mutableListOf(document(1, 1, name = "a.pdf"), document(2, 2, name = "b.pdf")),
        onDeleted = { ocrRepository.results.remove(it) }
    )
    private val client = FakeStudyAnalysisClient()
    private val credentials = FakeCredentialRepository()
    private val analyses = FakeStudyAnalysisRepository()
    private val cardRepository = FakeMicroCardRepository()
    private val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)

    private fun analyzeAt(time: Instant, campaignId: Long = 1): GenerateAnalysisResult = runBlocking {
        GenerateCampaignAnalysisUseCase(
            FakeCampaignRepository(listOf(campaign(1), campaign(2))),
            getSource, credentials, client, analyses, Clock.fixed(time, ZoneOffset.UTC)
        )(campaignId)
    }

    private val createCards = CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))
    private val getCards = GetMicroCardsUseCase(cardRepository, getAnalysis)

    @Test
    fun cardsAreBuiltLocallyWithoutAnyGeminiRequestOrCredential() = runBlocking {
        client.nextDraft = draft(item(title = "Red", refs = listOf("B1")), item(title = "Yellow", triage = "CODE_YELLOW", refs = listOf("B2")))
        analyzeAt(NOW)
        val requestsAfterAnalysis = client.prompts.size
        credentials.key = null

        assertEquals(CreateMicroCardsResult.Created(2), createCards(1))
        getCards(1)
        getCards(1)

        // Creating and opening cards never contacts Gemini, and needs no key.
        assertEquals(requestsAfterAnalysis, client.prompts.size)
        assertEquals(0, client.verifyCalls)
        val state = getCards(1)
        assertEquals(listOf("Red", "Yellow"), state.cards.map { it.title })
        assertFalse(state.cardsStale)
    }

    @Test
    fun campaignsNeverShareCards() = runBlocking {
        client.nextDraft = draft(item(title = "A card", refs = listOf("B2")))
        analyzeAt(NOW, campaignId = 1)
        client.nextDraft = draft(item(title = "B card", refs = listOf("B1")))
        analyzeAt(NOW, campaignId = 2)

        createCards(1)
        createCards(2)

        assertEquals(listOf("A card"), getCards(1).cards.map { it.title })
        assertTrue(getCards(1).cards.flatMap { it.sourceRefs }.all { it.documentId == 1L })
        assertEquals(listOf("B card"), getCards(2).cards.map { it.title })
        assertEquals(listOf(SourceRef(2, 0, 0)), getCards(2).cards.single().sourceRefs)
    }

    @Test
    fun noAnalysisMeansNoCards() = runBlocking {
        assertEquals(CreateMicroCardsResult.NoAnalysis, createCards(1))
        assertEquals(0, cardRepository.replaceCalls)
    }

    @Test
    fun cardsBecomeStaleWhenMaterialChangesAndCannotBeRebuiltFromAStaleAnalysis() = runBlocking {
        client.nextDraft = draft(item(refs = listOf("B1")))
        analyzeAt(NOW)
        createCards(1)

        documents.documents += document(3, campaignId = 1)

        val state = getCards(1)
        assertTrue(state.analysisStale)
        assertTrue(state.cardsStale)
        assertEquals(1, state.cards.size) // still shown, as stale
        assertEquals(CreateMicroCardsResult.AnalysisStale, createCards(1))
        assertEquals(1, cardRepository.replaceCalls)
    }

    @Test
    fun deletingASourceDocumentMakesCardsStaleWithoutCallingGemini() = runBlocking {
        client.nextDraft = draft(item(refs = listOf("B1")))
        analyzeAt(NOW)
        createCards(1)
        val requests = client.prompts.size

        DeleteSourceDocumentUseCase(documents)(campaignId = 1, documentId = 1)

        assertTrue(getCards(1).cardsStale)
        assertEquals(requests, client.prompts.size)
    }

    @Test
    fun reOcrMakesCardsStale() = runBlocking {
        client.nextDraft = draft(item(refs = listOf("B1")))
        analyzeAt(NOW)
        createCards(1)

        ocrRepository.results[1L] = ocr(1, mapOf(0 to listOf(LONG_TEXT, "Campaign A fact")), processedAt = NOW.plusSeconds(30))

        assertTrue(getCards(1).cardsStale)
    }

    @Test
    fun aNewAnalysisDoesNotMakeOldCardsLookCurrent() = runBlocking {
        client.nextDraft = draft(item(title = "V1", refs = listOf("B1")))
        analyzeAt(NOW)
        createCards(1)

        client.nextDraft = draft(item(title = "V2", refs = listOf("B1")))
        analyzeAt(NOW.plusSeconds(60))

        val state = getCards(1)
        assertFalse(state.analysisStale)
        assertTrue(state.cardsStale)
        assertEquals(listOf("V1"), state.cards.map { it.title })

        // Explicit rebuild replaces the whole set with the current analysis.
        assertEquals(CreateMicroCardsResult.Created(1), createCards(1))
        val rebuilt = getCards(1)
        assertFalse(rebuilt.cardsStale)
        assertEquals(listOf("V2"), rebuilt.cards.map { it.title })
    }

    @Test
    fun invalidOrFailedRebuildKeepsThePreviousCards() = runBlocking {
        client.nextDraft = draft(item(title = "Original", refs = listOf("B1")))
        analyzeAt(NOW)
        createCards(1)
        val original = getCards(1).cards

        // Persistence failure: previous set untouched.
        cardRepository.failReplace = true
        assertEquals(CreateMicroCardsResult.PersistenceFailed, createCards(1))
        assertEquals(original, getCards(1).cards)
        cardRepository.failReplace = false

        // Invalid content (source no longer in the material) never reaches the repository.
        val stored = analyses.analyses.getValue(1L)
        analyses.analyses[1L] = stored.copy(
            priorities = stored.priorities.map { it.copy(sourceRefs = listOf(SourceRef(1, 0, 99))) }
        )
        val replaceCallsBefore = cardRepository.replaceCalls
        assertTrue(createCards(1) is CreateMicroCardsResult.Invalid)
        assertEquals(replaceCallsBefore, cardRepository.replaceCalls)
        assertEquals(original, getCards(1).cards)
    }
}
