package com.example.cramrescue.domain.usecase.word

import com.example.cramrescue.domain.analysis.FakeDocumentTextRepository
import com.example.cramrescue.domain.analysis.NOW
import com.example.cramrescue.domain.model.MAX_WORD_DOCUMENT_WORDS
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.word.ExtractedTextBlock
import com.example.cramrescue.domain.word.WordExtractionFailure
import com.example.cramrescue.domain.word.WordExtractionResult
import com.example.cramrescue.domain.word.WordTextExtractor
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FixedExtractor(var result: WordExtractionResult) : WordTextExtractor {
    val paths = mutableListOf<String>()
    override suspend fun extract(localPath: String): WordExtractionResult {
        paths += localPath
        return result
    }
}

private fun paragraph(text: String) = ExtractedTextBlock(TextBlockKind.PARAGRAPH, text)

class ImportWordDocumentUseCaseTest {

    private val repository = FakeDocumentTextRepository()
    private val extractor = FixedExtractor(WordExtractionResult.Success(emptyList()))
    private val import = ImportWordDocumentUseCase(extractor, repository, Clock.fixed(NOW, ZoneOffset.UTC))

    private fun run() = runBlocking { import(campaignId = 7, localPath = "/files/a.docx", displayName = "Đề cương.docx") }

    @Test
    fun savesOneWordDocumentWithItsParagraphsAtomically() {
        val blocks = listOf(ExtractedTextBlock(TextBlockKind.HEADING, "Chương 1"), paragraph("Mảng phải được sắp xếp."))
        extractor.result = WordExtractionResult.Success(blocks)

        val result = run() as WordImportResult.Imported

        assertEquals(WordImportResult.Imported(result.documentId, wordCount = 7, blockCount = 2), result)
        val (document, savedBlocks) = repository.saved.single()
        assertEquals(7L, document.campaignId)
        assertEquals(SourceType.WORD, document.sourceType)
        assertEquals("/files/a.docx", document.localPath)
        assertEquals("Đề cương.docx", document.displayName)
        assertEquals(1, document.pageCount) // one logical section, not a page count
        assertEquals(NOW, document.createdAt)
        assertEquals(blocks, savedBlocks)
        assertEquals(NOW, repository.texts.getValue(result.documentId).extractedAt)
        assertEquals(listOf("/files/a.docx"), extractor.paths)
    }

    @Test
    fun extractionFailuresSaveNothing() {
        WordExtractionFailure.entries.forEach { reason ->
            extractor.result = WordExtractionResult.Failure(reason)
            assertEquals(WordImportResult.Unreadable(reason), run())
        }
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun documentsWithoutWordsAreRejected() {
        extractor.result = WordExtractionResult.Success(emptyList())
        assertEquals(WordImportResult.NoText, run())
        extractor.result = WordExtractionResult.Success(listOf(paragraph("— … •"), paragraph("|")))
        assertEquals(WordImportResult.NoText, run())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun theTenThousandWordLimitIsInclusiveAndNeverTruncates() {
        val words = { n: Int -> List(n) { "từ" }.joinToString(" ") }
        extractor.result = WordExtractionResult.Success(listOf(paragraph(words(MAX_WORD_DOCUMENT_WORDS - 1)), paragraph(words(1))))
        assertTrue(run() is WordImportResult.Imported)

        extractor.result = WordExtractionResult.Success(listOf(paragraph(words(MAX_WORD_DOCUMENT_WORDS)), paragraph(words(1))))
        assertEquals(WordImportResult.TooManyWords(MAX_WORD_DOCUMENT_WORDS + 1), run())
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun aDatabaseFailureIsReportedNotThrown() {
        extractor.result = WordExtractionResult.Success(listOf(paragraph("Nội dung")))
        repository.failSaves = true
        assertEquals(WordImportResult.SaveFailed, run())
        assertTrue(repository.texts.isEmpty())
    }
}
