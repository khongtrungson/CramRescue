package com.example.cramrescue.domain.usecase.ocr

import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.MAX_DOCUMENT_PAGES
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.ocr.RecognizedText
import com.example.cramrescue.domain.ocr.RecognizedTextBlock
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.example.cramrescue.domain.pdf.PdfDocumentSession
import com.example.cramrescue.domain.pdf.PdfPageRenderer
import com.example.cramrescue.domain.pdf.RenderedPage
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pagePath(pageIndex: Int) = "/render/page-$pageIndex.jpg"

private class FakeSourceDocumentRepository : SourceDocumentRepository {
    private val documents = mutableMapOf<Long, SourceDocument>()
    private var nextId = 1L

    override suspend fun deleteDocument(campaignId: Long, documentId: Long): Boolean =
        documents[documentId]?.takeIf { it.campaignId == campaignId }?.let { documents.remove(documentId) } != null

    override suspend fun saveDocument(document: SourceDocument): Long {
        val id = nextId++
        documents[id] = document.copy(id = id)
        return id
    }

    override suspend fun getDocumentsForCampaign(campaignId: Long): List<SourceDocument> =
        documents.values.filter { it.campaignId == campaignId }

    override suspend fun getDocumentById(documentId: Long): SourceDocument? = documents[documentId]
}

private class FakeOcrRepository : OcrRepository {
    val saved = mutableMapOf<Long, OcrResult>()
    var saveCount = 0

    override suspend fun saveResult(result: OcrResult) {
        saveCount++
        saved[result.documentId] = result // mirrors the DAO's atomic replace-on-reprocess
    }

    override suspend fun getResultForDocument(documentId: Long): OcrResult? = saved[documentId]
}

private class FakeTextRecognitionEngine(
    private val blocksByPath: Map<String, List<RecognizedTextBlock>>,
    private val width: Int = 1000,
    private val height: Int = 1400,
    private val failOnPath: String? = null
) : TextRecognitionEngine {
    override suspend fun recognize(imagePath: String): RecognizedText {
        if (imagePath == failOnPath) throw IllegalStateException("recognition failed for $imagePath")
        return RecognizedText(width, height, blocksByPath[imagePath].orEmpty())
    }
}

private class FakePdfPageRenderer(
    private val totalPages: Int,
    private val failOnPageIndex: Int? = null
) : PdfPageRenderer {
    val renderedPages = mutableListOf<Int>()
    val deletedPaths = mutableListOf<String>()
    var documentReleased = false

    override suspend fun <T> withDocument(localPath: String, block: suspend (PdfDocumentSession) -> T): T {
        val session = object : PdfDocumentSession {
            override val pageCount: Int = totalPages

            override suspend fun renderPage(pageIndex: Int): RenderedPage {
                if (pageIndex == failOnPageIndex) error("render failed on page $pageIndex")
                renderedPages += pageIndex
                return RenderedPage(pageIndex, pagePath(pageIndex), 1000, 1400)
            }

            override fun deleteRenderedPage(page: RenderedPage) {
                deletedPaths += page.imagePath
            }
        }
        return try {
            block(session)
        } finally {
            documentReleased = true
        }
    }
}

private fun block(text: String) = RecognizedTextBlock(text, BoundingBox(1, 2, 3, 4))

class ProcessDocumentOcrUseCaseTest {

    private val sourceDocumentRepository = FakeSourceDocumentRepository()
    private val ocrRepository = FakeOcrRepository()

    private suspend fun addImage(path: String = "/a.jpg", campaignId: Long = 1L): Long =
        sourceDocumentRepository.saveDocument(
            SourceDocument(
                campaignId = campaignId,
                sourceType = SourceType.GALLERY,
                localPath = path,
                createdAt = Instant.now(),
                pageCount = 1
            )
        )

    private suspend fun addPdf(pageCount: Int, campaignId: Long = 1L): Long =
        sourceDocumentRepository.saveDocument(
            SourceDocument(
                campaignId = campaignId,
                sourceType = SourceType.PDF,
                localPath = "/doc.pdf",
                createdAt = Instant.now(),
                pageCount = pageCount,
                displayName = "doc.pdf"
            )
        )

