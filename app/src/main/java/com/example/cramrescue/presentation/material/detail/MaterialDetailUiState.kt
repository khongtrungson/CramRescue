package com.example.cramrescue.presentation.material.detail

import com.example.cramrescue.domain.model.SourceType

/** One page's OCR outcome, prepared for display. [pageNumber] is 1-based; storage is 0-based. */
data class OcrPageUi(
    val pageNumber: Int,
    val characterCount: Int,
    val blockCount: Int,
    val isLowText: Boolean,
    val text: String
)

data class MaterialDetailUiState(
    val isLoadingDocument: Boolean = true,
    val documentPath: String? = null,
    val sourceType: SourceType? = null,
    val displayName: String? = null,
    val pageCount: Int = 1,
    val documentNotFound: Boolean = false,
    val isProcessingOcr: Boolean = false,
    val processedPages: Int = 0,
    val totalPages: Int = 0,
    val pages: List<OcrPageUi> = emptyList(),
    val hasResult: Boolean = false,
    val errorMessage: String? = null,
    val isDeleting: Boolean = false,
    /** Set once the material has been deleted; the screen then navigates away. */
    val isDeleted: Boolean = false
) {
    /** Deletion is blocked while OCR runs, so OCR can't re-insert rows for a deleted document. */
    val canDelete: Boolean get() = !isLoadingDocument && !documentNotFound && !isProcessingOcr && !isDeleting

    val isPdf: Boolean get() = sourceType == SourceType.PDF
    val totalCharacterCount: Int get() = pages.sumOf { it.characterCount }
    val lowTextPageNumbers: List<Int> get() = pages.filter { it.isLowText }.map { it.pageNumber }
}
