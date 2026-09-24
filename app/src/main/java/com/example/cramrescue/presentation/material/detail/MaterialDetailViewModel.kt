package com.example.cramrescue.presentation.material.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
import com.example.cramrescue.domain.usecase.material.DeleteDocumentResult
import com.example.cramrescue.domain.usecase.material.DeleteSourceDocumentUseCase
import com.example.cramrescue.domain.usecase.ocr.ProcessDocumentOcrResult
import com.example.cramrescue.domain.usecase.ocr.ProcessDocumentOcrUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MaterialDetailViewModel(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    private val processDocumentOcrUseCase: ProcessDocumentOcrUseCase,
    private val deleteSourceDocument: DeleteSourceDocumentUseCase,
    private val campaignId: Long,
    private val documentId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaterialDetailUiState())
    val uiState: StateFlow<MaterialDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val document = sourceDocumentRepository.getDocumentById(documentId)
            if (document == null) {
                _uiState.update { it.copy(isLoadingDocument = false, documentNotFound = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoadingDocument = false,
                    documentPath = document.localPath,
                    sourceType = document.sourceType,
                    displayName = document.displayName,
                    pageCount = document.effectivePageCount
                )
            }

            // Show a previously persisted OCR result immediately, without rerunning OCR.
            ocrRepository.getResultForDocument(documentId)?.let { applyResult(it) }
        }
    }

    fun extractText() {
        // Guard against duplicate concurrent processing, and against OCR racing a deletion.
        if (_uiState.value.isProcessingOcr || _uiState.value.isDeleting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingOcr = true,
                    errorMessage = null,
                    processedPages = 0,
                    totalPages = it.pageCount
                )
            }

            val outcome = processDocumentOcrUseCase(documentId) { pagesDone, totalPages ->
                _uiState.update { it.copy(processedPages = pagesDone, totalPages = totalPages) }
            }

            when (outcome) {
                is ProcessDocumentOcrResult.Success -> {
                    applyResult(outcome.result)
                    _uiState.update { it.copy(isProcessingOcr = false) }
                }
                ProcessDocumentOcrResult.DocumentNotFound -> _uiState.update {
                    it.copy(isProcessingOcr = false, documentNotFound = true)
                }
                is ProcessDocumentOcrResult.Failure -> _uiState.update {
                    it.copy(isProcessingOcr = false, errorMessage = outcome.message)
                }
            }
        }
    }

    /** Only called after the user confirmed the deletion dialog. */
    fun delete() {
        if (!_uiState.value.canDelete) return
        _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = deleteSourceDocument(campaignId, documentId)
            _uiState.update {
                when (result) {
                    // NotFound: already gone (e.g. deleted concurrently) — same end state for the user.
                    DeleteDocumentResult.Deleted, DeleteDocumentResult.NotFound ->
                        it.copy(isDeleting = false, isDeleted = true)
                    DeleteDocumentResult.NotInCampaign -> it.copy(
                        isDeleting = false,
                        errorMessage = "Tài liệu này không thuộc chiến dịch hiện tại nên không thể xóa."
                    )
                    DeleteDocumentResult.Failed -> it.copy(
                        isDeleting = false,
                        errorMessage = "Không thể xóa tài liệu. Vui lòng thử lại."
                    )
                }
            }
        }
    }

    private fun applyResult(result: OcrResult) {
        val pages = result.pages.sortedBy { it.pageIndex }.map { page ->
            OcrPageUi(
                pageNumber = page.pageIndex + 1,
                characterCount = page.characterCount,
                blockCount = page.blocks.size,
                isLowText = page.isLowText,
                text = page.reconstructedText()
            )
        }
        _uiState.update { it.copy(pages = pages, hasResult = true) }
    }
}

class MaterialDetailViewModelFactory(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    private val processDocumentOcrUseCase: ProcessDocumentOcrUseCase,
    private val deleteSourceDocument: DeleteSourceDocumentUseCase,
    private val campaignId: Long,
    private val documentId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MaterialDetailViewModel(
            sourceDocumentRepository,
            ocrRepository,
            processDocumentOcrUseCase,
            deleteSourceDocument,
            campaignId,
            documentId
        ) as T
    }
}