    private fun useCase(
        engine: TextRecognitionEngine,
        renderer: PdfPageRenderer = FakePdfPageRenderer(0)
    ) = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine, renderer)

    // ---------------------------------------------------------------- image documents (M5)

    @Test
    fun imageDocumentProducesASinglePageZeroIndexedResult() = runBlocking {
        val documentId = addImage()
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(block("Hello world"))))

        val outcome = useCase(engine)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(documentId, result.documentId)
        assertEquals(1, result.pages.size)
        assertEquals(0, result.pages.single().pageIndex)
        assertEquals(documentId, ocrRepository.saved[documentId]?.documentId)
    }

    @Test
    fun boundingBoxAndSourceDimensionsArePreserved() = runBlocking {
        val documentId = addImage()
        val box = BoundingBox(left = 10, top = 20, right = 30, bottom = 40)
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(RecognizedTextBlock("text", box))))

        val outcome = useCase(engine)(documentId)

        val page = (outcome as ProcessDocumentOcrResult.Success).result.pages.single()
        assertEquals(box, page.blocks.single().boundingBox)
        assertEquals(1000, page.sourceWidthPx)
        assertEquals(1400, page.sourceHeightPx)
    }

    @Test
    fun documentNotFoundIsReportedWithoutThrowing() = runBlocking {
        val outcome = useCase(FakeTextRecognitionEngine(emptyMap()))(999L)

        assertEquals(ProcessDocumentOcrResult.DocumentNotFound, outcome)
    }

    // ---------------------------------------------------------------- low text / empty (per page)

    @Test
    fun fewerThanFiftyCharactersOnAPageIsLowText() = runBlocking {
        val documentId = addImage()
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(block("a".repeat(49)))))

        val outcome = useCase(engine)(documentId)

        val page = (outcome as ProcessDocumentOcrResult.Success).result.pages.single()
        assertEquals(49, page.characterCount)
        assertTrue(page.isLowText)
    }

    @Test
    fun exactlyFiftyCharactersOnAPageIsNotLowText() = runBlocking {
        val documentId = addImage()
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(block("a".repeat(50)))))

        val outcome = useCase(engine)(documentId)

        val page = (outcome as ProcessDocumentOcrResult.Success).result.pages.single()
        assertEquals(50, page.characterCount)
        assertFalse(page.isLowText)
    }

    @Test
    fun lowTextIsEvaluatedPerPageNotAcrossTheWholeDocument() = runBlocking {
        val documentId = addPdf(pageCount = 2)
        val renderer = FakePdfPageRenderer(totalPages = 2)
        // Page 0 is comfortably above the threshold, page 1 is below it. A document-level sum
        // (200 + 32) would hide page 1's problem — it must still be flagged.
        val engine = FakeTextRecognitionEngine(
            mapOf(
                pagePath(0) to listOf(block("a".repeat(200))),
                pagePath(1) to listOf(block("b".repeat(32)))
            )
        )

        val outcome = useCase(engine, renderer)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(232, result.characterCount)
        assertFalse(result.pages[0].isLowText)
        assertTrue(result.pages[1].isLowText)
        assertEquals(listOf(1), result.lowTextPageIndices)
    }

    @Test
    fun emptyPageIsHandledAndDoesNotStopOtherPages() = runBlocking {
        val documentId = addPdf(pageCount = 3)
        val renderer = FakePdfPageRenderer(totalPages = 3)
        val engine = FakeTextRecognitionEngine(
            mapOf(
                pagePath(0) to listOf(block("a".repeat(80))),
                pagePath(1) to emptyList(), // blank/decorative page
                pagePath(2) to listOf(block("c".repeat(90)))
            )
        )

        val outcome = useCase(engine, renderer)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(3, result.pages.size)
        assertTrue(result.pages[1].blocks.isEmpty())
        assertEquals(0, result.pages[1].characterCount)
        assertTrue(result.pages[1].isLowText)
        assertEquals(90, result.pages[2].characterCount)
    }

    // ---------------------------------------------------------------- PDF paging

    @Test
    fun pdfPagesAreIndexedAndOrderedDeterministically() = runBlocking {
        val documentId = addPdf(pageCount = 3)
        val renderer = FakePdfPageRenderer(totalPages = 3)
        val engine = FakeTextRecognitionEngine(
            mapOf(
                pagePath(0) to listOf(block("first")),
                pagePath(1) to listOf(block("second")),
                pagePath(2) to listOf(block("third"))
            )
        )

        val outcome = useCase(engine, renderer)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(listOf(0, 1, 2), result.pages.map { it.pageIndex })
        assertEquals(listOf(0, 1, 2), renderer.renderedPages)
        assertEquals("first\n\nsecond\n\nthird", result.reconstructedText())
    }

    @Test
    fun blocksFromDifferentPagesStaySeparated() = runBlocking {
        val documentId = addPdf(pageCount = 2)
        val renderer = FakePdfPageRenderer(totalPages = 2)
        val engine = FakeTextRecognitionEngine(
            mapOf(
                pagePath(0) to listOf(block("p0-a"), block("p0-b")),
                pagePath(1) to listOf(block("p1-a"))
            )
        )

        val outcome = useCase(engine, renderer)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(listOf("p0-a", "p0-b"), result.pages[0].blocks.map { it.text })
        assertEquals(listOf("p1-a"), result.pages[1].blocks.map { it.text })
        assertTrue(result.pages[0].blocks.all { it.pageIndex == 0 })
        assertTrue(result.pages[1].blocks.all { it.pageIndex == 1 })
        assertEquals(listOf(0, 1), result.pages[0].blocks.map { it.orderIndex })
    }

    @Test
    fun twentyPagePdfIsAccepted() = runBlocking {
        val documentId = addPdf(pageCount = MAX_DOCUMENT_PAGES)
        val renderer = FakePdfPageRenderer(totalPages = MAX_DOCUMENT_PAGES)
        val engine = FakeTextRecognitionEngine(
            (0 until MAX_DOCUMENT_PAGES).associate { pagePath(it) to listOf(block("page $it text ${"x".repeat(60)}")) }
        )

        val outcome = useCase(engine, renderer)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(MAX_DOCUMENT_PAGES, result.pages.size)
        assertEquals(MAX_DOCUMENT_PAGES - 1, result.pages.last().pageIndex)
    }

    @Test
    fun pdfWithMoreThanTwentyPagesIsRejected() = runBlocking {
        val documentId = addPdf(pageCount = MAX_DOCUMENT_PAGES + 1)
        val renderer = FakePdfPageRenderer(totalPages = MAX_DOCUMENT_PAGES + 1)

        val outcome = useCase(FakeTextRecognitionEngine(emptyMap()), renderer)(documentId)

        assertTrue(outcome is ProcessDocumentOcrResult.Failure)
        assertTrue((outcome as ProcessDocumentOcrResult.Failure).message.contains("$MAX_DOCUMENT_PAGES"))
        assertNull(ocrRepository.saved[documentId]) // nothing persisted
    }

    @Test
    fun renderedPagesAreReleasedAsProcessingAdvances() = runBlocking {
        val documentId = addPdf(pageCount = 3)
        val renderer = FakePdfPageRenderer(totalPages = 3)
        val engine = FakeTextRecognitionEngine(
            (0 until 3).associate { pagePath(it) to listOf(block("page $it")) }
        )

        useCase(engine, renderer)(documentId)

        // Every rendered page's temp file is deleted, and the document handle is released.
        assertEquals(listOf(pagePath(0), pagePath(1), pagePath(2)), renderer.deletedPaths)
        assertTrue(renderer.documentReleased)
    }

    @Test
    fun progressIsReportedPerPage() = runBlocking {
        val documentId = addPdf(pageCount = 3)
        val renderer = FakePdfPageRenderer(totalPages = 3)
        val engine = FakeTextRecognitionEngine(
            (0 until 3).associate { pagePath(it) to listOf(block("page $it")) }
        )
        val progress = mutableListOf<Pair<Int, Int>>()

        useCase(engine, renderer)(documentId) { done, total -> progress += done to total }

        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)
    }

    // ---------------------------------------------------------------- failure + reprocessing

    @Test
    fun failureOnOnePagePersistsNothingAndKeepsPreviousResult() = runBlocking {
        val documentId = addPdf(pageCount = 3)
        val goodEngine = FakeTextRecognitionEngine(
            (0 until 3).associate { pagePath(it) to listOf(block("first pass page $it")) }
        )
        useCase(goodEngine, FakePdfPageRenderer(totalPages = 3))(documentId)
        val savesAfterFirstRun = ocrRepository.saveCount

        // Page 2 fails on the retry: the whole run must fail, leaving run #1's data intact.
        val failingEngine = FakeTextRecognitionEngine(
            (0 until 3).associate { pagePath(it) to listOf(block("second pass page $it")) },
            failOnPath = pagePath(2)
        )
        val outcome = useCase(failingEngine, FakePdfPageRenderer(totalPages = 3))(documentId)

        assertTrue(outcome is ProcessDocumentOcrResult.Failure)
        assertEquals(savesAfterFirstRun, ocrRepository.saveCount) // no partial write
        val stored = ocrRepository.saved.getValue(documentId)
        assertEquals(3, stored.pages.size)
        assertTrue(stored.pages.all { it.blocks.single().text.startsWith("first pass") })
    }

    @Test
    fun renderFailureIsReportedWithoutPersisting() = runBlocking {
        val documentId = addPdf(pageCount = 3)
        val renderer = FakePdfPageRenderer(totalPages = 3, failOnPageIndex = 1)
        val engine = FakeTextRecognitionEngine(
            (0 until 3).associate { pagePath(it) to listOf(block("page $it")) }
        )

        val outcome = useCase(engine, renderer)(documentId)

        assertTrue(outcome is ProcessDocumentOcrResult.Failure)
        assertNull(ocrRepository.saved[documentId])
        assertTrue(renderer.documentReleased) // handles still released on failure
    }

    @Test
    fun reprocessingReplacesPagesRatherThanAppending() = runBlocking {
        val documentId = addPdf(pageCount = 2)
        val firstEngine = FakeTextRecognitionEngine(
            mapOf(pagePath(0) to listOf(block("first pass 0")), pagePath(1) to listOf(block("first pass 1")))
        )
        useCase(firstEngine, FakePdfPageRenderer(totalPages = 2))(documentId)

        val secondEngine = FakeTextRecognitionEngine(
            mapOf(pagePath(0) to listOf(block("second pass 0")), pagePath(1) to listOf(block("second pass 1")))
        )
        val outcome = useCase(secondEngine, FakePdfPageRenderer(totalPages = 2))(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(2, result.pages.size)
        assertEquals(2, result.blockCount)
        assertEquals("second pass 0", result.pages[0].blocks.single().text)
        assertEquals(1, ocrRepository.saved.size)
    }
}
