package com.example.cramrescue.domain.usecase.recall

import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisRepository
import com.example.cramrescue.domain.analysis.LONG_TEXT
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.analysis.ocr
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.recall.RecallPromptBuilder
import com.example.cramrescue.domain.recall.RecallSession
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.repository.StudyAttemptRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStudyAttemptRepository : StudyAttemptRepository {
    val attempts = mutableListOf<StudyAttempt>()
    var fail = false
    override suspend fun recordAttempt(attempt: StudyAttempt): Long {
        if (fail) throw IllegalStateException("simulated database failure")
        val stored = attempt.copy(id = attempts.size + 1L)
        attempts += stored
        return stored.id
    }
    override suspend fun getAttemptsForCampaign(campaignId: Long) = attempts.filter { it.campaignId == campaignId }
    override suspend fun getAttemptsForCard(campaignId: Long, microCardId: Long) =
        attempts.filter { it.campaignId == campaignId && it.microCardId == microCardId }
}

private class InMemoryMicroCardRepository : MicroCardRepository {
    private val cards = mutableMapOf<Long, List<MicroCard>>()
    private var nextId = 100L
    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        this.cards[campaignId] = cards.map { it.copy(id = nextId++) }
    }
    override suspend fun getCardsForCampaign(campaignId: Long) = cards[campaignId].orEmpty()
    override suspend fun getCard(campaignId: Long, cardId: Long) = cards[campaignId]?.firstOrNull { it.id == cardId }
}

class RecordRecallAssessmentUseCaseTest {

    private val attempts = FakeStudyAttemptRepository()
    private val record = RecordRecallAssessmentUseCase(attempts, Clock.fixed(NOW, ZoneOffset.UTC))

    private fun card(id: Long, campaignId: Long) = MicroCard(
        id = id, campaignId = campaignId, analysisGeneratedAt = NOW, orderIndex = 0, triage = TriageLevel.CODE_RED,
        title = "Card $id", bullets = listOf("b"), notes = null, sourceRefs = listOf(SourceRef(1, 0, 0)), createdAt = NOW
    )

    @Test
    fun eachLevelIsSavedAsItsTypedValueWithSnapshotAndTime() = runBlocking {
        RecallAssessment.entries.forEach { level -> record(1, card(7, 1), level) }

        assertEquals(RecallAssessment.entries, attempts.attempts.map { it.assessment })
        attempts.attempts.forEach {
            assertEquals(7L, it.microCardId)
            assertEquals(TriageLevel.CODE_RED, it.triage)
            assertEquals("Card 7", it.cardTitle)
            assertEquals(NOW, it.attemptedAt)
        }
    }

    @Test
    fun reStudyAppendsANewAttemptAndNeverOverwritesHistory() = runBlocking {
        record(1, card(7, 1), RecallAssessment.NOT_REMEMBERED)
        record(1, card(7, 1), RecallAssessment.PARTIAL)
        record(1, card(7, 1), RecallAssessment.MASTERED)

        assertEquals(
            listOf(RecallAssessment.NOT_REMEMBERED, RecallAssessment.PARTIAL, RecallAssessment.MASTERED),
            attempts.getAttemptsForCard(1, 7).map { it.assessment }
        )
        assertEquals(listOf(1L, 2L, 3L), attempts.attempts.map { it.id })
    }

    @Test
    fun campaignsNeverSeeEachOthersAttempts() = runBlocking {
        record(1, card(7, 1), RecallAssessment.MASTERED)
        record(2, card(8, 2), RecallAssessment.NOT_REMEMBERED)

        assertEquals(listOf(7L), attempts.getAttemptsForCampaign(1).map { it.microCardId })
        assertEquals(listOf(8L), attempts.getAttemptsForCampaign(2).map { it.microCardId })
    }

    @Test
    fun aCardFromAnotherCampaignIsRejected() = runBlocking {
        assertEquals(RecordAssessmentResult.WrongCampaign, record(1, card(8, 2), RecallAssessment.MASTERED))
        assertTrue(attempts.attempts.isEmpty())
    }

    @Test
    fun persistenceFailureIsReportedNotTreatedAsRemembered() = runBlocking {
        attempts.fail = true
        assertEquals(RecordAssessmentResult.Failed, record(1, card(7, 1), RecallAssessment.MASTERED))
        assertTrue(attempts.attempts.isEmpty())
    }

    /**
     * The whole M9 path — load cards, build prompts, run a session, record results — built from
     * M8's local use cases. No StudyAnalysisClient and no GeminiCredentialRepository exist in
     * this graph, so M9 cannot make a Gemini request or read the key; it needs no network.
     */
    @Test
    fun fullStudyFlowRunsEntirelyLocally() = runBlocking {
        val ocrRepository = FakeOcrRepository(mutableMapOf(1L to ocr(1, mapOf(0 to listOf(LONG_TEXT, "Fact two")))))
        val documents = FakeSourceDocumentRepository(mutableListOf(document(1, 1)))
        val analyses = FakeStudyAnalysisRepository()
        val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
        val source = getSource(1)
        analyses.replaceAnalysis(
            (StudyAnalysisValidator.validate(
                draft(
                    item(title = "Yellow", triage = "CODE_YELLOW", bullets = listOf("Độ phức tạp là O(log n)."), refs = listOf("B2")),
                    item(title = "Red", bullets = listOf("Binary Search yêu cầu dữ liệu đã được sắp xếp."), refs = listOf("B1"))
                ),
                source, "m", NOW
            ) as ValidationResult.Valid).analysis
        )
        val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
        val cardRepository = InMemoryMicroCardRepository()
        CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))(1)
        val state = GetMicroCardsUseCase(cardRepository, getAnalysis)(1)

        var session = RecallSession.start(state.cards)
        val prompts = session.cards.map { RecallPromptBuilder.build(it) }
        listOf(RecallAssessment.NOT_REMEMBERED, RecallAssessment.MASTERED).forEach { level ->
            session = session.reveal().beginAssessment()
            record(1, session.currentCard!!, level)
            session = session.assessmentSaved(level)
        }

        assertEquals(RecallSession.Phase.COMPLETED, session.phase)
        assertEquals(listOf("Red", "Yellow"), attempts.attempts.map { it.cardTitle })
        assertEquals(2, prompts.size)
    }

    @Test
    fun staleCardsRemainStudyableAndAreReportedStale() = runBlocking {
        val ocrRepository = FakeOcrRepository(mutableMapOf(1L to ocr(1, mapOf(0 to listOf(LONG_TEXT)))))
        val documents = FakeSourceDocumentRepository(mutableListOf(document(1, 1)))
        val analyses = FakeStudyAnalysisRepository()
        val getSource = GetAnalysisSourceUseCase(documents, ocrRepository)
        analyses.replaceAnalysis(
            (StudyAnalysisValidator.validate(draft(item(refs = listOf("B1"))), getSource(1), "m", NOW) as ValidationResult.Valid).analysis
        )
        val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
        val cardRepository = InMemoryMicroCardRepository()
        CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))(1)

        documents.documents += document(2, 1) // material changed after the cards were built

        val state = GetMicroCardsUseCase(cardRepository, getAnalysis)(1)
        assertTrue(state.cardsStale)
        assertEquals(RecallSession.Phase.QUESTION, RecallSession.start(state.cards).phase)
    }
}
