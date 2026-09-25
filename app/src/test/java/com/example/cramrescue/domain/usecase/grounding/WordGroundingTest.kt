package com.example.cramrescue.domain.usecase.grounding

import com.example.cramrescue.domain.analysis.FakeDocumentTextRepository
import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisRepository
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.analysis.wordDocument
import com.example.cramrescue.domain.analysis.wordText
import com.example.cramrescue.domain.grounding.GroundingResult
import com.example.cramrescue.domain.grounding.GroundingUnavailableReason
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class WordCards : MicroCardRepository {
    private val cards = mutableMapOf<Long, List<MicroCard>>()
    private var nextId = 1L
    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        this.cards[campaignId] = cards.map { it.copy(id = nextId++) }
    }
    override suspend fun getCardsForCampaign(campaignId: Long) = cards[campaignId].orEmpty()
    override suspend fun getCard(campaignId: Long, cardId: Long) = cards[campaignId]?.firstOrNull { it.id == cardId }
}

/** M11 grounding for Word (FR-14): exact paragraphs by identity, shown as a text snippet. */
class WordGroundingTest {

    private val documents = FakeSourceDocumentRepository(mutableListOf())
    private val texts = FakeDocumentTextRepository()
    private val analyses = FakeStudyAnalysisRepository()
    private val cardRepository = WordCards()
    private val getSource = GetAnalysisSourceUseCase(documents, FakeOcrRepository(mutableMapOf()), texts)
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
    private val getCards = GetMicroCardsUseCase(cardRepository, getAnalysis)
    private val ground = GetGroundedSourceUseCase(getCards, documents, FakeOcrRepository(mutableMapOf()), texts)

    private val paragraphs = listOf("Chương 1", "Định nghĩa A", "Công thức B", "Ví dụ C")

    /** Word doc 30: B1..B4 = paragraphs 0..3; doc 31: B5. "Multi" cites B2, B4, B5; "Other" cites B3. */
    private fun setUp(): Pair<MicroCard, MicroCard> = runBlocking {
        documents.documents += wordDocument(30, 1, name = "notes.docx")
        documents.documents += wordDocument(31, 1, name = "extra.docx")
        texts.texts[30L] = wordText(30, paragraphs)
        texts.texts[31L] = wordText(31, listOf("Phụ lục"))
        val d = draft(item(title = "Multi", refs = listOf("B2", "B4", "B5")), item(title = "Other", refs = listOf("B3")))
        analyses.replaceAnalysis((StudyAnalysisValidator.validate(d, getSource(1), "m", NOW) as ValidationResult.Valid).analysis)
        CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))(1)
        val cards = getCards(1).cards
        cards.first { it.title == "Multi" } to cards.first { it.title == "Other" }
    }

    private fun text(result: GroundingResult) = (result as GroundingResult.AvailableText).source

    @Test
    fun resolvesExactlyTheCitedParagraphsWithAllParagraphsForContext() = runBlocking {
        val (multi, other) = setUp()
        val source = text(ground(1, multi.id, 30, 0))

        assertEquals(30L, source.documentId)
        assertEquals("notes.docx", source.displayName)
        assertEquals(setOf(1, 3), source.citedOrderIndices)
        assertEquals(paragraphs, source.blocks.map { it.text })
        assertEquals(0, source.missingBlockCount)
        assertFalse(source.cardStale)

        assertEquals(setOf(0), text(ground(1, multi.id, 31, 0)).citedOrderIndices)
        assertEquals(setOf(2), text(ground(1, other.id, 30, 0)).citedOrderIndices)
    }

    @Test
    fun aCardCannotOpenAWordSourceItDoesNotCiteOrANonZeroSection() = runBlocking {
        val (_, other) = setUp()
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(1, other.id, 31, 0))
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(1, other.id, 30, 1))
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.CARD_NOT_FOUND), ground(2, other.id, 30, 0))
    }

    @Test
    fun deletedWordDocumentIsUnavailable() = runBlocking {
        val (multi, _) = setUp()
        documents.documents.removeAll { it.id == 30L }
        texts.texts.remove(30L)
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.SOURCE_DELETED), ground(1, multi.id, 30, 0))
    }

    @Test
    fun textExtractedAfterTheCardWasBuiltIsSourceChanged() = runBlocking {
        val (multi, _) = setUp()
        texts.texts[30L] = wordText(30, paragraphs, extractedAt = NOW.plusSeconds(60))
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.SOURCE_CHANGED), ground(1, multi.id, 30, 0))
    }

    @Test
    fun missingParagraphsAreCountedOrUnavailableAndNeverTextMatched() = runBlocking {
        val (multi, other) = setUp()
        // Paragraph 3 gone (same extraction time): one of two cited paragraphs remains.
        texts.texts[30L] = wordText(30, paragraphs.take(3))
        val partial = text(ground(1, multi.id, 30, 0))
        assertEquals(setOf(1), partial.citedOrderIndices)
        assertEquals(1, partial.missingBlockCount)

        // "Công thức B" moved to order 9 with identical text: identity is gone, text must not find it.
        texts.texts[30L] = wordText(30, listOf("Chương 1", "Định nghĩa A", "Công thức B"), orders = listOf(0, 1, 9))
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.BLOCKS_MISSING), ground(1, other.id, 30, 0))

        texts.texts.remove(30L)
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.BLOCKS_MISSING), ground(1, other.id, 30, 0))
    }

    @Test
    fun addingMaterialMarksTheCardStaleButKeepsWordGrounding() = runBlocking {
        val (multi, _) = setUp()
        documents.documents += wordDocument(32, 1)
        assertTrue(text(ground(1, multi.id, 30, 0)).cardStale)
    }
}
