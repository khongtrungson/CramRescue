package com.example.cramrescue.domain.word

import com.example.cramrescue.domain.model.TextBlockKind

data class ExtractedTextBlock(val kind: TextBlockKind, val text: String)

enum class WordExtractionFailure {
    EMPTY_FILE,

    /** OLE compound file: a legacy .doc, or a password-protected .docx. Not supported. */
    LEGACY_DOC_OR_ENCRYPTED,

    /** Not a ZIP package / not a Word document. */
    NOT_DOCX,

    /** Broken ZIP/XML, or no main document part. */
    MALFORMED,

    /** Exceeds the extractor's safety limits (e.g. a decompression bomb). */
    TOO_LARGE
}

sealed interface WordExtractionResult {
    /** Blocks in reading order; may be empty for a document with no text. */
    data class Success(val blocks: List<ExtractedTextBlock>) : WordExtractionResult
    data class Failure(val reason: WordExtractionFailure) : WordExtractionResult
}

/** Local, offline Word text extraction (FR-3). Implementations must never throw for bad input. */
interface WordTextExtractor {
    suspend fun extract(localPath: String): WordExtractionResult
}
