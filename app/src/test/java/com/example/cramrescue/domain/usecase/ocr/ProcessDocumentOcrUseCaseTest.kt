package com.example.cramrescue.domain.usecase.ocr

import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.ocr.RecognizedText
import com.example.cramrescue.domain.ocr.RecognizedTextBlock
import com.example.cramrescue.domain.ocr.TextRecognitionEngine
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSourceDocumentRepository : SourceDocumentRepository {
    private val documents = mutableMapOf<Long, SourceDocument>()
    private var nextId = 1L

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

    override suspend fun saveResult(result: OcrResult) {
        saved[result.documentId] = result // mirrors the DAO's replace-on-reprocess semantics
    }

    override suspend fun getResultForDocument(documentId: Long): OcrResult? = saved[documentId]
}

private class FakeTextRecognitionEngine(
    private val blocksByPath: Map<String, List<RecognizedTextBlock>>,
    private val width: Int = 1000,
    private val height: Int = 2000
) : TextRecognitionEngine {
    override suspend fun recognize(imagePath: String): RecognizedText =
        RecognizedText(width, height, blocksByPath[imagePath].orEmpty())
}

class ProcessDocumentOcrUseCaseTest {

    private val sourceDocumentRepository = FakeSourceDocumentRepository()
    private val ocrRepository = FakeOcrRepository()

    private suspend fun addDocument(path: String): Long =
        sourceDocumentRepository.saveDocument(
            SourceDocument(campaignId = 1L, sourceType = SourceType.GALLERY, localPath = path, createdAt = Instant.now())
        )

    @Test
    fun ocrResultIsAssociatedWithTheRequestedDocument() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val engine = FakeTextRecognitionEngine(
            mapOf("/a.jpg" to listOf(RecognizedTextBlock("Hello world", BoundingBox(1, 2, 3, 4))))
        )
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(documentId, result.documentId)
        assertEquals(documentId, ocrRepository.saved[documentId]?.documentId)
    }

    @Test
    fun boundingBoxCoordinatesArePreservedThroughMapping() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val box = BoundingBox(left = 10, top = 20, right = 30, bottom = 40)
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(RecognizedTextBlock("text", box))))
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(box, result.blocks.single().boundingBox)
        assertEquals(1000, result.sourceWidthPx)
        assertEquals(2000, result.sourceHeightPx)
    }

    @Test
    fun fewerThanFiftyCharactersIsLowText() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val shortText = "a".repeat(49)
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(RecognizedTextBlock(shortText, BoundingBox(0, 0, 1, 1)))))
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(49, result.characterCount)
        assertTrue(result.isLowText)
    }

    @Test
    fun exactlyFiftyCharactersIsNotLowText() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val text = "a".repeat(50)
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(RecognizedTextBlock(text, BoundingBox(0, 0, 1, 1)))))
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(50, result.characterCount)
        assertFalse(result.isLowText)
    }

    @Test
    fun emptyOcrResultIsHandledWithoutError() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val engine = FakeTextRecognitionEngine(mapOf("/a.jpg" to emptyList()))
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertTrue(result.blocks.isEmpty())
        assertEquals(0, result.characterCount)
        assertTrue(result.isLowText)
    }

    @Test
    fun reprocessingReplacesPreviousBlocksRatherThanAppending() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val firstEngine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(RecognizedTextBlock("first pass", BoundingBox(0, 0, 1, 1)))))
        ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, firstEngine)(documentId)

        val secondEngine = FakeTextRecognitionEngine(mapOf("/a.jpg" to listOf(RecognizedTextBlock("second pass", BoundingBox(0, 0, 1, 1)))))
        val outcome = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, secondEngine)(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(1, result.blocks.size)
        assertEquals("second pass", result.blocks.single().text)
        assertEquals(1, ocrRepository.saved.size)
    }

    @Test
    fun blockOrderingIsDeterministic() = runBlocking {
        val documentId = addDocument("/a.jpg")
        val engine = FakeTextRecognitionEngine(
            mapOf(
                "/a.jpg" to listOf(
                    RecognizedTextBlock("first", BoundingBox(0, 0, 1, 1)),
                    RecognizedTextBlock("second", BoundingBox(0, 10, 1, 11)),
                    RecognizedTextBlock("third", BoundingBox(0, 20, 1, 21))
                )
            )
        )
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(documentId)

        val result = (outcome as ProcessDocumentOcrResult.Success).result
        assertEquals(listOf(0, 1, 2), result.blocks.map { it.orderIndex })
        assertEquals("first\nsecond\nthird", result.reconstructedText())
    }

    @Test
    fun documentNotFoundIsReportedWithoutThrowing() = runBlocking {
        val engine = FakeTextRecognitionEngine(emptyMap())
        val useCase = ProcessDocumentOcrUseCase(sourceDocumentRepository, ocrRepository, engine)

        val outcome = useCase(999L)

        assertEquals(ProcessDocumentOcrResult.DocumentNotFound, outcome)
    }
}
