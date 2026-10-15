package com.example.cramrescue.domain.usecase.grounding

import com.example.cramrescue.domain.analysis.FakeOcrRepository
import com.example.cramrescue.domain.analysis.FakeSourceDocumentRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisRepository
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.analysis.StudyAnalysisValidator
import com.example.cramrescue.domain.analysis.ValidationResult
import com.example.cramrescue.domain.analysis.document
import com.example.cramrescue.domain.analysis.draft
import com.example.cramrescue.domain.analysis.item
import com.example.cramrescue.domain.grounding.GroundingResult
import com.example.cramrescue.domain.grounding.GroundingUnavailableReason
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.OcrPageResult
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.OcrTextBlock
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.repository.MicroCardRepository
import com.example.cramrescue.domain.usecase.analysis.GetAnalysisSourceUseCase
import com.example.cramrescue.domain.usecase.analysis.GetCampaignAnalysisUseCase
import com.example.cramrescue.domain.usecase.microcard.CreateMicroCardsUseCase
import com.example.cramrescue.domain.usecase.microcard.GetMicroCardsUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class Cards : MicroCardRepository {
    private val cards = mutableMapOf<Long, List<MicroCard>>()
    private var nextId = 1L
    override suspend fun replaceCards(campaignId: Long, cards: List<MicroCard>) {
        this.cards[campaignId] = cards.map { it.copy(id = nextId++) }
    }
    override suspend fun getCardsForCampaign(campaignId: Long) = cards[campaignId].orEmpty()
    override suspend fun getCard(campaignId: Long, cardId: Long) = cards[campaignId]?.firstOrNull { it.id == cardId }
}

/** Every block gets a DISTINCT box derived from (document, page, order), so a wrong block is detectable. */
private fun boxFor(documentId: Long, page: Int, order: Int) =
    BoundingBox(left = 10 * order + documentId.toInt(), top = 100 * page + 5, right = 10 * order + 50, bottom = 100 * page + 60)

private fun ocrPages(documentId: Long, pages: List<List<String>>, processedAt: Instant = NOW, width: Int = 1414) = OcrResult(
    documentId = documentId,
    pages = pages.mapIndexed { page, texts ->
        OcrPageResult(
            documentId = documentId, pageIndex = page, sourceWidthPx = width, sourceHeightPx = 2000,
            characterCount = texts.sumOf { it.length }, isLowText = false, processedAt = processedAt,
            blocks = texts.mapIndexed { order, text -> OcrTextBlock(0, documentId, page, order, text, boxFor(documentId, page, order)) }
        )
    }
)

class GetGroundedSourceUseCaseTest {

    private val ocr = FakeOcrRepository(mutableMapOf())
    private val documents = FakeSourceDocumentRepository(mutableListOf())
    private val analyses = FakeStudyAnalysisRepository()
    private val cardRepository = Cards()
    private val getSource = GetAnalysisSourceUseCase(documents, ocr)
    private val getAnalysis = GetCampaignAnalysisUseCase(analyses, getSource)
    private val getCards = GetMicroCardsUseCase(cardRepository, getAnalysis)
    private val ground = GetGroundedSourceUseCase(getCards, documents, ocr)

    private val longText = "Nội dung đủ dài để không bị đánh dấu chất lượng thấp khi nhận dạng."

    /**
     * Campaign 1: PDF 10 (page 0: B1,B2,B3; page 1: B4,B5) and gallery image 11 (B6).
     * Card "Multi" cites B1,B3 (p0), B4 (p1) and B6 (image). Card "Other" cites B2 only.
     * Campaign 2: image 20 with its own card.
     */
    private fun setUp(): Pair<MicroCard, MicroCard> = runBlocking {
        documents.documents += document(10, 1, name = "notes.pdf", pages = 2).copy(sourceType = SourceType.PDF)
        documents.documents += document(11, 1, name = "photo.jpg").copy(sourceType = SourceType.GALLERY)
        documents.documents += document(20, 2, name = "b.jpg").copy(sourceType = SourceType.CAMERA)
        ocr.results[10L] = ocrPages(10, listOf(listOf("$longText A", "$longText B", "$longText C"), listOf("$longText D", "$longText E")))
        ocr.results[11L] = ocrPages(11, listOf(listOf("$longText F")), width = 800)
        ocr.results[20L] = ocrPages(20, listOf(listOf("$longText G")))
        listOf(1L to draft(item(title = "Multi", refs = listOf("B1", "B3", "B4", "B6")), item(title = "Other", refs = listOf("B2"))),
            2L to draft(item(title = "B card", refs = listOf("B1")))).forEach { (campaignId, d) ->
            analyses.replaceAnalysis((StudyAnalysisValidator.validate(d, getSource(campaignId), "m", NOW) as ValidationResult.Valid).analysis)
            CreateMicroCardsUseCase(getAnalysis, cardRepository, Clock.fixed(NOW, ZoneOffset.UTC))(campaignId)
        }
        val cards = getCards(1).cards
        cards.first { it.title == "Multi" } to cards.first { it.title == "Other" }
    }

    private fun available(result: GroundingResult) = (result as GroundingResult.Available).source

