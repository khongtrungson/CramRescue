package com.example.cramrescue.domain.usecase.word

import com.example.cramrescue.domain.model.DocumentText
import com.example.cramrescue.domain.model.MAX_WORD_DOCUMENT_WORDS
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.WORD_LOGICAL_SECTION_INDEX
import com.example.cramrescue.domain.repository.DocumentTextRepository
import com.example.cramrescue.domain.word.WordExtractionFailure
import com.example.cramrescue.domain.word.WordExtractionResult
import com.example.cramrescue.domain.word.WordTextExtractor
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException

sealed interface WordImportResult {
    data class Imported(val documentId: Long, val wordCount: Int, val blockCount: Int) : WordImportResult
    data class Unreadable(val reason: WordExtractionFailure) : WordImportResult
    data object NoText : WordImportResult

    /** FR-3: at most 10,000 words. Rejected rather than silently truncated. */
    data class TooManyWords(val wordCount: Int) : WordImportResult
    data object SaveFailed : WordImportResult
}

/**
 * Imports an already app-owned Word copy into [campaignId]: extract locally → validate → save the
 * document and its text atomically. Nothing is saved unless everything succeeds; the caller
 * deletes the copied file for any non-Imported result. No OCR, no AI, no network.
 */
class ImportWordDocumentUseCase(
    private val extractor: WordTextExtractor,
    private val documentTextRepository: DocumentTextRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend operator fun invoke(campaignId: Long, localPath: String, displayName: String?): WordImportResult {
        val blocks = when (val result = extractor.extract(localPath)) {
            is WordExtractionResult.Failure -> return WordImportResult.Unreadable(result.reason)
            is WordExtractionResult.Success -> result.blocks
        }
        if (blocks.isEmpty()) return WordImportResult.NoText
        val wordCount = blocks.sumOf { DocumentText.countWords(it.text) }
        if (wordCount == 0) return WordImportResult.NoText
        if (wordCount > MAX_WORD_DOCUMENT_WORDS) return WordImportResult.TooManyWords(wordCount)

        val now = Instant.now(clock)
        return try {
            val id = documentTextRepository.saveWordDocument(
                SourceDocument(
                    campaignId = campaignId,
                    sourceType = SourceType.WORD,
                    localPath = localPath,
                    createdAt = now,
                    // One logical section, not a physical page count (see WORD_LOGICAL_SECTION_INDEX).
                    pageCount = WORD_LOGICAL_SECTION_INDEX + 1,
                    displayName = displayName
                ),
                blocks,
                now
            )
            WordImportResult.Imported(id, wordCount, blocks.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WordImportResult.SaveFailed
        }
    }
}
