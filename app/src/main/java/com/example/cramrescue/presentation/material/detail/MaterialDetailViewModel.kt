package com.example.cramrescue.presentation.material.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cramrescue.domain.model.OcrResult
import com.example.cramrescue.domain.repository.OcrRepository
import com.example.cramrescue.domain.repository.SourceDocumentRepository
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
            _uiState.update { it.copy(isLoadingDocument = false, documentPath = document.localPath) }

            // Show a previously persisted OCR result immediately, without rerunning OCR.
            ocrRepository.getResultForDocument(documentId)?.let { applyResult(it) }
        }
    }

    fun extractText() {
        if (_uiState.value.isProcessingOcr) return // guard against duplicate concurrent processing

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true, errorMessage = null) }
            when (val outcome = processDocumentOcrUseCase(documentId)) {
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

    private fun applyResult(result: OcrResult) {
        _uiState.update {
            it.copy(
                recognizedText = result.reconstructedText(),
                characterCount = result.characterCount,
                blockCount = result.blocks.size,
                isLowText = result.isLowText,
                hasNoText = result.blocks.isEmpty()
            )
        }
    }
}

class MaterialDetailViewModelFactory(
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val ocrRepository: OcrRepository,
    private val processDocumentOcrUseCase: ProcessDocumentOcrUseCase,
    private val documentId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MaterialDetailViewModel(sourceDocumentRepository, ocrRepository, processDocumentOcrUseCase, documentId) as T
    }
}