    @Test
    fun resolvesTheExactDocumentPageAndBlocksWithTheirPersistedBoxesAndDimensions() = runBlocking {
        val (multi, _) = setUp()
        val source = available(ground(1, multi.id, 10, 0))

        assertEquals(10L, source.documentId)
        assertEquals(0, source.pageIndex)
        assertEquals(SourceType.PDF, source.sourceType)
        assertEquals("/doc10", source.localPath)
        assertEquals(1414 to 2000, source.sourceWidthPx to source.sourceHeightPx)
        // Multiple refs on the same page → exactly those blocks, each with its own box.
        assertEquals(listOf(0, 2), source.blocks.map { it.blockOrderIndex })
        assertEquals(listOf(boxFor(10, 0, 0), boxFor(10, 0, 2)), source.blocks.map { it.boundingBox })
        assertEquals(0, source.missingBlockCount)
        assertFalse(source.cardStale)
    }

    @Test
    fun differentPagesAndDocumentsStaySeparate() = runBlocking {
        val (multi, _) = setUp()
        val page1 = available(ground(1, multi.id, 10, 1))
        assertEquals(listOf(boxFor(10, 1, 0)), page1.blocks.map { it.boundingBox })

        val image = available(ground(1, multi.id, 11, 0))
        assertEquals(SourceType.GALLERY, image.sourceType)
        assertEquals(800, image.sourceWidthPx)
        assertEquals(listOf(boxFor(11, 0, 0)), image.blocks.map { it.boundingBox })
    }

    @Test
    fun anotherCampaignsCardOrSourceCannotBeResolved() = runBlocking {
        setUp()
        val cardB = getCards(2).cards.single()
        // Campaign 1 scope with campaign 2's card → not found.
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.CARD_NOT_FOUND), ground(1, cardB.id, 20, 0))
        // Campaign 1's card asked for campaign 2's document → it isn't cited, so refused.
        val (multi, _) = getCards(1).cards.let { c -> c.first { it.title == "Multi" } to Unit }
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(1, multi.id, 20, 0))
    }

    @Test
    fun aCardCannotOpenAPageItDoesNotCite() = runBlocking {
        val (_, other) = setUp()
        // "Other" cites only page 0 of the PDF; page 1 and the image belong to "Multi".
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(1, other.id, 10, 1))
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.NOT_CITED_BY_CARD), ground(1, other.id, 11, 0))
        assertEquals(listOf(1), available(ground(1, other.id, 10, 0)).blocks.map { it.blockOrderIndex })
    }

    @Test
    fun deletedSourceDocumentIsUnavailable() = runBlocking {
        val (multi, _) = setUp()
        documents.documents.removeAll { it.id == 11L }
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.SOURCE_DELETED), ground(1, multi.id, 11, 0))
    }

    @Test
    fun missingBlocksAreCountedOrUnavailableAndNeverTextMatched() = runBlocking {
        val (multi, _) = setUp()
        // Drop block 2 of page 0, keep everything else (same processedAt): one of two cited blocks is gone.
        val original = ocr.results.getValue(10L)
        ocr.results[10L] = original.copy(pages = original.pages.map { page ->
            if (page.pageIndex == 0) page.copy(blocks = page.blocks.filter { it.orderIndex != 2 }) else page
        })
        val partial = available(ground(1, multi.id, 10, 0))
        assertEquals(listOf(0), partial.blocks.map { it.blockOrderIndex })
        assertEquals(1, partial.missingBlockCount)

        // The image's only cited block (order 0) replaced by a block with the SAME text at order 5:
        // identity is gone, and the identical text must not be used to "find" it.
        val image = ocr.results.getValue(11L)
        ocr.results[11L] = image.copy(pages = image.pages.map { page ->
            page.copy(blocks = page.blocks.map { it.copy(orderIndex = 5) })
        })
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.BLOCKS_MISSING), ground(1, multi.id, 11, 0))
    }

    @Test
    fun reOcrAfterTheCardWasBuiltMakesTheSourceUnavailable() = runBlocking {
        val (multi, _) = setUp()
        ocr.results[11L] = ocrPages(11, listOf(listOf("$longText F")), processedAt = NOW.plusSeconds(60), width = 800)
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.SOURCE_CHANGED), ground(1, multi.id, 11, 0))
    }

    @Test
    fun missingPageOrInvalidDimensionsAreUnavailable() = runBlocking {
        val (multi, _) = setUp()
        val pdf = ocr.results.getValue(10L)
        ocr.results[10L] = pdf.copy(pages = pdf.pages.filter { it.pageIndex != 1 })
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.PAGE_MISSING), ground(1, multi.id, 10, 1))

        ocr.results[10L] = pdf.copy(pages = pdf.pages.map { it.copy(sourceWidthPx = 0) })
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.INVALID_DIMENSIONS), ground(1, multi.id, 10, 0))
    }

    @Test
    fun staleCardWithExistingSourceResolvesMarkedStale() = runBlocking {
        val (multi, _) = setUp()
        documents.documents += document(12, 1) // material changed after the cards were built
        val source = available(ground(1, multi.id, 10, 0))
        assertTrue(source.cardStale)

        documents.documents.removeAll { it.id == 10L }
        assertEquals(GroundingResult.Unavailable(GroundingUnavailableReason.SOURCE_DELETED), ground(1, multi.id, 10, 0))
    }

    @Test
    fun directDocumentGroundingWithZeroCardIdResolvesPageBlocks() = runBlocking {
        setUp()
        val result = ground(1, cardId = 0L, documentId = 10L, pageIndex = 0)
        val source = available(result)
        assertEquals(10L, source.documentId)
        assertEquals(0, source.pageIndex)
        assertEquals(3, source.blocks.size)
        assertEquals("$longText A", source.blocks.first().text)
        assertFalse(source.cardStale)
    }
}
